package com.meetbhavesh.notification.domain.entity;

import com.meetbhavesh.notification.domain.enums.AuditEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail (4.9). `details` must never contain the notification
 * payload, recipient PII beyond the opaque recipient id, or provider
 * credentials -- short structured summaries only.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, updatable = false)
    private String notificationId;

    /** Null for notification-level events (accepted/rejected/expired). */
    private String deliveryTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditEventType eventType;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(length = 500)
    private String details;
}
