package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommandTypeBreakdownDto {

    private String commandType;
    private long total;
    private long completed;
    private long failed;
    private long canceled;
    private long pending;
    private Double avgExecutionTimeMs;
}
