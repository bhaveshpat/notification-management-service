package com.meetbhavesh.notification.domain.repository;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DeliveryTaskRepositoryTest {

    @Autowired
    private DeliveryTaskRepository deliveryTaskRepository;

    private DeliveryTask task(String notificationId, DeliveryTaskStatus status, Instant nextAttemptAt) {
        return DeliveryTask.builder()
                .notificationId(notificationId)
                .recipientId("user-1")
                .channel(Channel.EMAIL)
                .status(status)
                .nextAttemptAt(nextAttemptAt)
                .build();
    }

    @Test
    void findsQueuedAndDueFailedRetryableTasks_excludesNotYetDueAndOtherStatuses() {
        Instant now = Instant.now();

        DeliveryTask dueQueued = deliveryTaskRepository.save(task("n1", DeliveryTaskStatus.QUEUED, null));
        DeliveryTask dueRetry = deliveryTaskRepository.save(
                task("n1", DeliveryTaskStatus.FAILED_RETRYABLE, now.minus(1, ChronoUnit.MINUTES)));
        deliveryTaskRepository.save(
                task("n1", DeliveryTaskStatus.FAILED_RETRYABLE, now.plus(1, ChronoUnit.HOURS))); // not due yet
        deliveryTaskRepository.save(task("n1", DeliveryTaskStatus.SUCCEEDED, null)); // wrong status
        deliveryTaskRepository.save(task("n1", DeliveryTaskStatus.FAILED_TERMINAL, null)); // wrong status

        List<DeliveryTask> due = deliveryTaskRepository.findDueForProcessing(
                List.of(DeliveryTaskStatus.QUEUED, DeliveryTaskStatus.FAILED_RETRYABLE),
                now,
                PageRequest.of(0, 20));

        assertThat(due).extracting(DeliveryTask::getId)
                .containsExactlyInAnyOrder(dueQueued.getId(), dueRetry.getId());
    }

    @Test
    void findsByNotificationId() {
        deliveryTaskRepository.save(task("n-target", DeliveryTaskStatus.QUEUED, null));
        deliveryTaskRepository.save(task("n-other", DeliveryTaskStatus.QUEUED, null));

        List<DeliveryTask> results = deliveryTaskRepository.findByNotificationId("n-target");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNotificationId()).isEqualTo("n-target");
    }
}
