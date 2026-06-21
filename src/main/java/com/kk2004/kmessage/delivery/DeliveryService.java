package com.kk2004.kmessage.delivery;

import com.kk2004.common.web.RequestContext;
import com.kk2004.kmessage.channel.*;
import com.kk2004.kmessage.config.KMessageProperties;
import com.kk2004.kmessage.domain.*;
import com.kk2004.kmessage.domain.Entities.*;
import com.kk2004.kmessage.persistence.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private final TaskRepository tasks; private final MessageRepository messages; private final AttemptRepository attempts;
    private final ChannelRepository channels; private final ChannelAdapterRegistry adapters; private final KMessageProperties properties;
    private final MeterRegistry metrics; private final String workerId = UUID.randomUUID().toString();

    public DeliveryService(TaskRepository tasks, MessageRepository messages, AttemptRepository attempts, ChannelRepository channels,
                           ChannelAdapterRegistry adapters, KMessageProperties properties, MeterRegistry metrics) {
        this.tasks = tasks; this.messages = messages; this.attempts = attempts; this.channels = channels;
        this.adapters = adapters; this.properties = properties; this.metrics = metrics;
        metrics.gauge("kmessage.delivery.backlog", tasks, t -> t.countByNextAttemptAtLessThanEqual(Instant.now()));
    }

    @Transactional
    public List<String> claim(int limit) {
        Instant now = Instant.now();
        List<DeliveryTask> claimable = tasks.findClaimable(now, PageRequest.of(0, limit));
        claimable.forEach(t -> { t.workerId = workerId; t.leaseUntil = now.plusSeconds(properties.worker().leaseSeconds()); t.updatedAt = now; });
        return claimable.stream().map(t -> t.id).toList();
    }

    @Transactional
    public void deliver(String taskId) {
        DeliveryTask task = tasks.findById(taskId).orElse(null);
        if (task == null || !workerId.equals(task.workerId)) return;
        Message message = messages.findById(task.messageId).orElseThrow();
        ChannelInstance channel = channels.findById(message.channelInstanceId).orElseThrow();
        Instant started = Instant.now();
        message.status = MessageStatus.DELIVERING; message.updatedAt = started;
        task.attemptCount++;
        if (message.traceId != null) RequestContext.put(RequestContext.TRACE_ID, message.traceId);
        DeliveryResult result;
        try { result = adapters.require(channel.channelType).send(channel, message); }
        catch (Exception e) { result = DeliveryResult.transientFailure("UNEXPECTED", e.getClass().getSimpleName()); }
        finally { RequestContext.clear(); }

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.messageId = message.id; attempt.attemptNumber = task.attemptCount; attempt.resultType = result.type();
        attempt.providerReference = result.providerReference(); attempt.errorCode = result.errorCode(); attempt.diagnostic = result.diagnostic();
        attempt.startedAt = started; attempt.finishedAt = Instant.now(); attempts.save(attempt);
        metrics.counter("kmessage.delivery.attempts", "channel", channel.channelType.name(), "result", result.type().name()).increment();

        if (result.type() == DeliveryResult.Type.SUCCESS) {
            message.status = MessageStatus.DELIVERED; tasks.delete(task);
        } else if (result.type() == DeliveryResult.Type.PERMANENT_FAILURE || task.attemptCount >= properties.worker().maxAttempts()) {
            message.status = MessageStatus.FAILED; tasks.delete(task);
            log.warn("delivery_failed messageId={} callerId={} channel={} instance={} attempt={} error={}",
                    message.id, message.callerId, channel.channelType, channel.id, task.attemptCount, result.errorCode());
        } else {
            message.status = MessageStatus.RETRYING;
            long delay = Math.min(300, (1L << Math.min(task.attemptCount, 8))) + java.util.concurrent.ThreadLocalRandom.current().nextLong(3);
            task.nextAttemptAt = Instant.now().plusSeconds(delay); task.leaseUntil = null; task.workerId = null;
        }
        message.updatedAt = Instant.now();
    }
}
