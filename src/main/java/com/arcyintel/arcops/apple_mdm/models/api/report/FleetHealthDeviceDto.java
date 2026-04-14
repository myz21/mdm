package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FleetHealthDeviceDto {

    private String deviceId;
    private String serialNumber;
    private String productName;
    private String platform;
    private int batteryLevel;
    private boolean batteryCharging;
    private String batteryState;
    private long storageTotalBytes;
    private long storageUsedBytes;
    private int storageUsagePercent;
    private String thermalState;
    private String networkType;
    private String wifiSsid;
    private boolean vpnActive;
    private Instant agentLastSeenAt;
}