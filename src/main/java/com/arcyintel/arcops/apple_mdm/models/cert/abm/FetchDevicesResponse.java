package com.arcyintel.arcops.apple_mdm.models.cert.abm;

import lombok.Data;

import java.util.List;

@Data
public class FetchDevicesResponse {
    private List<Device> devices;
    private String cursor;
    private String fetchedUntil;
    private Boolean moreToFollow;
}