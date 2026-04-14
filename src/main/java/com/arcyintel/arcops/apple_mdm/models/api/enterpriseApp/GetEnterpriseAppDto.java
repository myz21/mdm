package com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetEnterpriseAppDto {
    private UUID id;
    private String bundleId;
    private String version;
    private String buildVersion;
    private String displayName;
    private String minimumOsVersion;
    private Long fileSizeBytes;
    private String fileName;
    private String platform;
    private String fileHash;
    private String downloadUrl;
    private String iconBase64;
    private List<String> supportedPlatforms;
    private Date creationDate;
    private Date lastModifiedDate;
}
