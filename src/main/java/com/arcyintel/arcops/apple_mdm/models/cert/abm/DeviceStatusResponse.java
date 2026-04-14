package com.arcyintel.arcops.apple_mdm.models.cert.abm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeviceStatusResponse {
    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("tokenExpirationDate")
    private String tokenExpirationDate;

    @JsonProperty("uId")
    private String uId;
}