package com.meetbhavesh.notification.worker.status;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.entity.Notification;
import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.enums.NotificationStatus;
import com.meetbhavesh.notification.domain.enums.Priority;
import com.meetbhavesh.notification.domain.enums.Severity;
import com.meetbhavesh.notification.domain.repository.DeliveryTaskRepository;
import com.meetbhavesh.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStatusRollupServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private DeliveryTaskRepository deliveryTaskRepository;

    private NotificationStatusRollupService rollupService;

    @BeforeEach
    void setUp() {
        rollupService = new NotificationStatusRollupService(notificationRepository, deliveryTaskRepository);
    }

    private DeliveryTask taskWithStatus(DeliveryTaskStatus status) {
        return DeliveryTask.builder()
                .notificationId("n1").recipientId("user-1").channel(Channel.EMAIL).status(status).build();
    }

    private Notification notificationWithStatus(NotificationStatus status) {
        return Notification.builder()
                .idempotencyKey("k")
                .sourceSystem("s")
                .notificationType("t")
                .severity(Severity.MEDIUM)
                .priority(Priority.NORMAL)
                .status(status)
                .build();
    }

    @Test
    void anyInFlightTask_rollsUpToInProgress() {
        Notification notification = notificationWithStatus(NotificationStatus.ROUTED);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(deliveryTaskRepository.findByNotificationId("n1"))
                .thenReturn(List.of(taskWithStatus(DeliveryTaskStatus.SUCCEEDED), taskWithStatus(DeliveryTaskStatus.QUEUED)));

        rollupService.rollUp("n1");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.IN_PROGRESS);
        verify(notificationRepository).save(notification);
    }

    @Test
    void allSucceeded_rollsUpToCompleted() {
        Notification notification = notificationWithStatus(NotificationStatus.IN_PROGRESS);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(deliveryTaskRepository.findByNotificationId("n1"))
                .thenReturn(List.of(taskWithStatus(DeliveryTaskStatus.SUCCEEDED), taskWithStatus(DeliveryTaskStatus.SUCCEEDED)));

        rollupService.rollUp("n1");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.COMPLETED);
    }

    @Test
    void mixOfSucceededAndTerminalFailed_rollsUpToPartiallyDelivered() {
        Notification notification = notificationWithStatus(NotificationStatus.IN_PROGRESS);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(deliveryTaskRepository.findByNotificationId("n1"))
                .thenReturn(List.of(taskWithStatus(DeliveryTaskStatus.SUCCEEDED), taskWithStatus(DeliveryTaskStatus.FAILED_TERMINAL)));

        rollupService.rollUp("n1");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PARTIALLY_DELIVERED);
    }

    @Test
    void allTerminalFailed_rollsUpToFailed() {
        Notification notification = notificationWithStatus(NotificationStatus.IN_PROGRESS);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(deliveryTaskRepository.findByNotificationId("n1"))
                .thenReturn(List.of(taskWithStatus(DeliveryTaskStatus.FAILED_TERMINAL)));

        rollupService.rollUp("n1");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void statusUnchanged_doesNotSave() {
        Notification notification = notificationWithStatus(NotificationStatus.COMPLETED);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(deliveryTaskRepository.findByNotificationId("n1"))
                .thenReturn(List.of(taskWithStatus(DeliveryTaskStatus.SUCCEEDED)));

        rollupService.rollUp("n1");

        verify(notificationRepository, never()).save(any());
    }
}
