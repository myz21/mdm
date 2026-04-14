package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecuritySummaryDto {

    private long totalDevices;
    private long supervisedCount;
    private long activationLockCount;
    private long cloudBackupCount;
    private long findMyCount;
    private long jailbreakCount;
    private long vpnActiveCount;
    private long passcodeCompliantCount;
    private long appAnalyticsCount;
    private long diagnosticSubmissionCount;
}
