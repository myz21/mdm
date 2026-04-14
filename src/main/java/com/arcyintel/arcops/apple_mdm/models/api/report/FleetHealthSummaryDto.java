package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class FleetHealthSummaryDto {

    private long totalDevicesWithTelemetry;
    private double avgBatteryLevel;
    private long lowBatteryCount;
    private long criticalStorageCount;
    private long thermalWarningCount;
    private Map<String, Long> networkDistribution;
    private List<DistributionBucketDto> batteryDistribution;
    private List<DistributionBucketDto> storageDistribution;

    @Data
    @Builder
    public static class DistributionBucketDto {
        private String range;
        private long count;
    }
}
