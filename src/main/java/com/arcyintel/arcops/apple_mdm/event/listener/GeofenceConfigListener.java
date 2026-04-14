package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.commons.events.geofence.GeofenceConfigApplyEvent;
import com.arcyintel.arcops.commons.events.geofence.GeofenceLocationPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.arcyintel.arcops.commons.constants.events.GeofenceEvents.*;

@Component
@RequiredArgsConstructor
public class GeofenceConfigListener {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceConfigListener.class);

    private final AgentCommandService agentCommandService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = GEOFENCE_CONFIG_APPLY_QUEUE_APPLE, durable = "true"),
            exchange = @Exchange(value = GEOFENCE_EVENT_EXCHANGE, type = "topic"),
            key = GEOFENCE_CONFIG_APPLY_ROUTE_KEY_APPLE
    ))
    public void handleConfigApply(GeofenceConfigApplyEvent event) {
        try {
            logger.info("Geofence config apply received for device: {}", event.getDeviceIdentifier());

            if (event.getDeviceIdentifier() == null || event.getDeviceIdentifier().isBlank()) {
                logger.error("Geofence config apply event has no device identifier, skipping");
                return;
            }

            List<GeofenceLocationPayload> locations = event.getLocations();
            if (locations == null) {
                locations = List.of();
            }

            // Desired state: empty list = clear all geofences on device
            List<Map<String, Object>> locationMaps = locations.stream()
                    .map(loc -> Map.<String, Object>of(
                            "locationId", loc.getLocationId().toString(),
                            "locationType", loc.getLocationType(),
                            "geometry", loc.getGeometry(),
                            "metadata", loc.getMetadata() != null ? loc.getMetadata() : Map.of()
                    ))
                    .collect(Collectors.toList());

            Map<String, Object> payload = Map.of("locations", locationMaps);

            agentCommandService.sendCommand(
                    event.getDeviceIdentifier(),
                    "update_geofences",
                    payload
            );

            logger.info("Geofence config pushed via MQTT to device: {} ({} locations)",
                    event.getDeviceIdentifier(), locations.size());
        } catch (Exception e) {
            logger.error("Failed to apply geofence config for device {}: {}",
                    event.getDeviceIdentifier(), e.getMessage(), e);
        }
    }
}
