package com.arcyintel.arcops.apple_mdm.services.mqtt;

import com.arcyintel.arcops.apple_mdm.repositories.AgentCommandRepository;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentLocationService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentPresenceService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentTelemetryService;
import com.arcyintel.arcops.apple_mdm.services.agent.GeofenceEventRelayService;
import com.arcyintel.arcops.apple_mdm.services.screenshare.ScreenShareSignalingHandler;
import com.arcyintel.arcops.apple_mdm.services.terminal.RemoteTerminalHandler;
import com.arcyintel.arcops.apple_mdm.services.vnc.VncSessionService;
import com.arcyintel.arcops.apple_mdm.models.session.VncSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.*;

/**
 * Routes incoming MQTT messages to the appropriate service based on the topic.
 *
 * Topic structure: arcops/{platform}/devices/{udid}/{subtopic}
 */
@Component
@RequiredArgsConstructor
public class MqttMessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(MqttMessageRouter.class);

    private final AgentPresenceService agentPresenceService;
    private final AgentTelemetryService agentTelemetryService;
    private final AgentLocationService agentLocationService;
    private final ScreenShareSignalingHandler screenShareSignalingHandler;
    private final RemoteTerminalHandler remoteTerminalHandler;
    private final VncSessionService vncSessionService;
    private final GeofenceEventRelayService geofenceEventRelayService;
    private final AgentCommandRepository agentCommandRepository;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void route(Message<?> message) {
        MessageHeaders headers = message.getHeaders();
        String topic = (String) headers.get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();

        if (topic == null) {
            logger.warn("Received MQTT message with no topic header");
            return;
        }

        // Parse device identifier from topic: arcops/{platform}/devices/{udid}/{subtopic}
        String[] parts = topic.split("/");
        if (parts.length < 5 || !"arcops".equals(parts[0]) || !"devices".equals(parts[2])) {
            logger.warn("Unexpected MQTT topic format: {}", topic);
            return;
        }

        String deviceUdid = parts[3];
        String subtopic = parts[4];

        switch (subtopic) {
            case MQTT_STATUS -> agentPresenceService.handleStatusMessage(deviceUdid, payload);
            case MQTT_TELEMETRY -> agentTelemetryService.handleTelemetryMessage(deviceUdid, payload);
            case MQTT_LOCATION -> agentLocationService.handleLocationMessage(deviceUdid, payload);
            case MQTT_EVENTS -> {
                logger.info("Event from device {}: {}", deviceUdid, payload);
                try {
                    com.fasterxml.jackson.databind.JsonNode eventJson = objectMapper.readTree(payload);
                    String eventType = eventJson.has("eventType") ? eventJson.get("eventType").asText() : null;
                    if (eventType != null && (eventType.startsWith("geofence_") || "ENTER".equals(eventType) || "EXIT".equals(eventType))) {
                        geofenceEventRelayService.relayGeofenceEvent(deviceUdid, eventJson);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse event from device {}: {}", deviceUdid, e.getMessage());
                }
            }
            case MQTT_RESPONSES -> handleCommandResponse(deviceUdid, payload);
            default -> logger.warn("Unknown subtopic '{}' from device {}", subtopic, deviceUdid);
        }
    }

    /**
     * Handles command responses from agents. Routes special-purpose responses (screen share,
     * terminal, VNC) to their handlers first, then correlates the response with the tracked
     * AgentCommand record in the database.
     */
    private void handleCommandResponse(String deviceUdid, String payload) {
        Map<String, Object> resp = null;
        try {
            resp = objectMapper.readValue(payload, new TypeReference<>() {});
            Object data = resp.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                String type = (String) dataMap.get("type");
                // Screen share responses
                if (type != null && (type.startsWith("webrtc_") || type.equals("screen_share_ready") || type.equals("screen_share_stopped"))) {
                    screenShareSignalingHandler.handleDeviceResponse(deviceUdid, resp);
                    correlateCommandResponse(resp);
                    return;
                }
                // Terminal responses
                if (type != null && (type.equals("terminal_output") || type.equals("terminal_started") || type.equals("terminal_ended"))) {
                    remoteTerminalHandler.handleDeviceResponse(deviceUdid, resp);
                    correlateCommandResponse(resp);
                    return;
                }
                // VNC tunnel responses
                if (type != null && type.startsWith("vnc_tunnel_")) {
                    handleVncResponse(type, dataMap);
                    correlateCommandResponse(resp);
                    return;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse command response from device {}: {}", deviceUdid, e.getMessage());
        }

        // Correlate with DB for general command responses
        if (resp != null) {
            correlateCommandResponse(resp);
        }

        logger.info("Command response from device {}: {}", deviceUdid, payload);
    }

    /**
     * Correlates a command response with the tracked AgentCommand record in the database.
     * Updates the command status to COMPLETED or FAILED based on the response.
     */
    private void correlateCommandResponse(Map<String, Object> resp) {
        String commandUuid = (String) resp.get("commandId");
        if (commandUuid == null) {
            return;
        }

        agentCommandRepository.findByCommandUuid(commandUuid).ifPresent(command -> {
            String status = (String) resp.get("status");
            Object resultData = resp.get("data");

            if ("SUCCESS".equalsIgnoreCase(status)) {
                command.setStatus("COMPLETED");
                if (resultData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) resultData;
                    command.setResult(resultMap);
                }
            } else {
                command.setStatus("FAILED");
                String error = (String) resp.get("error");
                command.setErrorMessage(error != null ? error : "Command failed (no error details from device)");
            }

            command.setResponseTime(Instant.now());
            agentCommandRepository.save(command);
            logger.debug("Agent command correlated — uuid={}, status={}", commandUuid, command.getStatus());
        });
    }

    private void handleVncResponse(String type, Map<?, ?> dataMap) {
        String sessionId = (String) dataMap.get("sessionId");
        if (sessionId == null) {
            logger.warn("VNC response missing sessionId — type={}", type);
            return;
        }

        switch (type) {
            case "vnc_tunnel_ready" -> {
                vncSessionService.updateState(sessionId, VncSession.STATE_AGENT_CONNECTED);
                logger.info("VNC tunnel ready — sessionId={}", sessionId);
            }
            case "vnc_tunnel_error" -> {
                String error = dataMap.get("error") != null ? dataMap.get("error").toString() : "unknown";
                logger.error("VNC tunnel error — sessionId={}, error={}", sessionId, error);
                vncSessionService.endSession(sessionId);
            }
            case "vnc_tunnel_stopped" -> {
                logger.info("VNC tunnel stopped — sessionId={}", sessionId);
                vncSessionService.endSession(sessionId);
            }
            default -> logger.warn("Unknown VNC response type: {}", type);
        }
    }
}
