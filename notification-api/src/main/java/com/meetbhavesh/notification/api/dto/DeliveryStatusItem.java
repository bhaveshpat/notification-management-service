package com.meetbhavesh.notification.api.dto;

import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.enums.FailureReason;

import java.time.Instant;

public record DeliveryStatusItem(
        String recipientId,
        Channel channel,
        DeliveryTaskStatus status,
        int attemptCount,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        FailureReason lastFailureReason
) {
}
