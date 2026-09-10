package com.meetbhavesh.notification.domain.entity;

import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.enums.FailureReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One (recipient, channel) delivery attempt line item for a Notification.
 * This row IS the async work queue: notification-api inserts it in QUEUED
 * state, delivery-worker polls for QUEUED rows whose nextAttemptAt has
 * elapsed, claims, and processes them. See docs/ARCHITECTURE.md.
 */
@Entity
@Table(name = "delivery_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryTask {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, updatable = false)
    private String notificationId;

    @Column(nullable = false, updatable = false)
    private String recipientId;

    /** Channel actually selected by routing -- may differ from what was requested. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeliveryTaskStatus status = DeliveryTaskStatus.CREATED;

    @Builder.Default
    private int attemptCount = 0;

    @Builder.Default
    private int maxAttempts = 5;

    private Instant lastAttemptAt;

    /** Earliest time this task is eligible to be (re)claimed by the worker. */
    private Instant nextAttemptAt;

    @Enumerated(EnumType.STRING)
    private FailureReason lastFailureReason;

    /** Id assigned by the channel provider, if any -- for cross-referencing. */
    private String providerMessageId;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @Version
    private Long version;
}
