package com.arcyintel.arcops.apple_mdm.models.api.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbmDeviceSummaryDto {
    private long totalCount;
    private long pushedCount;
    private long assignedCount;
    private long emptyCount;
    private List<RecentDevice> recentDevices;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentDevice {
        private String serialNumber;
        private String model;
        private String description;
        private String color;
        private String os;
        private String deviceFamily;
        private String profileStatus;
        private String deviceAssignedDate;
    }
}
