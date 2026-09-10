package com.meetbhavesh.notification.api.dto;

import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.Priority;
import com.meetbhavesh.notification.domain.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Client-supplied idempotencyKey is required: it's the sole dedup boundary
 * (4.4). Repeating a submission with the same key returns the original
 * notification's current state rather than creating a second one.
 */
public record NotificationSubmitRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String sourceSystem,
        String eventId,
        @NotBlank String notificationType,
        @NotNull Severity severity,
        @NotNull Priority priority,
        @NotEmpty List<@NotBlank String> recipients,
        @NotEmpty List<@NotNull Channel> requestedChannels,
        Instant scheduledAt,
        Instant expiresAt
) {
}
