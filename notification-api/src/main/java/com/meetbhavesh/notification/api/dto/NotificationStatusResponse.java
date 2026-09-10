package com.meetbhavesh.notification.api.dto;

import com.meetbhavesh.notification.domain.enums.NotificationStatus;

import java.time.Instant;
import java.util.List;

public record NotificationStatusResponse(
        String notificationId,
        NotificationStatus status,
        String sourceSystem,
        String eventId,
        Instant createdAt,
        List<DeliveryStatusItem> deliveries
) {
}
