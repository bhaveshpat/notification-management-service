package com.meetbhavesh.notification.worker;

import com.meetbhavesh.notification.domain.entity.AuditEvent;
import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.AuditEventType;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.repository.AuditEventRepository;
import com.meetbhavesh.notification.domain.repository.DeliveryTaskRepository;
import com.meetbhavesh.notification.worker.provider.ChannelProvider;
import com.meetbhavesh.notification.worker.retry.RetryPolicy;
import com.meetbhavesh.notification.worker.status.NotificationStatusRollupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls delivery_tasks for QUEUED / FAILED_RETRYABLE rows that are due,
 * calls the (simulated) channel provider, applies the retry policy, and
 * rolls the outcome up into the parent Notification's aggregate status.
 *
 * Documented limitation: claiming is a plain read-then-update, not a locking
 * SELECT ... FOR UPDATE SKIP LOCKED. Safe for the single-instance prototype;
 * would need a real claim query before running >1 worker instance.
 */
@Component
public class DeliveryTaskPoller {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTaskPoller.class);

    private static final List<DeliveryTaskStatus> POLLABLE_STATUSES =
            List.of(DeliveryTaskStatus.QUEUED, DeliveryTaskStatus.FAILED_RETRYABLE);

    private final DeliveryTaskRepository deliveryTaskRepository;
    private final AuditEventRepository auditEventRepository;
    private final ChannelProvider channelProvider;
    private final RetryPolicy retryPolicy;
    private final NotificationStatusRollupService rollupService;

    @Value("${notification.worker.batch-size:20}")
    private int batchSize = 20;

    public DeliveryTaskPoller(DeliveryTaskRepository deliveryTaskRepository,
                               AuditEventRepository auditEventRepository,
                               ChannelProvider channelProvider,
                               RetryPolicy retryPolicy,
                               NotificationStatusRollupService rollupService) {
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.auditEventRepository = auditEventRepository;
        this.channelProvider = channelProvider;
        this.retryPolicy = retryPolicy;
        this.rollupService = rollupService;
    }

    @Scheduled(fixedDelayString = "${notification.worker.poll-interval-ms:2000}")
    @Transactional
    public void pollAndProcess() {
        Pageable page = PageRequest.of(0, batchSize);
        List<DeliveryTask> due = deliveryTaskRepository.findDueForProcessing(POLLABLE_STATUSES, Instant.now(), page);
        if (due.isEmpty()) {
            return;
        }

        log.info("Claiming {} due delivery task(s)", due.size());
        for (DeliveryTask task : due) {
            processOne(task);
        }
    }

    private void processOne(DeliveryTask task) {
        task.setStatus(DeliveryTaskStatus.SENDING);
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setLastAttemptAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        deliveryTaskRepository.save(task);
        audit(task.getNotificationId(), task.getId(), AuditEventType.DELIVERY_ATTEMPTED,
                "attempt=" + task.getAttemptCount() + " channel=" + task.getChannel());

        ChannelProvider.Result result = channelProvider.send(task);

        if (result.success()) {
            task.setStatus(DeliveryTaskStatus.SUCCEEDED);
            task.setProviderMessageId(result.providerMessageId());
            task.setLastFailureReason(null);
            task.setNextAttemptAt(null);
            task.setUpdatedAt(Instant.now());
            deliveryTaskRepository.save(task);
            audit(task.getNotificationId(), task.getId(), AuditEventType.DELIVERY_SUCCEEDED,
                    "providerMessageId=" + result.providerMessageId());
        } else {
            task.setLastFailureReason(result.failureReason());
            boolean canRetry = retryPolicy.isRetryable(result.failureReason())
                    && task.getAttemptCount() < task.getMaxAttempts();

            if (canRetry) {
                task.setStatus(DeliveryTaskStatus.FAILED_RETRYABLE);
                task.setNextAttemptAt(Instant.now().plus(retryPolicy.backoffFor(task.getAttemptCount())));
                task.setUpdatedAt(Instant.now());
                deliveryTaskRepository.save(task);
                audit(task.getNotificationId(), task.getId(), AuditEventType.DELIVERY_FAILED,
                        "reason=" + result.failureReason() + " attempt=" + task.getAttemptCount() + " retryable=true");
                audit(task.getNotificationId(), task.getId(), AuditEventType.RETRY_SCHEDULED,
                        "nextAttemptAt=" + task.getNextAttemptAt());
            } else {
                task.setStatus(DeliveryTaskStatus.FAILED_TERMINAL);
                task.setNextAttemptAt(null);
                task.setUpdatedAt(Instant.now());
                deliveryTaskRepository.save(task);
                audit(task.getNotificationId(), task.getId(), AuditEventType.DELIVERY_FAILED,
                        "reason=" + result.failureReason() + " attempt=" + task.getAttemptCount() + " terminal=true");
            }
        }

        rollupService.rollUp(task.getNotificationId());
    }

    private void audit(String notificationId, String deliveryTaskId, AuditEventType type, String details) {
        auditEventRepository.save(AuditEvent.builder()
                .notificationId(notificationId)
                .deliveryTaskId(deliveryTaskId)
                .eventType(type)
                .occurredAt(Instant.now())
                .details(details)
                .build());
    }
}
