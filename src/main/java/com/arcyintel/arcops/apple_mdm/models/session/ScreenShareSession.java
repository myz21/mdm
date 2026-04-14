package com.arcyintel.arcops.apple_mdm.models.session;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a screen-share signaling session stored in Redis.
 */
public record ScreenShareSession(
        String sessionId,
        String deviceUdid,
        String state,
        String createdAt,
        String updatedAt
) implements Serializable {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_WAKING = "WAKING";
    public static final String STATE_DEVICE_READY = "DEVICE_READY";
    public static final String STATE_CONNECTED = "CONNECTED";
    public static final String STATE_ENDED = "ENDED";

    public ScreenShareSession withState(String newState) {
        return new ScreenShareSession(sessionId, deviceUdid, newState, createdAt, Instant.now().toString());
    }
}
