package com.meetbhavesh.notification.worker.retry;

import com.meetbhavesh.notification.domain.enums.FailureReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy();

    @Test
    void transientRateLimitedAndTimeoutAreRetryable() {
        assertThat(retryPolicy.isRetryable(FailureReason.TRANSIENT_PROVIDER_FAILURE)).isTrue();
        assertThat(retryPolicy.isRetryable(FailureReason.RATE_LIMITED)).isTrue();
        assertThat(retryPolicy.isRetryable(FailureReason.TIMEOUT)).isTrue();
    }

    @Test
    void permanentRejectionInvalidRecipientAndAuthErrorAreNotRetryable() {
        assertThat(retryPolicy.isRetryable(FailureReason.PERMANENT_PROVIDER_REJECTION)).isFalse();
        assertThat(retryPolicy.isRetryable(FailureReason.INVALID_RECIPIENT)).isFalse();
        assertThat(retryPolicy.isRetryable(FailureReason.AUTH_ERROR)).isFalse();
    }

    @Test
    void backoffDoublesWithEachAttemptUpToCap() {
        assertThat(retryPolicy.backoffFor(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(retryPolicy.backoffFor(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(retryPolicy.backoffFor(3)).isEqualTo(Duration.ofSeconds(40));
        assertThat(retryPolicy.backoffFor(10)).isEqualTo(Duration.ofMinutes(5)); // capped
    }
}
