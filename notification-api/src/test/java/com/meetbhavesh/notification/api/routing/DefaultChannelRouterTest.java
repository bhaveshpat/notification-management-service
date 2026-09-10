package com.meetbhavesh.notification.api.routing;

import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultChannelRouterTest {

    private final DefaultChannelRouter router = new DefaultChannelRouter();

    @Test
    void criticalSeverityAlwaysAddsEmailAndSms() {
        List<Channel> result = router.route("user-1", List.of(Channel.PUSH), Severity.CRITICAL);

        assertThat(result).containsExactlyInAnyOrder(Channel.PUSH, Channel.EMAIL, Channel.SMS);
    }

    @Test
    void nonCriticalSeverityUsesRequestedChannelsAsIs() {
        List<Channel> result = router.route("user-1", List.of(Channel.PUSH, Channel.WEBHOOK), Severity.MEDIUM);

        assertThat(result).containsExactlyInAnyOrder(Channel.PUSH, Channel.WEBHOOK);
    }

    @Test
    void deduplicatesRequestedChannels() {
        List<Channel> result = router.route("user-1", List.of(Channel.EMAIL, Channel.EMAIL), Severity.LOW);

        assertThat(result).containsExactly(Channel.EMAIL);
    }

    @Test
    void defaultsToEmailWhenNothingRequestedAndNotCritical() {
        List<Channel> result = router.route("user-1", List.of(), Severity.LOW);

        assertThat(result).containsExactly(Channel.EMAIL);
    }

    @Test
    void criticalWithNoRequestedChannelsStillGetsEmailAndSms() {
        List<Channel> result = router.route("user-1", List.of(), Severity.CRITICAL);

        assertThat(result).containsExactlyInAnyOrder(Channel.EMAIL, Channel.SMS);
    }
}
