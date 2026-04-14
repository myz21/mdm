package com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMetadata {
    private String bundleId;
    private String version;
    private String buildVersion;
    private String displayName;
    private String minimumOsVersion;
    private String platform;              // "ios" or "macos"
    private List<String> supportedPlatforms;
    private String iconBase64;
}
