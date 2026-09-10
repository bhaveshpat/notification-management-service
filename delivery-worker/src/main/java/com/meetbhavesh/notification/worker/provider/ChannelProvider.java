package com.meetbhavesh.notification.worker.provider;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.FailureReason;

/**
 * Abstraction over "actually sending" through a channel. Kept as an
 * interface so a real provider (SES, Twilio, FCM, a webhook client) can be
 * swapped in later without touching the poller/retry logic.
 */
public interface ChannelProvider {

    Result send(DeliveryTask task);

    record Result(boolean success, String providerMessageId, FailureReason failureReason) {
        public static Result success(String providerMessageId) {
            return new Result(true, providerMessageId, null);
        }

        public static Result failure(FailureReason reason) {
            return new Result(false, null, reason);
        }
    }
}
