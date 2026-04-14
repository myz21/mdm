package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommandReportSummaryDto {

    private long totalCommands;
    private long completedCommands;
    private long failedCommands;
    private long pendingCommands;
    private long executingCommands;
    private long canceledCommands;
    private Double avgExecutionTimeMs;
    private Double successRate;
}
