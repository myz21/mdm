package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SecurityDeviceRequestDto {

    private String platform;
    private String filter;
    private int page = 0;
    private int size = 25;
    private String sortBy = "productName";
    private boolean sortDesc = false;
}
