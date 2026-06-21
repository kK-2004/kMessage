package com.kk2004.kmessage.channel;

import com.kk2004.common.exception.BusinessException;
import com.kk2004.kmessage.domain.*;
import com.kk2004.kmessage.domain.Entities.*;
import com.kk2004.kmessage.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
public class ChannelService {
    private final ChannelRepository channels;
    private final CallerRepository callers;
    private final GrantRepository grants;
    private final MessageRepository messages;
    private final TaskRepository tasks;
    private final AttemptRepository attempts;
    private final ChannelContactRepository contacts;
    private final AppUserRepository appUsers;
    private final UserGroupRepository userGroups;
    private final UserGroupMemberRepository groupMembers;
    public ChannelService(ChannelRepository channels, CallerRepository callers, GrantRepository grants,
                          MessageRepository messages, TaskRepository tasks, AttemptRepository attempts,
                          ChannelContactRepository contacts,
                          AppUserRepository appUsers, UserGroupRepository userGroups,
                          UserGroupMemberRepository groupMembers) {
        this.channels = channels; this.callers = callers; this.grants = grants;
        this.messages = messages; this.tasks = tasks; this.attempts = attempts;
        this.contacts = contacts;
        this.appUsers = appUsers; this.userGroups = userGroups; this.groupMembers = groupMembers;
    }

    @Transactional
    public ChannelView create(ChannelType type, String name, boolean enabled, String credentialRef, String configJson) {
        if (!type.implemented())
            throw new BusinessException(type.getLabel() + " 渠道尚未实现，不能创建实例");
        if (credentialRef == null || credentialRef.isBlank()) throw new BusinessException("凭据不能为空");
        if (channels.findByName(name).isPresent()) throw new BusinessException(409, "渠道名称已存在");
        ChannelInstance c = new ChannelInstance();
        c.channelType = type; c.name = name; c.enabled = enabled; c.credentialRef = credentialRef;
        c.configJson = configJson == null ? "{}" : configJson;
        return view(channels.save(c));
    }

    @Transactional
    public ChannelView update(String id, String name, boolean enabled, String credentialRef, String configJson) {
        ChannelInstance c = require(id);
        if (c.channelType == ChannelType.EMAIL && enabled) throw new BusinessException("邮箱渠道尚未实现，不能启用");
        if (name != null) {
            if (name.isBlank()) throw new BusinessException("渠道名称不能为空");
            Optional<ChannelInstance> existing = channels.findByName(name);
            if (existing.isPresent() && !existing.get().id.equals(c.id))
                throw new BusinessException(409, "渠道名称已存在");
            c.name = name;
        }
        c.enabled = enabled;
        if (credentialRef != null) {
            if (credentialRef.isBlank()) throw new BusinessException("凭据不能为空");
            c.credentialRef = credentialRef;
        }
        if (configJson != null) c.configJson = configJson;
        c.updatedAt = Instant.now();
        return view(c);
    }

    /**
     * Incrementally persist freshly-fetched contacts for a channel instance: insert new ones,
     * refresh label + lastSeenAt for known targets. Returns the full persisted contact list
     * (history + just-synced), most-recently-seen first, so the UI can offer all known targets
     * even when a provider only exposes the last 48 hours of activity.
     */
    @Transactional
    public List<ContactOption> syncContacts(String channelInstanceId, List<ContactOption> fetched) {
        Instant now = Instant.now();
        for (ContactOption opt : fetched == null ? List.<ContactOption>of() : fetched) {
            Optional<ChannelContact> existing = contacts
                    .findByChannelInstanceIdAndTargetId(channelInstanceId, opt.id());
            if (existing.isPresent()) {
                ChannelContact c = existing.get();
                c.label = opt.label();
                c.contactType = opt.type();
                c.lastSeenAt = now;
                contacts.save(c);
            } else {
                ChannelContact c = new ChannelContact();
                c.channelInstanceId = channelInstanceId;
                c.targetId = opt.id();
                c.label = opt.label();
                c.contactType = opt.type();
                c.firstSeenAt = now;
                c.lastSeenAt = now;
                contacts.save(c);
            }
        }
        return mergedContactOptions(channelInstanceId);
    }

    private List<ContactOption> mergedContactOptions(String channelInstanceId) {
        LinkedHashMap<String, ContactOption> byTarget = new LinkedHashMap<>();
        for (ChannelContact c : contacts.findByChannelInstanceIdOrderByLastSeenAtDesc(channelInstanceId)) {
            byTarget.put(c.targetId, new ContactOption(
                    c.targetId,
                    hasText(c.label) ? c.label : c.targetId,
                    hasText(c.contactType) ? c.contactType : "user"));
        }
        for (AppUser u : appUsers.findByChannelInstanceId(channelInstanceId)) {
            byTarget.putIfAbsent(u.targetId, new ContactOption(u.targetId, appUserLabel(u), "user"));
        }
        return List.copyOf(byTarget.values());
    }

    private String appUserLabel(AppUser u) {
        if (hasText(u.name)) return u.name;
        if (hasText(u.phone)) return u.phone;
        if (hasText(u.email)) return u.email;
        return u.targetId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public List<ChannelView> list() { return channels.findAll().stream().map(this::view).toList(); }

    @Transactional
    public void grant(String callerId, String channelId) {
        callers.findById(callerId).orElseThrow(() -> new BusinessException(404, "调用方不存在"));
        require(channelId);
        if (!grants.existsByCallerIdAndChannelInstanceId(callerId, channelId)) grants.save(new Grant(callerId, channelId));
    }
    @Transactional public void revoke(String callerId, String channelId) {
        grants.deleteByCallerIdAndChannelInstanceId(callerId, channelId);
    }
    public ChannelInstance require(String id) {
        return channels.findById(id).orElseThrow(() -> new BusinessException(404, "渠道实例不存在"));
    }
    public ChannelView view(ChannelInstance c) {
        return new ChannelView(c.id, c.name, c.channelType, c.enabled, "redacted:" + c.credentialRef.substring(0, Math.min(c.credentialRef.length(), 8)), c.configJson, c.updatedAt);
    }

    /**
     * Summarize what references a channel instance before destructive deletion, so the
     * console can ask the operator to confirm. Does not mutate anything.
     */
    public DeletePreview previewDelete(String id) {
        require(id);
        List<String> appNames = grants.findByChannelInstanceId(id).stream()
                .map(g -> callers.findById(g.callerId).map(c -> c.name).orElse(g.callerId))
                .toList();
        long messageCount = messages.countByChannelInstanceId(id);
        return new DeletePreview(appNames.size(), appNames, messageCount);
    }

    /**
     * Hard-delete a channel instance and everything that references it, in FK-safe order:
     * delivery_attempts → delivery_tasks → messages → grants → channel_instance.
     */
    @Transactional
    public void delete(String id) {
        require(id);
        List<String> messageIds = messages.findIdsByChannelInstanceId(id);
        if (!messageIds.isEmpty()) {
            attempts.deleteByMessageIdIn(messageIds);
            tasks.deleteByMessageIdIn(messageIds);
            messages.deleteByChannelInstanceId(id);
        }
        grants.deleteByChannelInstanceId(id);
        contacts.deleteByChannelInstanceId(id);
        // Cascade user-group membership tree: delete members of this channel's groups, then groups, then users.
        // Null out self-referential parent_id first so group deletion doesn't trip the FK.
        List<UserGroup> channelGroups = userGroups.findByChannelInstanceId(id);
        if (!channelGroups.isEmpty()) {
            groupMembers.deleteByGroupIdIn(channelGroups.stream().map(g -> g.id).toList());
            channelGroups.forEach(g -> { g.parentId = null; userGroups.save(g); });
            userGroups.deleteByChannelInstanceId(id);
        }
        appUsers.deleteByChannelInstanceId(id);
        channels.deleteById(id);
    }

    public record ChannelView(String id, String name, ChannelType channelType, boolean enabled, String credential, String configJson, Instant updatedAt) {}
    public record DeletePreview(int grantedAppCount, List<String> appNames, long messageCount) {}
}
