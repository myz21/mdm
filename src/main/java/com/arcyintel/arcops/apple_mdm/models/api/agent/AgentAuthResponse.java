package com.arcyintel.arcops.apple_mdm.models.api.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentAuthResponse {

    private String token;
    private AgentUserDto user;
    private AgentMqttConfig mqtt;
    private String deviceUdid;

    @Data
    @Builder
    public static class AgentUserDto {
        private String id;
        private String username;
        private String email;
        private String fullName;
    }

    @Data
    @Builder
    public static class AgentMqttConfig {
        private String host;
        private int port;
        private String username;
        private String password;
        private String clientId;
        private boolean websocket;
        private String path;
        private boolean ssl;
        /** Topic prefix for this device, e.g. "arcops/apple/devices/{udid}" */
        private String topicPrefix;
    }
}
