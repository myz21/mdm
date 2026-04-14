package com.arcyintel.arcops.apple_mdm.models.api.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto implements Serializable {

    private List<TopManagedAppDto> topManagedApps;
    private CommandStatsDto commandStats;
    private DeviceStatsDto deviceStats;
    private List<OsVersionCountDto> osVersions;
    private List<ModelCountDto> modelDistribution;

    // New dashboard data
    private FleetTelemetryDto fleetTelemetry;
    private OnlineStatusDto onlineStatus;
    private List<EnrollmentTypeCountDto> enrollmentTypeDistribution;
    private SecurityPostureDto securityPosture;
    private List<RecentCommandDto> recentCommands;
    private List<DeviceLocationPointDto> deviceLocations;
    private CommandTrendDto commandTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopManagedAppDto implements Serializable {
        private String bundleId;
        private String name;
        private long installCount;
        private String iconUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandStatsDto implements Serializable {
        private long total;
        private long completed;
        private long failed;
        private long pending;
        private long executing;
        private long canceled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceStatsDto implements Serializable {
        private long totalDevices;
        private long compliantCount;
        private long nonCompliantCount;
        private long supervisedCount;
        private long awaitingConfigurationCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsVersionCountDto implements Serializable {
        private String version;
        private long count;
        private String platform;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelCountDto implements Serializable {
        private String modelName;
        private long count;
    }

    // ──── New DTOs for enhanced dashboard ────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FleetTelemetryDto implements Serializable {
        private double avgBatteryLevel;
        private long lowBatteryCount;
        private long criticalBatteryCount;
        private double avgStorageUsagePercent;
        private long storageWarningCount;
        private long storageCriticalCount;
        private List<NetworkTypeCountDto> networkTypeDistribution;
        private long vpnActiveCount;
        private long lowPowerModeCount;
        private List<ThermalStateCountDto> thermalStateDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkTypeCountDto implements Serializable {
        private String networkType;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThermalStateCountDto implements Serializable {
        private String thermalState;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnlineStatusDto implements Serializable {
        private long onlineCount;
        private long offlineCount;
        private long neverSeenCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrollmentTypeCountDto implements Serializable {
        private String enrollmentType;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityPostureDto implements Serializable {
        private long totalDevices;
        private double supervisedPercent;
        private long activationLockCount;
        private long cloudBackupEnabledCount;
        private long jailbreakDetectedCount;
        private long debuggerAttachedCount;
        private double compliantPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentCommandDto implements Serializable {
        private String id;
        private String commandType;
        private String status;
        private String deviceName;
        private String deviceUdid;
        private Instant requestTime;
        private Instant completionTime;
        private String failureReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceLocationPointDto implements Serializable {
        private double latitude;
        private double longitude;
        private String deviceName;
        private String deviceIdentifier;
        private boolean agentOnline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandTrendDto implements Serializable {
        private List<DailyCommandCountDto> dailyCounts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCommandCountDto implements Serializable {
        private String date;
        private long completed;
        private long failed;
        private long total;
    }

    // ──── New DTOs for dashboard charts ────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelCountDto implements Serializable {
        private String label;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandTypeCountDto implements Serializable {
        private String commandType;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandSuccessRateDto implements Serializable {
        private String commandType;
        private long completed;
        private long failed;
        private long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandAnalyticsDto implements Serializable {
        private List<CommandTypeCountDto> commandTypeDistribution;
        private List<CommandSuccessRateDto> commandSuccessRates;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TelemetryAnalyticsDto implements Serializable {
        private List<LabelCountDto> batteryHistogram;
        private List<LabelCountDto> batteryStateDistribution;
        private List<LabelCountDto> topWifiNetworks;
        private List<LabelCountDto> carrierDistribution;
        private List<LabelCountDto> radioTechDistribution;
        private List<LabelCountDto> languageDistribution;
        private List<LabelCountDto> timezoneDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageTierCountDto implements Serializable {
        private String tier;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureEnablementDto implements Serializable {
        private String featureName;
        private long enabledCount;
        private long disabledCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceFeatureEnablementDto implements Serializable {
        private List<FeatureEnablementDto> features;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyEnrollmentCountDto implements Serializable {
        private String date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrollmentTrendDto implements Serializable {
        private List<DailyEnrollmentCountDto> dailyCounts;
    }
}
