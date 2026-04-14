package com.arcyintel.arcops.apple_mdm.models.api.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationResponse {
    private int total;
    private int sent;
    private int queued;
    private int failed;
    private List<BulkCommandResponse.FailureDetail> failures;
}
