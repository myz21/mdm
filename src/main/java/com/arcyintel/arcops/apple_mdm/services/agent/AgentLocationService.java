package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.domains.AgentLocation;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.repositories.AgentLocationRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.utils.JsonNodeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.arcyintel.arcops.apple_mdm.utils.JsonNodeUtils.*;

/**
 * Handles location messages received from devices via MQTT.
 * Parses the JSON payload and persists it to the agent_location table.
 *
 * Topic: arcops/{platform}/devices/{deviceIdentifier}/location
 */
@Service
@RequiredArgsConstructor
public class AgentLocationService {

    private static final Logger logger = LoggerFactory.getLogger(AgentLocationService.class);

    private final ObjectMapper objectMapper;
    private final AppleDeviceRepository appleDeviceRepository;
    private final AgentLocationRepository locationRepository;

    @Transactional
    public void handleLocationMessage(String deviceIdentifier, String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            Instant now = Instant.now();

            // Parse deviceCreatedAt from payload, fallback to server time
            Instant deviceCreatedAt = parseInstant(json, "deviceCreatedAt", now);

            AppleDevice device = appleDeviceRepository.findByUdid(deviceIdentifier).orElse(null);

            Double latitude = json.path("latitude").asDouble();
            Double longitude = json.path("longitude").asDouble();

            if (latitude == 0.0 && longitude == 0.0) {
                logger.warn("Ignoring zero-coordinate location from device {}", deviceIdentifier);
                return;
            }

            AgentLocation location = AgentLocation.builder()
                    .device(device)
                    .deviceIdentifier(deviceIdentifier)
                    .deviceCreatedAt(deviceCreatedAt)
                    .serverReceivedAt(now)
                    .latitude(latitude)
                    .longitude(longitude)
                    .altitude(doubleOrNull(json, "altitude"))
                    .horizontalAccuracy(doubleOrNull(json, "horizontalAccuracy"))
                    .verticalAccuracy(doubleOrNull(json, "verticalAccuracy"))
                    .speed(doubleOrNull(json, "speed"))
                    .course(doubleOrNull(json, "course"))
                    .floorLevel(intOrNull(json, "floor"))
                    .build();

            locationRepository.save(location);

            // Update device last seen
            if (device != null) {
                device.setAgentLastSeenAt(now);
                appleDeviceRepository.save(device);
            }

            logger.debug("Location saved — device={}, lat={}, lon={}, deviceCreatedAt={}",
                    deviceIdentifier, latitude, longitude, deviceCreatedAt);
        } catch (Exception e) {
            logger.error("Failed to process location from device {}: {}", deviceIdentifier, e.getMessage());
        }
    }

}
