package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.Auditable;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "enterprise_app",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bundle_id", "version"}))
public class EnterpriseApp extends Auditable<String> {

    @Column(name = "bundle_id", nullable = false, length = 512)
    private String bundleId;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "build_version", length = 64)
    private String buildVersion;

    @Column(name = "display_name", length = 512)
    private String displayName;

    @Column(name = "minimum_os_version", length = 32)
    private String minimumOsVersion;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "icon_base64", columnDefinition = "TEXT")
    private String iconBase64;

    @ElementCollection
    @CollectionTable(
            name = "enterprise_app_supported_platforms",
            joinColumns = @JoinColumn(name = "enterprise_app_id"))
    @Column(name = "platform")
    private List<String> supportedPlatforms = new ArrayList<>();
}
