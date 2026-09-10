package com.meetbhavesh.notification.worker.retry;

import com.meetbhavesh.notification.domain.enums.FailureReason;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Bounded exponential backoff (4.5). Classification of retryable vs.
 * terminal failure reasons: transient provider failures, rate limits, and
 * timeouts are retried (up to DeliveryTask.maxAttempts); permanent
 * rejections, invalid recipients, and auth errors are never retried
 * regardless of remaining attempts -- retrying those wastes attempts on
 * something that cannot succeed.
 */
@Component
public class RetryPolicy {

    private static final Set<FailureReason> RETRYABLE = Set.of(
            FailureReason.TRANSIENT_PROVIDER_FAILURE,
            FailureReason.RATE_LIMITED,
            FailureReason.TIMEOUT
    );

    private static final Duration BASE_DELAY = Duration.ofSeconds(10);
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);

    public boolean isRetryable(FailureReason reason) {
        return RETRYABLE.contains(reason);
    }

    /** base * 2^(attemptCount-1), capped at MAX_DELAY. */
    public Duration backoffFor(int attemptCount) {
        long millis = BASE_DELAY.toMillis() * (1L << Math.max(0, attemptCount - 1));
        return Duration.ofMillis(Math.min(millis, MAX_DELAY.toMillis()));
    }
}
