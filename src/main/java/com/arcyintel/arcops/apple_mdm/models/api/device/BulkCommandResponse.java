package com.arcyintel.arcops.apple_mdm.models.api.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCommandResponse {
    private UUID bulkCommandId;
    private int total;
    private int success;
    private int failed;
    @Builder.Default
    private List<FailureDetail> failures = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureDetail {
        private String udid;
        private String reason;
    }
}
