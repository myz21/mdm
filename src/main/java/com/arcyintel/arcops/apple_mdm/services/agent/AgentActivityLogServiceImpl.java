package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.domains.AgentActivityLog;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.repositories.AgentActivityLogRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentActivityLogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentActivityLogServiceImpl implements AgentActivityLogService {

    private static final Logger logger = LoggerFactory.getLogger(AgentActivityLogServiceImpl.class);

    private final AgentActivityLogRepository activityLogRepository;
    private final AppleDeviceRepository appleDeviceRepository;

    @Override
    public AgentActivityLog logStart(String udid, String activityType, String sessionId,
                                      Map<String, Object> details, String initiatedBy) {
        AppleDevice device = resolveDevice(udid);

        AgentActivityLog log = AgentActivityLog.builder()
                .device(device)
                .deviceIdentifier(udid)
                .activityType(activityType)
                .status("STARTED")
                .details(details)
                .sessionId(sessionId)
                .initiatedBy(initiatedBy)
                .build();

        AgentActivityLog saved = activityLogRepository.save(log);
        logger.info("Activity log started: type={}, sessionId={}, udid={}", activityType, sessionId, udid);
        return saved;
    }

    @Override
    public void logComplete(String sessionId) {
        Optional<AgentActivityLog> logOpt = activityLogRepository.findBySessionIdAndStatus(sessionId, "STARTED");
        if (logOpt.isEmpty()) {
            logger.warn("No STARTED activity log found for sessionId={}", sessionId);
            return;
        }

        AgentActivityLog log = logOpt.get();
        Instant now = Instant.now();
        log.setStatus("COMPLETED");
        log.setEndedAt(now);
        log.setDurationSeconds(Duration.between(log.getStartedAt(), now).getSeconds());

        activityLogRepository.save(log);
        logger.info("Activity log completed: sessionId={}, duration={}s", sessionId, log.getDurationSeconds());
    }

    @Override
    public AgentActivityLog logNotification(String udid, Map<String, Object> details,
                                             String channel, String initiatedBy) {
        AppleDevice device = resolveDevice(udid);

        Map<String, Object> enrichedDetails = new java.util.HashMap<>(details);
        enrichedDetails.put("deliveryChannel", channel);

        AgentActivityLog log = AgentActivityLog.builder()
                .device(device)
                .deviceIdentifier(udid)
                .activityType("NOTIFICATION")
                .status("COMPLETED")
                .details(enrichedDetails)
                .initiatedBy(initiatedBy)
                .build();

        AgentActivityLog saved = activityLogRepository.save(log);
        logger.info("Notification activity logged: udid={}, channel={}", udid, channel);
        return saved;
    }

    @Override
    public List<AgentActivityLog> getDeviceActivityLog(UUID deviceId, int limit) {
        return activityLogRepository.findByDevice_IdOrderByCreatedAtDesc(
                deviceId, PageRequest.of(0, limit));
    }

    private AppleDevice resolveDevice(String udid) {
        return appleDeviceRepository.findByUdid(udid).orElse(null);
    }
}
