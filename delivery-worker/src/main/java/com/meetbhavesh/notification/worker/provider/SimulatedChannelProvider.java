package com.meetbhavesh.notification.worker.provider;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.FailureReason;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated channel provider -- documented assumption: there is no real
 * email/SMS/push/webhook integration in this prototype.
 *
 * Recipient id conventions, so specific outcomes can be exercised on demand
 * (for the brownfield/ambiguous scenarios, demos, or manual testing) rather
 * than relying purely on randomness:
 *  - starts with "invalid-"   -> INVALID_RECIPIENT (non-retryable)
 *  - starts with "blocked-"   -> AUTH_ERROR (non-retryable)
 *  - starts with "ratelimit-" -> RATE_LIMITED (retryable)
 *  - starts with "flaky-"     -> TRANSIENT_PROVIDER_FAILURE (retryable, every attempt)
 *  - anything else            -> ~85% success, ~15% TRANSIENT_PROVIDER_FAILURE
 */
@Component
public class SimulatedChannelProvider implements ChannelProvider {

    @Override
    public Result send(DeliveryTask task) {
        String recipientId = task.getRecipientId() == null ? "" : task.getRecipientId().toLowerCase();

        if (recipientId.startsWith("invalid-")) {
            return Result.failure(FailureReason.INVALID_RECIPIENT);
        }
        if (recipientId.startsWith("blocked-")) {
            return Result.failure(FailureReason.AUTH_ERROR);
        }
        if (recipientId.startsWith("ratelimit-")) {
            return Result.failure(FailureReason.RATE_LIMITED);
        }
        if (recipientId.startsWith("flaky-")) {
            return Result.failure(FailureReason.TRANSIENT_PROVIDER_FAILURE);
        }

        if (ThreadLocalRandom.current().nextInt(100) < 15) {
            return Result.failure(FailureReason.TRANSIENT_PROVIDER_FAILURE);
        }
        return Result.success("sim-" + task.getChannel() + "-" + UUID.randomUUID());
    }
}
