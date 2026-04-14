package com.arcyintel.arcops.apple_mdm.models.api.device;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkInstallAppRequest {
    @NotEmpty(message = "UDID list cannot be empty")
    private List<String> udids;

    private Integer trackId;
    private String identifier;
}
