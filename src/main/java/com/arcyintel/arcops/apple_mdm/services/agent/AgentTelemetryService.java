package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.domains.AgentTelemetry;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.repositories.AgentTelemetryRepository;
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
 * Handles telemetry messages received from devices via MQTT.
 * Parses the JSON payload and persists it to the agent_telemetry table.
 *
 * Topic: arcops/{platform}/devices/{deviceIdentifier}/telemetry
 */
@Service
@RequiredArgsConstructor
public class AgentTelemetryService {

    private static final Logger logger = LoggerFactory.getLogger(AgentTelemetryService.class);

    private final ObjectMapper objectMapper;
    private final AppleDeviceRepository appleDeviceRepository;
    private final AgentTelemetryRepository telemetryRepository;

    @Transactional
    public void handleTelemetryMessage(String deviceIdentifier, String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            Instant now = Instant.now();

            // Parse deviceCreatedAt from payload, fallback to server time
            Instant deviceCreatedAt = parseInstant(json, "deviceCreatedAt", now);

            AppleDevice device = appleDeviceRepository.findByUdid(deviceIdentifier).orElse(null);

            JsonNode battery = json.path("battery");
            JsonNode storage = json.path("storage");
            JsonNode memory = json.path("memory");
            JsonNode system = json.path("system");
            JsonNode network = json.path("network");
            JsonNode security = json.path("security");
            JsonNode locale = json.path("locale");

            AgentTelemetry telemetry = AgentTelemetry.builder()
                    .device(device)
                    .deviceIdentifier(deviceIdentifier)
                    .deviceCreatedAt(deviceCreatedAt)
                    .serverReceivedAt(now)
                    // Battery
                    .batteryLevel(intOrNull(battery, "level"))
                    .batteryCharging(boolOrNull(battery, "charging"))
                    .batteryState(textOrNull(battery, "state"))
                    .lowPowerMode(boolOrNull(battery, "lowPowerMode"))
                    // Storage
                    .storageTotalBytes(longOrNull(storage, "totalBytes"))
                    .storageFreeBytes(longOrNull(storage, "freeBytes"))
                    .storageUsedBytes(longOrNull(storage, "usedBytes"))
                    .storageUsagePercent(intOrNull(storage, "usagePercent"))
                    // Memory
                    .memoryTotalBytes(longOrNull(memory, "totalBytes"))
                    .memoryAvailableBytes(longOrNull(memory, "availableBytes"))
                    // System
                    .systemUptime(intOrNull(system, "uptime"))
                    .cpuCores(intOrNull(system, "cpuCores"))
                    .thermalState(textOrNull(system, "thermalState"))
                    .brightness(intOrNull(system, "brightness"))
                    .osVersion(textOrNull(system, "osVersion"))
                    .modelIdentifier(textOrNull(system, "modelIdentifier"))
                    .deviceModel(textOrNull(system, "deviceModel"))
                    // Network
                    .networkType(textOrNull(network, "type"))
                    .ipAddress(textOrNull(network, "ipAddress"))
                    .wifiSsid(textOrNull(network, "wifiSSID"))
                    .isExpensive(boolOrNull(network, "isExpensive"))
                    .isConstrained(boolOrNull(network, "isConstrained"))
                    .vpnActive(boolOrNull(network, "vpnActive"))
                    .carrierName(textOrNull(network, "carrierName"))
                    .radioTechnology(textOrNull(network, "radioTechnology"))
                    // Security
                    .jailbreakDetected(boolOrNull(security, "jailbreakDetected"))
                    .debuggerAttached(boolOrNull(security, "debuggerAttached"))
                    // Locale
                    .localeLanguage(textOrNull(locale, "language"))
                    .localeRegion(textOrNull(locale, "region"))
                    .localeTimezone(textOrNull(locale, "timezone"))
                    .build();

            telemetryRepository.save(telemetry);

            // Update device last seen
            if (device != null) {
                device.setAgentLastSeenAt(now);
                appleDeviceRepository.save(device);
            }

            logger.debug("Telemetry saved — device={}, deviceCreatedAt={}", deviceIdentifier, deviceCreatedAt);
        } catch (Exception e) {
            logger.error("Failed to process telemetry from device {}: {}", deviceIdentifier, e.getMessage());
        }
    }

}
