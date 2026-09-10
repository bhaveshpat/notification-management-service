package com.meetbhavesh.notification.api.routing;

import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Default routing policy -- documented assumption: no real recipient
 * preference store exists in this prototype, so that input is not yet used.
 *
 *  - CRITICAL severity always includes EMAIL and SMS, regardless of what was
 *    requested (a critical alert should not depend on a single, possibly
 *    muted, channel).
 *  - Otherwise, use exactly the requested channels (deduplicated).
 *  - If nothing was requested, default to EMAIL.
 */
@Component
public class DefaultChannelRouter implements ChannelRouter {

    private static final Set<Channel> CRITICAL_MINIMUM = EnumSet.of(Channel.EMAIL, Channel.SMS);

    @Override
    public List<Channel> route(String recipientId, List<Channel> requestedChannels, Severity severity) {
        Set<Channel> resolved = new LinkedHashSet<>(requestedChannels == null ? List.of() : requestedChannels);

        if (severity == Severity.CRITICAL) {
            resolved.addAll(CRITICAL_MINIMUM);
        }
        if (resolved.isEmpty()) {
            resolved.add(Channel.EMAIL);
        }
        return List.copyOf(resolved);
    }
}
