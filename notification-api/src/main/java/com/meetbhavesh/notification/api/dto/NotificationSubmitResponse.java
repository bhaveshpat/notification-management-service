package com.meetbhavesh.notification.api.dto;

import com.meetbhavesh.notification.domain.enums.NotificationStatus;

import java.time.Instant;

public record NotificationSubmitResponse(
        String notificationId,
        NotificationStatus status,
        boolean duplicate,
        Instant createdAt
) {
}
