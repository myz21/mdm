package com.arcyintel.arcops.apple_mdm.models.api.agent;

import lombok.Data;

@Data
public class AgentAuthRequest {
    private String username;
    private String password;
    private String deviceSerialNumber;
    private String agentVersion;
}
