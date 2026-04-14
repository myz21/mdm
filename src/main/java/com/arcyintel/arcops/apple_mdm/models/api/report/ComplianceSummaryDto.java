package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ComplianceSummaryDto {

    private long totalDevices;
    private long compliantCount;
    private long nonCompliantCount;
    private long noPolicyCount;
    private double complianceRate;
    private List<ComplianceFailureReasonDto> topFailureReasons;

    @Data
    @Builder
    public static class ComplianceFailureReasonDto {
        private String reason;
        private long count;
    }
}
