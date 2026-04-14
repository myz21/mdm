package com.arcyintel.arcops.apple_mdm.models.session;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a remote terminal session stored in Redis.
 */
public record RemoteTerminalSession(
        String sessionId,
        String deviceUdid,
        String state,
        String createdAt,
        String updatedAt
) implements Serializable {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_ENDED = "ENDED";

    public RemoteTerminalSession withState(String newState) {
        return new RemoteTerminalSession(sessionId, deviceUdid, newState, createdAt, Instant.now().toString());
    }
}
