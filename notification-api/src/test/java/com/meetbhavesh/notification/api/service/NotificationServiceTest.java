package com.meetbhavesh.notification.api.service;

import com.meetbhavesh.notification.api.dto.NotificationStatusResponse;
import com.meetbhavesh.notification.api.dto.NotificationSubmitRequest;
import com.meetbhavesh.notification.api.dto.NotificationSubmitResponse;
import com.meetbhavesh.notification.api.exception.NotificationNotFoundException;
import com.meetbhavesh.notification.api.routing.ChannelRouter;
import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.entity.Notification;
import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.NotificationStatus;
import com.meetbhavesh.notification.domain.enums.Priority;
import com.meetbhavesh.notification.domain.enums.Severity;
import com.meetbhavesh.notification.domain.repository.AuditEventRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private DeliveryTaskRepository deliveryTaskRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private ChannelRouter channelRouter;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, deliveryTaskRepository, auditEventRepository, channelRouter);
    }

    private NotificationSubmitRequest request(String idempotencyKey, List<String> recipients) {
        return new NotificationSubmitRequest(
                idempotencyKey, "order-service", "evt-1", "ORDER_SHIPPED",
                Severity.MEDIUM, Priority.NORMAL, recipients, List.of(Channel.EMAIL), null, null);
    }

    @Test
    void submit_createsNotificationAndOneDeliveryTaskPerRecipientChannel() {
        when(notificationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(channelRouter.route(anyString(), anyList(), any(Severity.class)))
                .thenReturn(List.of(Channel.EMAIL, Channel.SMS));
        when(deliveryTaskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationSubmitResponse response = notificationService.submit(request("key-1", List.of("user-1", "user-2")));

        assertThat(response.duplicate()).isFalse();
        assertThat(response.status()).isEqualTo(NotificationStatus.ROUTED);
        // 2 recipients x 2 routed channels = 4 delivery tasks
        verify(deliveryTaskRepository, times(4)).save(any(DeliveryTask.class));
        // notification saved twice: once as RECEIVED, once updated to ROUTED
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(auditEventRepository, atLeastOnce()).save(any());
    }

    @Test
    void submit_withExistingIdempotencyKey_returnsExistingWithoutCreatingNew() {
        Notification existing = Notification.builder()
                .idempotencyKey("dup-key")
                .sourceSystem("order-service")
                .notificationType("ORDER_SHIPPED")
                .severity(Severity.MEDIUM)
                .priority(Priority.NORMAL)
                .status(NotificationStatus.COMPLETED)
                .build();
        when(notificationRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existing));

        NotificationSubmitResponse response = notificationService.submit(request("dup-key", List.of("user-1")));

        assertThat(response.duplicate()).isTrue();
        assertThat(response.notificationId()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(NotificationStatus.COMPLETED);
        verify(deliveryTaskRepository, never()).save(any());
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(auditEventRepository).save(any());
    }

    @Test
    void getStatus_returnsAggregateAndPerDeliveryStatus() {
        Notification notification = Notification.builder()
                .idempotencyKey("key-2")
                .sourceSystem("order-service")
                .eventId("evt-9")
                .notificationType("ORDER_SHIPPED")
                .severity(Severity.MEDIUM)
                .priority(Priority.NORMAL)
                .status(NotificationStatus.IN_PROGRESS)
                .build();
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        DeliveryTask task = DeliveryTask.builder()
                .notificationId(notification.getId())
                .recipientId("user-1")
                .channel(Channel.EMAIL)
                .build();
        when(deliveryTaskRepository.findByNotificationId(notification.getId())).thenReturn(List.of(task));

        NotificationStatusResponse response = notificationService.getStatus(notification.getId());

        assertThat(response.status()).isEqualTo(NotificationStatus.IN_PROGRESS);
        assertThat(response.deliveries()).hasSize(1);
        assertThat(response.deliveries().get(0).recipientId()).isEqualTo("user-1");
    }

    @Test
    void getStatus_unknownId_throwsNotFound() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getStatus("missing"))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
