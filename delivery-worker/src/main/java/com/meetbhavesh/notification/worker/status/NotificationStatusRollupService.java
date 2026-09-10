package com.meetbhavesh.notification.worker.status;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.entity.Notification;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.enums.NotificationStatus;
import com.meetbhavesh.notification.domain.repository.DeliveryTaskRepository;
import com.meetbhavesh.notification.domain.repository.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Recomputes Notification.status from the current state of its DeliveryTask
 * rows, and persists it if changed. Called after every DeliveryTask
 * transition rather than computed at read time -- see docs/ARCHITECTURE.md
 * for the documented trade-off (a second place that must stay consistent,
 * in exchange for a cheap status-lookup API).
 */
@Component
public class NotificationStatusRollupService {

    private static final Set<DeliveryTaskStatus> IN_FLIGHT = Set.of(
            DeliveryTaskStatus.QUEUED, DeliveryTaskStatus.SENDING, DeliveryTaskStatus.FAILED_RETRYABLE);

    private final NotificationRepository notificationRepository;
    private final DeliveryTaskRepository deliveryTaskRepository;

    public NotificationStatusRollupService(NotificationRepository notificationRepository,
                                            DeliveryTaskRepository deliveryTaskRepository) {
        this.notificationRepository = notificationRepository;
        this.deliveryTaskRepository = deliveryTaskRepository;
    }

    @Transactional
    public void rollUp(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            return;
        }

        List<DeliveryTask> tasks = deliveryTaskRepository.findByNotificationId(notificationId);
        if (tasks.isEmpty()) {
            return;
        }

        boolean anyInFlight = tasks.stream().anyMatch(t -> IN_FLIGHT.contains(t.getStatus()));
        boolean anySucceeded = tasks.stream().anyMatch(t -> t.getStatus() == DeliveryTaskStatus.SUCCEEDED);
        boolean anyTerminalFailure = tasks.stream().anyMatch(t -> t.getStatus() == DeliveryTaskStatus.FAILED_TERMINAL);

        NotificationStatus newStatus;
        if (anyInFlight) {
            newStatus = NotificationStatus.IN_PROGRESS;
        } else if (anySucceeded && anyTerminalFailure) {
            newStatus = NotificationStatus.PARTIALLY_DELIVERED;
        } else if (anySucceeded) {
            newStatus = NotificationStatus.COMPLETED;
        } else {
            newStatus = NotificationStatus.FAILED;
        }

        if (newStatus != notification.getStatus()) {
            notification.setStatus(newStatus);
            notificationRepository.save(notification);
        }
    }
}
