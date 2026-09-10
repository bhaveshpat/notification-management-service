package com.meetbhavesh.notification.worker.provider;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.FailureReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedChannelProviderTest {

    private final SimulatedChannelProvider provider = new SimulatedChannelProvider();

    private DeliveryTask taskFor(String recipientId) {
        return DeliveryTask.builder().recipientId(recipientId).channel(Channel.EMAIL).build();
    }

    @Test
    void invalidPrefixAlwaysFailsWithInvalidRecipient() {
        ChannelProvider.Result result = provider.send(taskFor("invalid-user-1"));
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(FailureReason.INVALID_RECIPIENT);
    }

    @Test
    void blockedPrefixAlwaysFailsWithAuthError() {
        ChannelProvider.Result result = provider.send(taskFor("blocked-user-1"));
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(FailureReason.AUTH_ERROR);
    }

    @Test
    void ratelimitPrefixAlwaysFailsWithRateLimited() {
        ChannelProvider.Result result = provider.send(taskFor("ratelimit-user-1"));
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(FailureReason.RATE_LIMITED);
    }

    @Test
    void flakyPrefixAlwaysFailsWithTransientProviderFailure() {
        for (int i = 0; i < 10; i++) {
            ChannelProvider.Result result = provider.send(taskFor("flaky-user-1"));
            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo(FailureReason.TRANSIENT_PROVIDER_FAILURE);
        }
    }

    @Test
    void normalRecipientOnlyEverSucceedsOrFailsTransiently() {
        for (int i = 0; i < 200; i++) {
            ChannelProvider.Result result = provider.send(taskFor("user-" + i));
            if (!result.success()) {
                assertThat(result.failureReason()).isEqualTo(FailureReason.TRANSIENT_PROVIDER_FAILURE);
            } else {
                assertThat(result.providerMessageId()).isNotBlank();
            }
        }
    }
}
