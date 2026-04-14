package com.arcyintel.arcops.apple_mdm.models.cert.abm;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class ProfileResponse {
    @JsonProperty("profile_uuid")
    private String profileUUID;

    @JsonProperty("devices")
    private Map<String, String> devices;
}
