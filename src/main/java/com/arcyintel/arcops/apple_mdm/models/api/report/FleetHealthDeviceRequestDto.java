package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FleetHealthDeviceRequestDto {

    private String platform;
    private int page = 0;
    private int size = 25;
    private String sortBy = "batteryLevel";
    private boolean sortDesc = false;
}