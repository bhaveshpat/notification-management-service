package com.meetbhavesh.notification.domain.enums;

public enum FailureReason {
    TRANSIENT_PROVIDER_FAILURE,
    PERMANENT_PROVIDER_REJECTION,
    INVALID_RECIPIENT,
    RATE_LIMITED,
    TIMEOUT,
    AUTH_ERROR
}
