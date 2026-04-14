package com.arcyintel.arcops.apple_mdm.configs.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "arcops-apple-mdm-server";
    private String username = "";
    private String password = "";
    private int defaultQos = 1;

    /**
     * Platform identifier for MQTT topic segmentation.
     * Used in topic structure: arcops/{platform}/devices/{id}/{subtopic}
     */
    private String platform = "apple";

    /** Unique instance ID appended to client ID for multi-instance support. */
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    /**
     * Returns a unique client ID for this instance to prevent MQTT client ID collisions.
     */
    public String getUniqueClientId() {
        return clientId + "-" + instanceId;
    }

    /**
     * External MQTT URL for iOS/macOS agents.
     * WSS example: wss://test.uconos.com/mqtt
     * TCP example: tcp://test.uconos.com:1883
     * When WSS, agents connect via WebSocket through Caddy reverse proxy.
     */
    private String agentBrokerUrl = "wss://localhost/mqtt";

    /**
     * Extracts the host from the broker URL (e.g. "tcp://localhost:1883" → "localhost").
     */
    public String getHost() {
        return extractHost(brokerUrl);
    }

    /**
     * Extracts the port from the broker URL (e.g. "tcp://localhost:1883" → 1883).
     */
    public int getPort() {
        return extractPort(brokerUrl);
    }

    /**
     * Agent-facing host extracted from agentBrokerUrl.
     */
    public String getAgentHost() {
        return extractHost(agentBrokerUrl);
    }

    /**
     * Agent-facing port extracted from agentBrokerUrl.
     */
    public int getAgentPort() {
        return extractPort(agentBrokerUrl);
    }

    /**
     * WebSocket path for agents (e.g. "wss://host/mqtt" → "/mqtt").
     * Returns null if not a WebSocket URL.
     */
    public String getAgentPath() {
        if (!isAgentWebSocket()) return null;
        String url = agentBrokerUrl;
        if (url.contains("://")) {
            url = url.substring(url.indexOf("://") + 3);
        }
        int slashIdx = url.indexOf('/');
        return slashIdx >= 0 ? url.substring(slashIdx) : "/";
    }

    /**
     * Whether the agent broker URL uses WebSocket (ws:// or wss://).
     */
    public boolean isAgentWebSocket() {
        return agentBrokerUrl.startsWith("wss://") || agentBrokerUrl.startsWith("ws://");
    }

    /**
     * Whether the agent broker URL uses SSL/TLS (wss:// or ssl://).
     */
    public boolean isAgentSsl() {
        return agentBrokerUrl.startsWith("wss://") || agentBrokerUrl.startsWith("ssl://");
    }

    private String extractHost(String url) {
        String u = url;
        if (u.contains("://")) {
            u = u.substring(u.indexOf("://") + 3);
        }
        // Remove path
        int slashIdx = u.indexOf('/');
        if (slashIdx >= 0) u = u.substring(0, slashIdx);
        // Remove port
        if (u.contains(":")) {
            u = u.substring(0, u.indexOf(":"));
        }
        return u;
    }

    private int extractPort(String url) {
        String u = url;
        if (u.contains("://")) {
            u = u.substring(u.indexOf("://") + 3);
        }
        // Remove path
        int slashIdx = u.indexOf('/');
        if (slashIdx >= 0) u = u.substring(0, slashIdx);
        if (u.contains(":")) {
            try {
                return Integer.parseInt(u.substring(u.indexOf(":") + 1));
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        // Default ports based on scheme
        if (url.startsWith("wss://") || url.startsWith("ssl://")) return 443;
        if (url.startsWith("ws://")) return 80;
        return 1883;
    }
}
