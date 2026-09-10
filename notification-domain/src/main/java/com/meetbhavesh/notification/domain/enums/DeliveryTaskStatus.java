package com.meetbhavesh.notification.domain.enums;

/**
 * Status of a single (recipient, channel) delivery attempt line item.
 * CREATED -> QUEUED -> SENDING -> SUCCEEDED
 *                          |
 *                          v
 *                 FAILED_RETRYABLE --(backoff, re-queue, bounded)--> QUEUED
 *                          |
 *                          v (attempts exhausted, or permanent failure)
 *                   FAILED_TERMINAL
 *
 * SUPPRESSED covers dedup matches / invalid recipients caught pre-send.
 */
public enum DeliveryTaskStatus {
    CREATED,
    QUEUED,
    SENDING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    SUPPRESSED
}
