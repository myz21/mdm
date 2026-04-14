package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.domains.AgentLocation;
import com.arcyintel.arcops.apple_mdm.domains.AgentPresenceHistory;
import com.arcyintel.arcops.apple_mdm.domains.AgentTelemetry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Provides read access to agent device data: telemetry, location, and presence history.
 */
public interface AgentDeviceDataService {

    Optional<AgentTelemetry> getLatestTelemetry(String udid);

    List<AgentTelemetry> getTelemetryHistory(String udid, Instant from, Instant to);

    List<AgentTelemetry> getAllTelemetry(String udid);

    Optional<AgentLocation> getLatestLocation(String udid);

    List<AgentLocation> getLocationHistory(String udid, Instant from, Instant to);

    List<AgentLocation> getAllLocations(String udid);

    List<AgentPresenceHistory> getPresenceHistory(String udid, Instant from, Instant to);

    List<AgentPresenceHistory> getAllPresence(String udid);
}
