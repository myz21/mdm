package com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadRequest {
    private String bundleId;
    private String version;
    private String buildVersion;
    private String displayName;
    private String minimumOsVersion;
    private List<String> supportedPlatforms;
}
