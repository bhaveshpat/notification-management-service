package com.meetbhavesh.notification.domain.entity;

import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.NotificationStatus;
import com.meetbhavesh.notification.domain.enums.Priority;
import com.meetbhavesh.notification.domain.enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single notification submission (4.1). Carries no per-recipient delivery
 * state itself -- that lives in {@link DeliveryTask}, one row per
 * (recipient, channel) pair. See docs/ARCHITECTURE.md for the full model.
 */
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_idempotency_key", columnNames = "idempotencyKey")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** Client-supplied (or derived) key used to detect duplicate submissions (4.4). */
    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String sourceSystem;

    /** Correlates back to the originating event in the source system. */
    @Column(updatable = false)
    private String eventId;

    @Column(nullable = false, updatable = false)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Priority priority;

    /** Recipients as opaque identifiers (email/phone/user-id) -- resolved by the
     *  routing step at delivery-task-creation time, not stored richly here. */
    @ElementCollection
    @CollectionTable(name = "notification_recipients", joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "recipient_id", nullable = false)
    @Builder.Default
    private List<String> recipients = new ArrayList<>();

    @ElementCollection(targetClass = Channel.class)
    @CollectionTable(name = "notification_requested_channels", joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "channel", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private List<Channel> requestedChannels = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.RECEIVED;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant scheduledAt;

    private Instant expiresAt;

    @Version
    private Long version;
}
