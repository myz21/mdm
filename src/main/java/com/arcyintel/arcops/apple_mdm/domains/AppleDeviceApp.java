package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "apple_device_apps")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AppleDeviceApp extends AbstractEntity {

    @Column(name = "bundle_size")
    private Integer bundleSize;

    // Unique identifier for the application (e.g., com.whatsapp, com.apple.calculator)
    @Column(name = "bundle_identifier", nullable = false)
    private String bundleIdentifier;

    @Column(name = "installing")
    private boolean installing;

    @Column(name = "name")
    private String name;

    @Column(name = "version")
    private String version;

    @Column(name = "short_version")
    private String shortVersion;

    @Column(name = "is_managed")
    private boolean managed;

    @Column(name = "has_configuration")
    private boolean hasConfiguration;

    @Column(name = "has_feedback")
    private boolean hasFeedback;

    @Column(name = "is_validated")
    private boolean validated;

    @Column(name = "management_flags")
    private int managementFlags;

    // Relationship with Device (Which device is this app installed on?)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private AppleDevice appleDevice;
}