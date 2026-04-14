package com.arcyintel.arcops.apple_mdm.models.cert.abm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ClearProfileRequest {
    @JsonProperty("devices")
    private List<String> devices;

    @JsonProperty("profile_uuid")
    private String profileUuid;

    @JsonProperty("reassign_profile_uuid")
    private String reassignProfileUuid;
}