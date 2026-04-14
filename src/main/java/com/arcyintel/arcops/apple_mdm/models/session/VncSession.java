package com.arcyintel.arcops.apple_mdm.models.session;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a VNC remote desktop session stored in Redis.
 */
public record VncSession(
        String sessionId,
        String deviceUdid,
        String state,
        String createdAt,
        String updatedAt
) implements Serializable {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_WAKING = "WAKING";
    public static final String STATE_AGENT_CONNECTED = "AGENT_CONNECTED";
    public static final String STATE_VIEWER_CONNECTED = "VIEWER_CONNECTED";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_ENDED = "ENDED";

    public VncSession withState(String newState) {
        return new VncSession(sessionId, deviceUdid, newState, createdAt, Instant.now().toString());
    }
}
