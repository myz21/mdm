package com.arcyintel.arcops.apple_mdm.domains;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "apple_device_location")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppleDeviceLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    @JsonBackReference
    private AppleDevice appleDevice;

    private Double latitude;
    private Double longitude;

    // Altitude in meters
    private Double altitude;

    // Speed in meters/second
    private Double speed;

    // Course (direction) in degrees
    private Double course;

    private Double horizontalAccuracy;
    private Double verticalAccuracy;

    // The timestamp reported by the device (when the location was actually fixed)
    private Instant timestamp;

    // When we received/saved the record
    @Column(nullable = false, updatable = false)
    private Instant createdDate;

    @PrePersist
    protected void onCreate() {
        this.createdDate = Instant.now();
    }
}