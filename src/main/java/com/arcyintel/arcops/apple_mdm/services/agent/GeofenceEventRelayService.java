package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.commons.events.geofence.GeofenceTriggeredEvent;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.events.GeofenceEvents.*;

@Service
@RequiredArgsConstructor
public class GeofenceEventRelayService {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceEventRelayService.class);

    private final RabbitTemplate rabbitTemplate;
    private final AppleDeviceRepository appleDeviceRepository;

    public void relayGeofenceEvent(String deviceIdentifier, JsonNode eventJson) {
        try {
            var deviceOpt = appleDeviceRepository.findByUdid(deviceIdentifier);
            if (deviceOpt.isEmpty()) {
                logger.warn("Cannot relay geofence event — device not found: {}", deviceIdentifier);
                return;
            }

            var device = deviceOpt.get();
            UUID locationId;
            try {
                locationId = UUID.fromString(eventJson.get("locationId").asText());
            } catch (Exception e) {
                logger.warn("Invalid locationId in geofence event from device {}: {}", deviceIdentifier, e.getMessage());
                return;
            }

            String eventType = eventJson.has("eventType") ? eventJson.get("eventType").asText() : "UNKNOWN";
            double latitude = eventJson.has("latitude") ? eventJson.get("latitude").asDouble() : 0.0;
            double longitude = eventJson.has("longitude") ? eventJson.get("longitude").asDouble() : 0.0;

            Instant timestamp;
            try {
                timestamp = eventJson.has("timestamp")
                        ? Instant.parse(eventJson.get("timestamp").asText())
                        : Instant.now();
            } catch (Exception e) {
                logger.warn("Invalid timestamp in geofence event from device {}, using current time: {}",
                        deviceIdentifier, e.getMessage());
                timestamp = Instant.now();
            }

            String normalizedType = normalizeEventType(eventType);

            GeofenceTriggeredEvent event = new GeofenceTriggeredEvent(
                    device.getId(),
                    deviceIdentifier,
                    locationId,
                    normalizedType,
                    latitude,
                    longitude,
                    timestamp
            );

            rabbitTemplate.convertAndSend(GEOFENCE_EVENT_EXCHANGE, GEOFENCE_TRIGGERED_ROUTE_KEY, event);
            logger.info("Geofence event relayed — device: {}, location: {}, type: {}",
                    deviceIdentifier, locationId, normalizedType);
        } catch (Exception e) {
            logger.error("Failed to relay geofence event from device {}: {}",
                    deviceIdentifier, e.getMessage(), e);
        }
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null) return "UNKNOWN";
        return switch (eventType.toLowerCase()) {
            case "geofence_enter", "enter" -> "ENTER";
            case "geofence_exit", "exit" -> "EXIT";
            default -> eventType.toUpperCase();
        };
    }
}
