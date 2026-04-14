package com.arcyintel.arcops.apple_mdm.models.api.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkNotificationRequest {
    @NotEmpty(message = "UDID list cannot be empty")
    private List<String> udids;

    @NotBlank(message = "Title is required")
    private String title;

    private String body;
    private String category;
}
