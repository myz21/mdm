package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionDto {
    private String version;
    private String shortVersion;
    private long deviceCount;
}
