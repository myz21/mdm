package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppDeviceReportRequestDto {
    private String version;
    private String platform;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 25;
    private String sortBy;
    @Builder.Default
    private boolean sortDesc = true;
}
