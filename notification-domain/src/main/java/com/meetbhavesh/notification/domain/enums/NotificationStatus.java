package com.meetbhavesh.notification.domain.enums;

/**
 * Aggregate status of a Notification, derived from (and kept in sync with) the
 * status of its child DeliveryTasks. See docs/ARCHITECTURE.md for the state
 * diagram and the rationale for maintaining this as a synchronously-updated
 * field rather than a pure read-time aggregation.
 */
public enum NotificationStatus {
    RECEIVED,
    REJECTED,
    ROUTED,
    IN_PROGRESS,
    COMPLETED,
    PARTIALLY_DELIVERED,
    FAILED,
    EXPIRED
}
