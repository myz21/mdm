package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.repositories.AgentLocationRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AgentPresenceHistoryRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AgentTelemetryRepository;
import com.arcyintel.arcops.commons.license.LicenseContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled service that purges agent data older than the license retention period.
 * BASIC=30 days, PLUS=60 days, PREMIUM/ENTERPRISE=90 days.
 * Falls back to 30 days if no license is loaded.
 */
@Service
@RequiredArgsConstructor
public class AgentDataRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(AgentDataRetentionService.class);
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final String RETENTION_LOCK = "apple:data-retention:lock";

    private final AgentPresenceHistoryRepository presenceRepository;
    private final AgentTelemetryRepository telemetryRepository;
    private final AgentLocationRepository locationRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<LicenseContext> licenseContextProvider;

    @Scheduled(fixedRate = 3_600_000) // every hour
    @Transactional
    public void purgeExpiredAgentData() {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(RETENTION_LOCK, "1", Duration.ofMinutes(50));
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        LicenseContext lc = licenseContextProvider.getIfAvailable();
        int retentionDays = (lc != null && lc.isLoaded()) ? lc.getDataRetentionDays() : DEFAULT_RETENTION_DAYS;

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int presenceDeleted = presenceRepository.deleteByTimestampBefore(cutoff);
        int telemetryDeleted = telemetryRepository.deleteByDeviceCreatedAtBefore(cutoff);
        int locationDeleted = locationRepository.deleteByDeviceCreatedAtBefore(cutoff);

        int total = presenceDeleted + telemetryDeleted + locationDeleted;
        if (total > 0) {
            logger.info("Agent data retention: purged {} records older than {} days " +
                            "(presence={}, telemetry={}, location={})",
                    total, retentionDays, presenceDeleted, telemetryDeleted, locationDeleted);
        }
    }
}
