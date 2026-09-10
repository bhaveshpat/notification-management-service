package com.meetbhavesh.notification.worker;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.repository.DeliveryTaskRepository;
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
 * Polls delivery_tasks for QUEUED-and-due rows and processes them.
 *
 * Scope note: this is currently a wiring skeleton. It proves the shared-DB
 * claim path end-to-end (QUEUED -> SENDING) but does not yet call a real
 * channel provider, apply routing policy, or implement retry/backoff --
 * that lands in the next increment. See docs/ARCHITECTURE.md open questions.
 *
 * Documented limitation: claiming is a plain read-then-update, not a locking
 * SELECT ... FOR UPDATE SKIP LOCKED. Safe for the single-instance prototype;
 * would need a real claim query before running >1 worker instance.
 */
@Component
public class DeliveryTaskPoller {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTaskPoller.class);

    private final DeliveryTaskRepository deliveryTaskRepository;

    @Value("${notification.worker.batch-size:20}")
    private int batchSize;

    public DeliveryTaskPoller(DeliveryTaskRepository deliveryTaskRepository) {
        this.deliveryTaskRepository = deliveryTaskRepository;
    }

    @Scheduled(fixedDelayString = "${notification.worker.poll-interval-ms:2000}")
    @Transactional
    public void pollAndProcess() {
        Pageable page = PageRequest.of(0, batchSize);
        List<DeliveryTask> due = deliveryTaskRepository.findDueForProcessing(
                DeliveryTaskStatus.QUEUED, Instant.now(), page);

        if (due.isEmpty()) {
            return;
        }

        log.info("Claiming {} due delivery task(s)", due.size());
        for (DeliveryTask task : due) {
            task.setStatus(DeliveryTaskStatus.SENDING);
            task.setAttemptCount(task.getAttemptCount() + 1);
            task.setLastAttemptAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            deliveryTaskRepository.save(task);

            // TODO next increment: route to the correct channel provider,
            // interpret its result, and transition to SUCCEEDED /
            // FAILED_RETRYABLE (with backoff) / FAILED_TERMINAL, plus an
            // AuditEvent per attempt.
        }
    }
}
