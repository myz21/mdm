package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.domains.AgentLocation;
import com.arcyintel.arcops.apple_mdm.domains.AgentPresenceHistory;
import com.arcyintel.arcops.apple_mdm.domains.AgentTelemetry;
import com.arcyintel.arcops.apple_mdm.repositories.AgentLocationRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AgentPresenceHistoryRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AgentTelemetryRepository;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentDeviceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentDeviceDataServiceImpl implements AgentDeviceDataService {

    private final AgentTelemetryRepository telemetryRepository;
    private final AgentLocationRepository locationRepository;
    private final AgentPresenceHistoryRepository presenceRepository;

    @Override
    public Optional<AgentTelemetry> getLatestTelemetry(String udid) {
        return telemetryRepository.findFirstByDeviceIdentifierOrderByDeviceCreatedAtDesc(udid);
    }

    @Override
    public List<AgentTelemetry> getTelemetryHistory(String udid, Instant from, Instant to) {
        return telemetryRepository
                .findByDeviceIdentifierAndDeviceCreatedAtBetweenOrderByDeviceCreatedAtAsc(udid, from, to);
    }

    @Override
    public List<AgentTelemetry> getAllTelemetry(String udid) {
        return telemetryRepository.findByDeviceIdentifierOrderByDeviceCreatedAtDesc(udid);
    }

    @Override
    public Optional<AgentLocation> getLatestLocation(String udid) {
        return locationRepository.findFirstByDeviceIdentifierOrderByDeviceCreatedAtDesc(udid);
    }

    @Override
    public List<AgentLocation> getLocationHistory(String udid, Instant from, Instant to) {
        return locationRepository
                .findByDeviceIdentifierAndDeviceCreatedAtBetweenOrderByDeviceCreatedAtAsc(udid, from, to);
    }

    @Override
    public List<AgentLocation> getAllLocations(String udid) {
        return locationRepository.findByDeviceIdentifierOrderByDeviceCreatedAtDesc(udid);
    }

    @Override
    public List<AgentPresenceHistory> getPresenceHistory(String udid, Instant from, Instant to) {
        return presenceRepository
                .findByDeviceIdentifierAndTimestampBetweenOrderByTimestampAsc(udid, from, to);
    }

    @Override
    public List<AgentPresenceHistory> getAllPresence(String udid) {
        return presenceRepository.findByDeviceIdentifierOrderByTimestampDesc(udid);
    }
}
