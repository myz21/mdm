package com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppAnalyzeResponse {
    private String tempId;
    private String fileName;
    private long fileSizeBytes;
    private String fileHash;
    private AppMetadata metadata;
}
