package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Tracks device state transitions (ONLINE ↔ OFFLINE).
 * Each row is one transition event, not a session.
 *
 * - ONLINE event:  device went from offline → online.  durationSeconds = how long it was offline.
 * - OFFLINE event: device went from online → offline.  durationSeconds = how long it was online.
 *
 * To query online/offline durations for a device over a time range,
 * select events ordered by timestamp and use durationSeconds.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "agent_presence_history")
public class AgentPresenceHistory extends AbstractEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private AppleDevice device;

    @Column(name = "device_identifier", nullable = false)
    private String deviceIdentifier;

    /** Event type: ONLINE or OFFLINE */
    @Column(name = "event_type", nullable = false, length = 10)
    private String eventType;

    /** When this transition happened */
    @Column(name = "connected_at", nullable = false)
    private Instant timestamp;

    /** Duration of the previous state in seconds (offline duration for ONLINE events, online duration for OFFLINE events) */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "agent_version", length = 50)
    private String agentVersion;

    @Column(name = "agent_platform", length = 20)
    private String agentPlatform;

    /** Reason for OFFLINE events: graceful, heartbeat_timeout, server_restart */
    @Column(name = "disconnect_reason", length = 100)
    private String reason;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
