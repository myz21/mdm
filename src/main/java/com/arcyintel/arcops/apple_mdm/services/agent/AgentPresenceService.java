package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.domains.AgentPresenceHistory;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.event.publisher.DeviceEventPublisher;
import com.arcyintel.arcops.apple_mdm.repositories.AgentPresenceHistoryRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.services.screenshare.PendingScreenShareService;
import com.arcyintel.arcops.apple_mdm.models.session.RemoteTerminalSession;
import com.arcyintel.arcops.apple_mdm.models.session.ScreenShareSession;
import com.arcyintel.arcops.apple_mdm.services.screenshare.ScreenShareSessionService;
import com.arcyintel.arcops.apple_mdm.services.terminal.PendingTerminalService;
import com.arcyintel.arcops.apple_mdm.services.terminal.RemoteTerminalSessionService;
import com.arcyintel.arcops.commons.events.device.DevicePresenceChangedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.*;

/**
 * Handles device presence (online/offline) status messages received via MQTT.
 * Records state transitions as individual events in agent_presence_history.
 *
 * Each heartbeat updates apple_device.agent_last_seen_at.
 * A new history row is only inserted on actual state transitions:
 *   - ONLINE event:  offline → online  (durationSeconds = offline duration)
 *   - OFFLINE event: online → offline  (durationSeconds = online duration)
 *
 * Topic: arcops/{platform}/devices/{deviceIdentifier}/status
 */
@Service
@RequiredArgsConstructor
public class AgentPresenceService {

    private static final Logger logger = LoggerFactory.getLogger(AgentPresenceService.class);

    @org.springframework.beans.factory.annotation.Value("${webrtc.turn-url:}")
    private String turnUrl;
    @org.springframework.beans.factory.annotation.Value("${webrtc.turn-username:}")
    private String turnUsername;
    @org.springframework.beans.factory.annotation.Value("${webrtc.turn-credential:}")
    private String turnCredential;

    private static final String STALE_DETECTION_LOCK = "apple:presence:stale-detection-lock";
    private static final String STARTUP_RESET_LOCK = "apple:presence:startup-reset-lock";

    private final ObjectMapper objectMapper;
    private final AppleDeviceRepository appleDeviceRepository;
    private final AgentPresenceHistoryRepository presenceHistoryRepository;
    private final AgentCommandService agentCommandService;
    private final DeviceEventPublisher deviceEventPublisher;
    private final PendingNotificationService pendingNotificationService;
    private final PendingScreenShareService pendingScreenShareService;
    private final ScreenShareSessionService screenShareSessionService;
    private final PendingTerminalService pendingTerminalService;
    private final RemoteTerminalSessionService remoteTerminalSessionService;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public void handleStatusMessage(String deviceIdentifier, String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            boolean online = json.path("online").asBoolean(false);
            String platform = json.path("platform").asText(null);
            String agentVersion = json.path("agentVersion").asText(null);

            String agentPushToken = json.path("agentPushToken").asText(null);

            if (online) {
                handleOnline(deviceIdentifier, platform, agentVersion, agentPushToken);
            } else {
                handleOffline(deviceIdentifier);
            }
        } catch (Exception e) {
            logger.error("Failed to process status message from device {}: {}", deviceIdentifier, e.getMessage());
        }
    }

    private void handleOnline(String deviceIdentifier, String platform, String agentVersion, String agentPushToken) {
        Instant now = Instant.now();

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceIdentifier);
        if (deviceOpt.isPresent()) {
            AppleDevice device = deviceOpt.get();
            boolean wasOffline = !Boolean.TRUE.equals(device.getAgentOnline());

            // Always update heartbeat timestamp
            device.setAgentOnline(true);
            device.setAgentLastSeenAt(now);
            device.setAgentPlatform(platform);
            device.setAgentVersion(agentVersion);
            if (agentPushToken != null && !agentPushToken.isBlank()) {
                device.setAgentPushToken(agentPushToken);
            }
            appleDeviceRepository.save(device);

            // Only record transition when state actually changes: offline → online
            if (wasOffline) {
                Long offlineDuration = calculateDurationSinceLastEvent(deviceIdentifier, now);

                presenceHistoryRepository.save(AgentPresenceHistory.builder()
                        .device(device)
                        .deviceIdentifier(deviceIdentifier)
                        .eventType("ONLINE")
                        .timestamp(now)
                        .durationSeconds(offlineDuration)
                        .agentPlatform(platform)
                        .agentVersion(agentVersion)
                        .build());

                logger.info("Device ONLINE — id={}, offlineDuration={}s, platform={}, agentVersion={}",
                        deviceIdentifier, offlineDuration, platform, agentVersion);

                deviceEventPublisher.publishDevicePresenceChangedEvent(
                        new DevicePresenceChangedEvent(device.getId(), true, now.toString(), agentVersion, platform));

                // Re-send agent config so the device picks up any config published while it was offline
                resendAgentConfigIfNeeded(device);

                // Flush any pending notifications queued while the device was offline
                flushPendingNotifications(device);

                // Flush pending screen share session if one was requested while offline
                flushPendingScreenShare(device);

                // Flush pending terminal session if one was requested while offline
                flushPendingTerminal(device);
            }
        } else {
            logger.warn("Device ONLINE but not found in DB — deviceIdentifier={}", deviceIdentifier);
        }
    }

    private void handleOffline(String deviceIdentifier) {
        handleOffline(deviceIdentifier, "graceful");
    }

    private void handleOffline(String deviceIdentifier, String reason) {
        Instant now = Instant.now();

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceIdentifier);
        if (deviceOpt.isPresent()) {
            AppleDevice device = deviceOpt.get();
            boolean wasOnline = Boolean.TRUE.equals(device.getAgentOnline());

            device.setAgentOnline(false);
            device.setAgentLastSeenAt(now);
            appleDeviceRepository.save(device);

            // Only record transition when state actually changes: online → offline
            if (wasOnline) {
                Long onlineDuration = calculateDurationSinceLastEvent(deviceIdentifier, now);

                presenceHistoryRepository.save(AgentPresenceHistory.builder()
                        .device(device)
                        .deviceIdentifier(deviceIdentifier)
                        .eventType("OFFLINE")
                        .timestamp(now)
                        .durationSeconds(onlineDuration)
                        .agentPlatform(device.getAgentPlatform())
                        .agentVersion(device.getAgentVersion())
                        .reason(reason)
                        .build());

                logger.info("Device OFFLINE — id={}, onlineDuration={}s, reason={}",
                        deviceIdentifier, onlineDuration, reason);

                deviceEventPublisher.publishDevicePresenceChangedEvent(
                        new DevicePresenceChangedEvent(device.getId(), false, now.toString(),
                                device.getAgentVersion(), device.getAgentPlatform()));
            }
        } else {
            logger.warn("Device OFFLINE but not found in DB — deviceIdentifier={}", deviceIdentifier);
        }
    }

    /**
     * Re-sends agent config (dataServices) when a device reconnects.
     * With cleanSession=true, MQTT messages sent while the device was offline are lost.
     * This ensures the device always receives its latest configuration on reconnect.
     */
    @SuppressWarnings("unchecked")
    private void resendAgentConfigIfNeeded(AppleDevice device) {
        Map<String, Object> appliedPolicy = device.getAppliedPolicy();
        if (appliedPolicy == null) return;

        Object payloadObj = appliedPolicy.get("payload");
        if (!(payloadObj instanceof Map<?, ?> payloadRaw)) return;
        Map<String, Object> payload = (Map<String, Object>) payloadRaw;

        Object dsObj = payload.get(DATA_SERVICES);
        if (!(dsObj instanceof Map<?, ?> dsRaw)) return;
        Map<String, Object> ds = (Map<String, Object>) dsRaw;

        Map<String, Object> agentPayload = new LinkedHashMap<>();
        if (ds.containsKey(AGENT_TELEMETRY)){
            Map<String,Object> telemetry = (Map<String, Object>) ds.get(AGENT_TELEMETRY);
            if (telemetry.containsKey("enabled") && telemetry.get("enabled") == Boolean.TRUE){
                agentPayload.put(TELEMETRY_INTERVAL_SECONDS, telemetry.get(TELEMETRY_INTERVAL_SECONDS));
                agentPayload.put(AGENT_TELEMETRY, Boolean.TRUE);
            }
        }
        if (ds.containsKey(AGENT_LOCATION)){
            Map<String,Object> location = (Map<String, Object>) ds.get(AGENT_LOCATION);
            if (location.containsKey("enabled") && location.get("enabled") == Boolean.TRUE){
                agentPayload.put(AGENT_LOCATION, Boolean.TRUE);
                agentPayload.put(LOCATION_INTERVAL_SECONDS, location.get(LOCATION_INTERVAL_SECONDS));
            }
        }

        if (!agentPayload.isEmpty()) {
            try {
                agentCommandService.sendCommand(device.getUdid(), CMD_UPDATE_CONFIG, agentPayload);
                logger.info("Re-sent agent config on reconnect for device {}: {}", device.getUdid(), agentPayload.keySet());
            } catch (Exception e) {
                logger.error("Failed to re-send agent config on reconnect for device {}: {}", device.getUdid(), e.getMessage());
            }
        }

        // Re-send blocked app list for macOS agents
        resendBlockedAppsIfNeeded(device, payload);
    }

    /**
     * Re-sends the blocked app list to the macOS agent on reconnect.
     * Reads the _resolvedBlockedApps list that was stored during policy application.
     */
    @SuppressWarnings("unchecked")
    private void resendBlockedAppsIfNeeded(AppleDevice device, Map<String, Object> payload) {
        Object appMgmtObj = payload.get("applicationManagement");
        if (!(appMgmtObj instanceof Map<?, ?> appMgmtRaw)) return;

        Map<String, Object> appMgmt = (Map<String, Object>) appMgmtRaw;
        Object resolvedBlocked = appMgmt.get("_resolvedBlockedApps");
        if (!(resolvedBlocked instanceof List<?> blockedList) || blockedList.isEmpty()) return;

        List<String> blockedApps = blockedList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();

        try {
            agentCommandService.sendCommand(device.getUdid(), CMD_UPDATE_APP_POLICY,
                    Map.of("blockedBundleIds", blockedApps));
            logger.info("Re-sent app blocking policy ({} apps) on reconnect for device {}",
                    blockedApps.size(), device.getUdid());
        } catch (Exception e) {
            logger.error("Failed to re-send app blocking policy on reconnect for device {}: {}",
                    device.getUdid(), e.getMessage());
        }
    }

    /**
     * Calculates seconds since the last event for this device.
     * Returns null if no previous event exists.
     */
    private Long calculateDurationSinceLastEvent(String deviceIdentifier, Instant now) {
        return presenceHistoryRepository
                .findFirstByDeviceIdentifierOrderByTimestampDesc(deviceIdentifier)
                .map(last -> Duration.between(last.getTimestamp(), now).getSeconds())
                .orElse(null);
    }

    /**
     * On server startup, mark all currently-online devices as offline
     * since we can't know their actual state after a restart.
     * Uses a Redis distributed lock to ensure only one instance performs the reset.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void markOnlineDevicesOfflineOnStartup() {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(STARTUP_RESET_LOCK, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(acquired)) {
            logger.info("Another instance is handling startup device reset, skipping");
            return;
        }

        List<AppleDevice> onlineDevices = appleDeviceRepository.findByAgentOnlineTrue();
        if (onlineDevices.isEmpty()) return;

        Instant now = Instant.now();
        for (AppleDevice device : onlineDevices) {
            Long onlineDuration = calculateDurationSinceLastEvent(device.getUdid(), now);

            device.setAgentOnline(false);
            appleDeviceRepository.save(device);

            presenceHistoryRepository.save(AgentPresenceHistory.builder()
                    .device(device)
                    .deviceIdentifier(device.getUdid())
                    .eventType("OFFLINE")
                    .timestamp(now)
                    .durationSeconds(onlineDuration)
                    .agentPlatform(device.getAgentPlatform())
                    .agentVersion(device.getAgentVersion())
                    .reason("server_restart")
                    .build());

            deviceEventPublisher.publishDevicePresenceChangedEvent(
                    new DevicePresenceChangedEvent(device.getId(), false, now.toString(),
                            device.getAgentVersion(), device.getAgentPlatform()));
        }
        logger.info("Marked {} devices as OFFLINE on startup", onlineDevices.size());
    }

    /**
     * Periodically detects devices that missed heartbeats and marks them as offline.
     * Runs every 60 seconds. Threshold: 90 seconds (3x 30s heartbeat interval).
     * Uses a Redis distributed lock to ensure only one instance runs detection at a time.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void detectStaleDevices() {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(STALE_DETECTION_LOCK, "1", Duration.ofSeconds(55));
        if (!Boolean.TRUE.equals(acquired)) {
            return; // Another instance is running stale detection
        }

        Instant now = Instant.now();
        Instant threshold = now.minus(90, ChronoUnit.SECONDS);
        List<AppleDevice> staleDevices = appleDeviceRepository
                .findByAgentOnlineTrueAndAgentLastSeenAtBefore(threshold);

        if (staleDevices.isEmpty()) return;

        logger.info("Detected {} stale devices, marking offline in bulk", staleDevices.size());

        // Batch: update devices + create history records without per-device DB lookups
        List<AgentPresenceHistory> historyBatch = new java.util.ArrayList<>(staleDevices.size());
        for (AppleDevice device : staleDevices) {
            Long onlineDuration = device.getAgentLastSeenAt() != null
                    ? Duration.between(device.getAgentLastSeenAt(), now).getSeconds()
                    : null;

            device.setAgentOnline(false);
            device.setAgentLastSeenAt(now);

            historyBatch.add(AgentPresenceHistory.builder()
                    .device(device)
                    .deviceIdentifier(device.getUdid())
                    .eventType("OFFLINE")
                    .timestamp(now)
                    .durationSeconds(onlineDuration)
                    .agentPlatform(device.getAgentPlatform())
                    .agentVersion(device.getAgentVersion())
                    .reason("heartbeat_timeout")
                    .build());
        }

        appleDeviceRepository.saveAll(staleDevices);
        presenceHistoryRepository.saveAll(historyBatch);

        // Publish RabbitMQ events (outside bulk DB writes)
        for (AppleDevice device : staleDevices) {
            deviceEventPublisher.publishDevicePresenceChangedEvent(
                    new DevicePresenceChangedEvent(device.getId(), false, now.toString(),
                            device.getAgentVersion(), device.getAgentPlatform()));
        }

        logger.info("Marked {} stale devices offline", staleDevices.size());
    }

    /**
     * Flushes pending notifications that were queued while the device was offline.
     */
    @SuppressWarnings("unchecked")
    private void flushPendingNotifications(AppleDevice device) {
        try {
            List<Map<String, Object>> pending = pendingNotificationService.drainPendingNotifications(device.getUdid());
            for (Map<String, Object> notification : pending) {
                // Mark as already pushed so iOS skips duplicate local notification
                Map<String, Object> enriched = new LinkedHashMap<>(notification);
                enriched.put("pushAlreadySent", true);
                agentCommandService.sendCommand(device.getUdid(), CMD_SEND_NOTIFICATION, enriched);
            }
            if (!pending.isEmpty()) {
                logger.info("Flushed {} pending notifications for device {}", pending.size(), device.getUdid());
            }
        } catch (Exception e) {
            logger.error("Failed to flush pending notifications for device {}: {}", device.getUdid(), e.getMessage());
        }
    }

    /**
     * Flushes a pending screen share session that was requested while the device was offline.
     */
    private void flushPendingScreenShare(AppleDevice device) {
        try {
            pendingScreenShareService.drainPending(device.getUdid()).ifPresent(data -> {
                String sessionId = data.get("sessionId");
                String captureType = data.get("captureType");

                // Verify the Redis session still exists before sending the command
                ScreenShareSession session = screenShareSessionService.getSession(sessionId);
                if (session == null) {
                    logger.warn("Pending screen share session expired before device came online — sessionId={}", sessionId);
                    return;
                }

                agentCommandService.sendCommand(device.getUdid(), CMD_START_SCREEN_SHARE, Map.of(
                        "sessionId", sessionId,
                        "stunServers", List.of("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"),
                        "turnServers", turnUrl.isBlank() ? List.of() : List.of(Map.of("url", turnUrl, "username", turnUsername, "credential", turnCredential)),
                        "captureType", captureType != null ? captureType : "screen"
                ));
                logger.info("Flushed pending screen share for device {} — sessionId={}", device.getUdid(), sessionId);
            });
        } catch (Exception e) {
            logger.error("Failed to flush pending screen share for device {}: {}", device.getUdid(), e.getMessage());
        }
    }

    /**
     * Flushes a pending terminal session that was requested while the device was offline.
     */
    private void flushPendingTerminal(AppleDevice device) {
        try {
            pendingTerminalService.drainPending(device.getUdid()).ifPresent(data -> {
                String sessionId = data.get("sessionId");

                // Verify the Redis session still exists before sending the command
                RemoteTerminalSession session = remoteTerminalSessionService.getSession(sessionId);
                if (session == null) {
                    logger.warn("Pending terminal session expired before device came online — sessionId={}", sessionId);
                    return;
                }

                agentCommandService.sendCommand(device.getUdid(), CMD_START_TERMINAL, Map.of(
                        "sessionId", sessionId
                ));
                logger.info("Flushed pending terminal for device {} — sessionId={}", device.getUdid(), sessionId);
            });
        } catch (Exception e) {
            logger.error("Failed to flush pending terminal for device {}: {}", device.getUdid(), e.getMessage());
        }
    }
}
