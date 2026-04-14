package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ComplianceDeviceDto {

    private String deviceId;
    private String serialNumber;
    private String productName;
    private String platform;
    private String osVersion;
    private String enrollmentType;
    private boolean isCompliant;
    private List<ComplianceFailureItemDto> complianceFailures;
    private String appliedPolicyName;
    private Instant lastModifiedDate;

    @Data
    @Builder
    public static class ComplianceFailureItemDto {
        private String commandType;
        private String failureReason;
    }
}
