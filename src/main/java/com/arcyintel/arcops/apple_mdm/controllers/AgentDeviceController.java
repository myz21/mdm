package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.AgentLocation;
import com.arcyintel.arcops.commons.web.RawResponse;
import com.arcyintel.arcops.apple_mdm.domains.AgentPresenceHistory;
import com.arcyintel.arcops.apple_mdm.domains.AgentTelemetry;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentDeviceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for managing agent device data and sending commands.
 * Used by the admin dashboard to interact with devices.
 */
@RestController
@RawResponse
@RequestMapping("/agent/devices")
@RequiredArgsConstructor
@Tag(name = "Agent Devices", description = "Agent device management and commands")
public class AgentDeviceController {

    private final AgentCommandService agentCommandService;
    private final AgentDeviceDataService agentDeviceDataService;

    // MARK: - Commands

    @Operation(summary = "Send policy configuration to a device")
    @PostMapping("/{udid}/policy")
    public ResponseEntity<?> sendPolicy(
            @PathVariable String udid,
            @RequestBody Map<String, Object> policy) {

        var command = agentCommandService.sendCommand(udid, "UpdateConfig", policy);
        return ResponseEntity.ok(Map.of(
                "commandId", command.getCommandUuid(),
                "status", command.getStatus()
        ));
    }

    @Operation(summary = "Request immediate telemetry from a device")
    @PostMapping("/{udid}/request-telemetry")
    public ResponseEntity<?> requestTelemetry(@PathVariable String udid) {
        var command = agentCommandService.sendCommand(udid, "request_telemetry", Map.of());
        return ResponseEntity.ok(Map.of(
                "commandId", command.getCommandUuid(),
                "status", command.getStatus()
        ));
    }

    @Operation(summary = "Request immediate location from a device")
    @PostMapping("/{udid}/request-location")
    public ResponseEntity<?> requestLocation(@PathVariable String udid) {
        var command = agentCommandService.sendCommand(udid, "request_location", Map.of());
        return ResponseEntity.ok(Map.of(
                "commandId", command.getCommandUuid(),
                "status", command.getStatus()
        ));
    }

    // MARK: - Telemetry Data

    @Operation(summary = "Get latest telemetry for a device")
    @GetMapping("/{udid}/telemetry/latest")
    public ResponseEntity<?> getLatestTelemetry(@PathVariable String udid) {
        return agentDeviceDataService.getLatestTelemetry(udid)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Get telemetry history for a device, optionally filtered by time range")
    @GetMapping("/{udid}/telemetry")
    public ResponseEntity<List<AgentTelemetry>> getTelemetryHistory(
            @PathVariable String udid,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        if (from != null && to != null) {
            return ResponseEntity.ok(agentDeviceDataService.getTelemetryHistory(udid, from, to));
        }
        return ResponseEntity.ok(agentDeviceDataService.getAllTelemetry(udid));
    }

    // MARK: - Location Data

    @Operation(summary = "Get latest location for a device")
    @GetMapping("/{udid}/location/latest")
    public ResponseEntity<?> getLatestLocation(@PathVariable String udid) {
        return agentDeviceDataService.getLatestLocation(udid)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Get location history for a device, optionally filtered by time range")
    @GetMapping("/{udid}/location")
    public ResponseEntity<List<AgentLocation>> getLocationHistory(
            @PathVariable String udid,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        if (from != null && to != null) {
            return ResponseEntity.ok(agentDeviceDataService.getLocationHistory(udid, from, to));
        }
        return ResponseEntity.ok(agentDeviceDataService.getAllLocations(udid));
    }

    // MARK: - Presence History

    @Operation(summary = "Get presence (online/offline) history for a device, optionally filtered by time range")
    @GetMapping("/{udid}/presence")
    public ResponseEntity<List<AgentPresenceHistory>> getPresenceHistory(
            @PathVariable String udid,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        if (from != null && to != null) {
            return ResponseEntity.ok(agentDeviceDataService.getPresenceHistory(udid, from, to));
        }
        return ResponseEntity.ok(agentDeviceDataService.getAllPresence(udid));
    }
}
