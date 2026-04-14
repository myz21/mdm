package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.configs.mqtt.MqttProperties;
import com.arcyintel.arcops.apple_mdm.domains.AgentCommand;
import com.arcyintel.arcops.apple_mdm.repositories.AgentCommandRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes commands to devices via MQTT and tracks them in the database.
 *
 * Topic: arcops/{platform}/devices/{udid}/commands
 */
@Service
@RequiredArgsConstructor
public class AgentCommandService {

    private static final Logger logger = LoggerFactory.getLogger(AgentCommandService.class);

    private final MessageChannel mqttOutputChannel;
    private final ObjectMapper objectMapper;
    private final MqttProperties mqttProperties;
    private final AgentCommandRepository agentCommandRepository;

    /**
     * Send a command to a specific device, persisting the command record in the database.
     *
     * @param deviceUdid  the target device UDID
     * @param commandType the command type (e.g. "request_location", "update_config")
     * @param payload     the command payload
     * @return the saved AgentCommand entity
     */
    public AgentCommand sendCommand(String deviceUdid, String commandType, Map<String, Object> payload) {
        String commandUuid = UUID.randomUUID().toString();

        // 1. Create and persist the command with PENDING status
        AgentCommand agentCommand = AgentCommand.builder()
                .commandUuid(commandUuid)
                .deviceIdentifier(deviceUdid)
                .commandType(commandType)
                .status("PENDING")
                .payload(payload)
                .build();
        agentCommandRepository.save(agentCommand);

        // 2. Publish via MQTT
        String topic = "arcops/" + mqttProperties.getPlatform() + "/devices/" + deviceUdid + "/commands";
        try {
            Map<String, Object> mqttPayload = Map.of(
                    "commandId", commandUuid,
                    "type", commandType,
                    "payload", payload != null ? payload : Map.of(),
                    "timestamp", Instant.now().toString()
            );

            String json = objectMapper.writeValueAsString(mqttPayload);

            mqttOutputChannel.send(
                    MessageBuilder.withPayload(json)
                            .setHeader(MqttHeaders.TOPIC, topic)
                            .setHeader(MqttHeaders.QOS, 2)
                            .build()
            );

            // 3. On success, update status to SENT
            agentCommand.setStatus("SENT");
            agentCommandRepository.save(agentCommand);
            logger.info("Command sent — uuid={}, type={}, device={}", commandUuid, commandType, deviceUdid);
        } catch (Exception e) {
            // 4. On failure, update status to FAILED with error
            agentCommand.setStatus("FAILED");
            agentCommand.setErrorMessage("MQTT publish failed: " + e.getMessage());
            agentCommandRepository.save(agentCommand);
            logger.error("Failed to send command to device {}: {}", deviceUdid, e.getMessage());
        }

        return agentCommand;
    }
}
