package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "device_auth_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceAuthHistory extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private AppleDevice device;

    @Column(name = "device_identifier", nullable = false)
    private String deviceIdentifier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_id")
    private AppleIdentity identity;

    @Column(name = "username")
    private String username;

    @Column(name = "auth_source", nullable = false)
    private String authSource;  // AGENT | SETUP

    @Column(name = "event_type", nullable = false)
    private String eventType;  // SIGN_IN | SIGN_OUT

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "agent_version")
    private String agentVersion;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
