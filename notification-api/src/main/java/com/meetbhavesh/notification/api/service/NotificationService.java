package com.meetbhavesh.notification.api.service;

import com.meetbhavesh.notification.api.dto.DeliveryStatusItem;
import com.meetbhavesh.notification.api.dto.NotificationStatusResponse;
import com.meetbhavesh.notification.api.dto.NotificationSubmitRequest;
import com.meetbhavesh.notification.api.dto.NotificationSubmitResponse;
import com.meetbhavesh.notification.api.exception.NotificationNotFoundException;
import com.meetbhavesh.notification.api.routing.ChannelRouter;
import com.meetbhavesh.notification.domain.entity.AuditEvent;
import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.entity.Notification;
import com.meetbhavesh.notification.domain.enums.AuditEventType;
import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.enums.NotificationStatus;
import com.meetbhavesh.notification.domain.repository.AuditEventRepository;
import com.meetbhavesh.notification.domain.repository.DeliveryTaskRepository;
import com.meetbhavesh.notification.domain.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Greenfield submit/status flow (4.1-4.4). Scope note: this increment covers
 * submission, routing, dedup, and status lookup. It does NOT yet cover
 * "reprocessing a queued delivery must not create uncontrolled duplicate
 * side effects" (the other half of 4.4) or retry/failure handling (4.5) --
 * those live in delivery-worker's processing logic, the next increment.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DeliveryTaskRepository deliveryTaskRepository;
    private final AuditEventRepository auditEventRepository;
    private final ChannelRouter channelRouter;

    public NotificationService(NotificationRepository notificationRepository,
                                DeliveryTaskRepository deliveryTaskRepository,
                                AuditEventRepository auditEventRepository,
                                ChannelRouter channelRouter) {
        this.notificationRepository = notificationRepository;
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.auditEventRepository = auditEventRepository;
        this.channelRouter = channelRouter;
    }

    @Transactional
    public NotificationSubmitResponse submit(NotificationSubmitRequest request) {
        Optional<Notification> existing = notificationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Notification duplicate = existing.get();
            audit(duplicate.getId(), null, AuditEventType.DUPLICATE_SUPPRESSED,
                    "Resubmission with idempotency key " + request.idempotencyKey());
            return new NotificationSubmitResponse(duplicate.getId(), duplicate.getStatus(), true, duplicate.getCreatedAt());
        }

        Notification notification = Notification.builder()
                .idempotencyKey(request.idempotencyKey())
                .sourceSystem(request.sourceSystem())
                .eventId(request.eventId())
                .notificationType(request.notificationType())
                .severity(request.severity())
                .priority(request.priority())
                .recipients(request.recipients())
                .requestedChannels(request.requestedChannels())
                .scheduledAt(request.scheduledAt())
                .expiresAt(request.expiresAt())
                .status(NotificationStatus.RECEIVED)
                .build();
        notification = notificationRepository.save(notification);
        audit(notification.getId(), null, AuditEventType.NOTIFICATION_ACCEPTED,
                "source=" + request.sourceSystem() + " type=" + request.notificationType());

        for (String recipientId : request.recipients()) {
            List<Channel> resolvedChannels = channelRouter.route(recipientId, request.requestedChannels(), request.severity());
            for (Channel channel : resolvedChannels) {
                DeliveryTask task = DeliveryTask.builder()
                        .notificationId(notification.getId())
                        .recipientId(recipientId)
                        .channel(channel)
                        .status(DeliveryTaskStatus.QUEUED)
                        .build();
                task = deliveryTaskRepository.save(task);
                audit(notification.getId(), task.getId(), AuditEventType.DELIVERY_QUEUED,
                        "recipient=" + recipientId + " channel=" + channel);
            }
        }
        audit(notification.getId(), null, AuditEventType.ROUTING_DECISION_MADE,
                "recipients=" + request.recipients().size());

        notification.setStatus(NotificationStatus.ROUTED);
        notification = notificationRepository.save(notification);

        return new NotificationSubmitResponse(notification.getId(), notification.getStatus(), false, notification.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public NotificationStatusResponse getStatus(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        List<DeliveryStatusItem> deliveries = deliveryTaskRepository.findByNotificationId(notificationId).stream()
                .map(t -> new DeliveryStatusItem(
                        t.getRecipientId(), t.getChannel(), t.getStatus(), t.getAttemptCount(),
                        t.getLastAttemptAt(), t.getNextAttemptAt(), t.getLastFailureReason()))
                .toList();

        return new NotificationStatusResponse(
                notification.getId(), notification.getStatus(), notification.getSourceSystem(),
                notification.getEventId(), notification.getCreatedAt(), deliveries);
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
