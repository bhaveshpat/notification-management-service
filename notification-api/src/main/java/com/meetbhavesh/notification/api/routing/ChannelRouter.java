package com.meetbhavesh.notification.api.routing;

import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.Severity;

import java.util.List;

/**
 * Decides the actual delivery channel(s) for one recipient, given what was
 * requested and the notification's severity (4.3: "requested channel /
 * notification severity / recipient preferences / routing policy"). Kept
 * behind an interface so the policy can be swapped or extended (e.g. real
 * per-recipient preferences, a rules engine) without touching the submission
 * flow. See docs/ARCHITECTURE.md.
 */
public interface ChannelRouter {
    List<Channel> route(String recipientId, List<Channel> requestedChannels, Severity severity);
}
