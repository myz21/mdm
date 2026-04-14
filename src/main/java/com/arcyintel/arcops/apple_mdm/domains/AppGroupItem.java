package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "app_group_item")
public class AppGroupItem extends AbstractEntity {

    // ... existing code ...
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonBackReference
    private AppGroup appGroup;
// ... existing code ...

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false, length = 32)
    private AppGroup.AppType appType;

    // VPP fields
    @Column(name = "track_id", length = 64)
    private String trackId;

    @Column(name = "bundle_id", length = 512)
    private String bundleId;

    // Enterprise fields (kept nullable for now; safe to start with VPP-only)
    @Column(name = "artifact_ref", length = 1024)
    private String artifactRef;

    @Column(name = "display_name", length = 512)
    private String displayName;

    @Transient
    private java.util.List<String> supportedPlatforms;

    @Transient
    private String iconUrl;

}
