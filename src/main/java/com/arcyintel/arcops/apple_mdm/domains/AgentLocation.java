package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Stores a single location point received from a device agent via MQTT.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "agent_location")
public class AgentLocation extends AbstractEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private AppleDevice device;

    @Column(name = "device_identifier", nullable = false)
    private String deviceIdentifier;

    @Column(name = "device_created_at", nullable = false)
    private Instant deviceCreatedAt;

    @Column(name = "server_received_at", nullable = false)
    @Builder.Default
    private Instant serverReceivedAt = Instant.now();

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "altitude")
    private Double altitude;

    @Column(name = "horizontal_accuracy")
    private Double horizontalAccuracy;

    @Column(name = "vertical_accuracy")
    private Double verticalAccuracy;

    @Column(name = "speed")
    private Double speed;

    @Column(name = "course")
    private Double course;

    @Column(name = "floor_level")
    private Integer floorLevel;

    /** AGENT = from iOS agent MQTT, MDM_LOST_MODE = from DeviceLocation command response */
    @Column(name = "source", nullable = false)
    @Builder.Default
    private String source = "AGENT";
}
