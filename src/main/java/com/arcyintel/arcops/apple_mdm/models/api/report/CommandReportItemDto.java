package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CommandReportItemDto {

    private UUID id;
    private String commandType;
    private String status;
    private UUID deviceId;
    private String serialNumber;
    private String productName;
    private String platform;
    private Instant requestTime;
    private Instant executionTime;
    private Instant completionTime;
    private Long executionDurationMs;
    private String failureReason;
    private UUID policyId;
    private String policyName;
    private UUID bulkCommandId;
    private String createdBy;
}
