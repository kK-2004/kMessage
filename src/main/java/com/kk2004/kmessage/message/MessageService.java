package com.kk2004.kmessage.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk2004.common.exception.BusinessException;
import com.kk2004.common.web.RequestContext;
import com.kk2004.kmessage.channel.*;
import com.kk2004.kmessage.domain.*;
import com.kk2004.kmessage.domain.Entities.*;
import com.kk2004.kmessage.persistence.*;
import com.kk2004.kmessage.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class MessageService {
    private final MessageRepository messages;
    private final TaskRepository tasks;
    private final AttemptRepository attempts;
    private final ChannelRepository channels;
    private final GrantRepository grants;
    private final ChannelAdapterRegistry adapters;
    private final UserGroupService userGroups;
    private final ObjectMapper mapper;

    public MessageService(MessageRepository messages, TaskRepository tasks, AttemptRepository attempts,
                          ChannelRepository channels, GrantRepository grants, ChannelAdapterRegistry adapters,
                          UserGroupService userGroups, ObjectMapper mapper) {
        this.messages = messages; this.tasks = tasks; this.attempts = attempts; this.channels = channels;
        this.grants = grants; this.adapters = adapters; this.userGroups = userGroups; this.mapper = mapper;
    }

    @Transactional
    public Object submit(SubmitRequest request) {
        Caller caller = CallerContext.require();

        // Determine the set of (target, per-member idempotency suffix) to deliver to.
        // The three targeting modes are mutually exclusive.
        int modes = (isSet(request.target()) ? 1 : 0) + (isSet(request.groupId()) ? 1 : 0) + (isSet(request.userId()) ? 1 : 0);
        if (modes == 0) throw new BusinessException("必须指定 target、groupId 或 userId 之一");
        if (modes > 1) throw new BusinessException("target、groupId、userId 只能指定其一");

        // Resolve the channel instance. channelInstanceId is optional when targeting a
        // group or user (each carries its own channel via its UUID primary key); it is
        // still required for raw target sends, whose target id is only unique per channel.
        String channelId = request.channelInstanceId();
        if (!isSet(channelId)) {
            if (isSet(request.groupId())) channelId = userGroups.groupChannel(request.groupId());
            else if (isSet(request.userId())) channelId = userGroups.userChannel(request.userId());
            else throw new BusinessException("channelInstanceId 不能为空");
        }
        ChannelInstance channel = channels.findById(channelId).orElseThrow(() -> new BusinessException(404, "渠道实例不存在"));
        if (!channel.enabled) throw new BusinessException("渠道实例未启用");
        if (!grants.existsByCallerIdAndChannelInstanceId(caller.id, channel.id)) throw new BusinessException(403, "调用方无权使用该渠道");
        String extensions = json(request.extensions() == null ? Map.of() : request.extensions());
        ResolvedMessageContent content = resolveContent(request);
        String traceId = RequestContext.getTraceId();

        List<Target> targets;
        if (isSet(request.groupId())) {
            targets = userGroups.expandGroup(caller.id, channel.id, request.groupId()).stream()
                    .map(m -> new Target(m.targetId(), ":" + m.appUserId()))
                    .toList();
            if (targets.isEmpty()) throw new BusinessException("分组没有成员，无法发送");
        } else if (isSet(request.userId())) {
            AppUser u = userGroups.resolveUser(channel.id, request.userId());
            targets = List.of(new Target(u.targetId, ""));
        } else {
            targets = List.of(new Target(request.target(), ""));
        }

        // Group fan-out returns a batch; single-target returns one view (backward compatible).
        if (targets.size() == 1 && targets.get(0).suffix.isEmpty()) {
            return deliverOne(caller, channel, targets.get(0).targetValue, request, content, extensions, traceId);
        }
        List<MessageView> results = new ArrayList<>();
        for (Target t : targets) {
            results.add(deliverOne(caller, channel, t.targetValue, request, content, extensions, traceId, t.suffix));
        }
        return new BatchView(results.size(), results);
    }

    private MessageView deliverOne(Caller caller, ChannelInstance channel, String target,
                                   SubmitRequest request, ResolvedMessageContent content, String extensions, String traceId) {
        return deliverOne(caller, channel, target, request, content, extensions, traceId, "");
    }

    private MessageView deliverOne(Caller caller, ChannelInstance channel, String target,
                                   SubmitRequest request, ResolvedMessageContent content,
                                   String extensions, String traceId, String idempotencySuffix) {
        try {
            adapters.require(channel.channelType).validate(target, content.type(), extensions);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        String idempotencyKey = request.idempotencyKey() + idempotencySuffix;
        String requestHash = Hashing.sha256(json(requestHashPayload(channel.id, target, content, extensions)));
        Optional<Message> existing = messages.findByCallerIdAndIdempotencyKey(caller.id, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().requestHash.equals(requestHash)) throw new BusinessException(409, "幂等键已用于不同请求");
            return view(existing.get());
        }
        Message message = new Message();
        message.callerId = caller.id; message.channelInstanceId = channel.id; message.targetValue = target;
        message.contentType = content.type(); message.contentText = content.text(); message.contentJson = content.json();
        message.extensionJson = extensions; message.idempotencyKey = idempotencyKey;
        message.requestHash = requestHash; message.traceId = traceId;
        messages.save(message);
        DeliveryTask task = new DeliveryTask();
        task.messageId = message.id;
        tasks.save(task);
        return view(message);
    }

    private boolean isSet(String s) { return s != null && !s.isBlank(); }

    public MessageDetail get(String id) {
        Caller caller = CallerContext.require();
        Message message = messages.findByIdAndCallerId(id, caller.id).orElseThrow(() -> new BusinessException(404, "消息不存在"));
        List<AttemptView> history = attempts.findByMessageIdOrderByAttemptNumberAsc(id).stream()
                .map(a -> new AttemptView(a.attemptNumber, a.resultType, a.errorCode, a.diagnostic, a.startedAt, a.finishedAt)).toList();
        return new MessageDetail(view(message), history);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BusinessException("扩展参数无法序列化"); }
    }
    private ResolvedMessageContent resolveContent(SubmitRequest request) {
        MessagePayload message = request.message();
        if (message == null) {
            return textContent(request.text());
        }
        MessageContentType type = message.type();
        if (type == null) throw new BusinessException("message.type 不能为空");
        return switch (type) {
            case TEXT -> textContent(message.text());
            case CARD -> cardContent(message.card());
        };
    }

    private ResolvedMessageContent textContent(String text) {
        if (text == null || text.isBlank()) throw new BusinessException("普通消息 text 不能为空");
        return new ResolvedMessageContent(MessageContentType.TEXT, text, null);
    }

    private ResolvedMessageContent cardContent(Map<String, Object> card) {
        if (card == null || card.isEmpty()) throw new BusinessException("卡片消息 card 不能为空");
        String contentJson = json(card);
        return new ResolvedMessageContent(MessageContentType.CARD, summarizeCard(card), contentJson);
    }

    private String summarizeCard(Map<String, Object> card) {
        Object header = card.get("header");
        if (header instanceof Map<?, ?> headerMap) {
            Object title = headerMap.get("title");
            if (title instanceof Map<?, ?> titleMap) {
                Object content = titleMap.get("content");
                if (content instanceof String s && !s.isBlank()) return s;
            }
        }
        return "[card]";
    }

    private Map<String, Object> requestHashPayload(String channelId, String target,
                                                   ResolvedMessageContent content, String extensions) {
        if (content.type() == MessageContentType.TEXT) {
            return Map.of("channel", channelId, "target", target, "text", content.text(), "extensions", extensions);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", channelId);
        payload.put("target", target);
        payload.put("type", content.type());
        payload.put("text", content.text());
        payload.put("contentJson", content.json());
        payload.put("extensions", extensions);
        return payload;
    }

    private MessageView view(Message m) { return new MessageView(m.id, m.channelInstanceId, m.status, m.createdAt, m.updatedAt); }

    public record SubmitRequest(String channelInstanceId, String target, String groupId, String userId,
                                String text, MessagePayload message, String idempotencyKey, Map<String, Object> extensions) {
        public SubmitRequest {
            // channelInstanceId is optional when a groupId or userId is provided; the channel
            // is resolved in MessageService.submit (and required there for raw target sends).
            if (message == null && (text == null || text.isBlank())) throw new BusinessException("text 不能为空");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new BusinessException("idempotencyKey 不能为空");
        }
    }
    public record MessagePayload(MessageContentType type, String text, Map<String, Object> card) {}
    public record MessageView(String id, String channelInstanceId, MessageStatus status, Instant createdAt, Instant updatedAt) {}
    public record BatchView(int totalMessages, List<MessageView> messages) {}
    public record AttemptView(int attemptNumber, DeliveryResult.Type result, String errorCode, String diagnostic, Instant startedAt, Instant finishedAt) {}
    public record MessageDetail(MessageView message, List<AttemptView> attempts) {}

    private record Target(String targetValue, String suffix) {}
    private record ResolvedMessageContent(MessageContentType type, String text, String json) {}
}
