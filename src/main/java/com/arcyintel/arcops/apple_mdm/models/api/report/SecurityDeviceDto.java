package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecurityDeviceDto {

    private String deviceId;
    private String serialNumber;
    private String productName;
    private String platform;
    private String osVersion;
    private boolean supervised;
    private boolean activationLockEnabled;
    private boolean cloudBackupEnabled;
    private boolean findMyEnabled;
    private boolean jailbreakDetected;
    private boolean vpnActive;
    private boolean passcodeCompliant;
}
