package com.arcyintel.arcops.apple_mdm.services.apple.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Tracks policy compliance by monitoring all commands related to a policy application.
 * Uses Redis for persistent storage to survive application restarts.
 * A policy is compliant only when ALL related commands (InstallProfile, InstallApp, Settings, DeclarativeManagement) succeed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyComplianceTracker {

    private static final String REDIS_KEY_PREFIX = "policy:compliance:";
    private static final Duration TTL = Duration.ofHours(24); // 24 hours TTL

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Starts tracking a new policy application for a device.
     */
    public void startTracking(String deviceUdid) {
        log.info("Starting compliance tracking for device UDID: {}", deviceUdid);
        String key = getRedisKey(deviceUdid);
        PolicyComplianceData data = new PolicyComplianceData();
        redisTemplate.opsForValue().set(key, data, TTL);
    }

    /**
     * Registers a command UUID that belongs to the current policy application.
     */
    public void registerCommand(String deviceUdid, String commandUuid, String commandType, String settingName) {
        String key = getRedisKey(deviceUdid);
        PolicyComplianceData data = getTrackingData(deviceUdid);

        if (data == null) {
            log.warn("No active tracking found for device UDID: {}. Cannot register command: {}", deviceUdid, commandUuid);
            return;
        }

        data.addCommand(commandUuid, commandType, settingName);
        redisTemplate.opsForValue().set(key, data, TTL);
        log.debug("Registered command {} of type {} for device {}", commandUuid, commandType, deviceUdid);
    }

    /**
     * Marks a command as successful.
     */
    public void markCommandSuccess(String deviceUdid, String commandUuid) {
        String key = getRedisKey(deviceUdid);
        PolicyComplianceData data = getTrackingData(deviceUdid);

        if (data == null) {
            log.debug("No active tracking found for device UDID: {}. Command {} may not be policy-related.", deviceUdid, commandUuid);
            return;
        }

        boolean wasTracked = data.markSuccess(commandUuid);
        if (wasTracked) {
            redisTemplate.opsForValue().set(key, data, TTL);
            log.info("Command {} marked as successful for device {}", commandUuid, deviceUdid);
        }
    }

    /**
     * Marks a command as failed with reason.
     */
    public void markCommandFailure(String deviceUdid, String commandUuid, String failureReason) {
        String key = getRedisKey(deviceUdid);
        PolicyComplianceData data = getTrackingData(deviceUdid);

        if (data == null) {
            log.debug("No active tracking found for device UDID: {}. Command {} may not be policy-related.", deviceUdid, commandUuid);
            return;
        }

        boolean wasTracked = data.markFailure(commandUuid, failureReason);
        if (wasTracked) {
            redisTemplate.opsForValue().set(key, data, TTL);
            log.warn("Command {} marked as failed for device {}: {}", commandUuid, deviceUdid, failureReason);
        }
    }

    /**
     * Checks if all commands have been processed (success or failure).
     */
    public boolean isTrackingComplete(String deviceUdid) {
        PolicyComplianceData data = getTrackingData(deviceUdid);
        return data != null && data.isComplete();
    }

    /**
     * Returns the final compliance status.
     */
    public ComplianceResult getComplianceResult(String deviceUdid) {
        PolicyComplianceData data = getTrackingData(deviceUdid);

        if (data == null) {
            log.warn("No tracking found for device UDID: {}", deviceUdid);
            return new ComplianceResult(true, Collections.emptyMap());
        }

        if (!data.isComplete()) {
            log.warn("Compliance tracking not complete for device UDID: {}. Returning partial result.", deviceUdid);
        }

        boolean isCompliant = data.getFailures().isEmpty();
        Map<String, Object> failures = new HashMap<>();

        for (Map.Entry<String, PolicyComplianceData.CommandTrackingData> entry : data.getFailures().entrySet()) {
            PolicyComplianceData.CommandTrackingData cmd = entry.getValue();
            Map<String, String> failureDetail = new HashMap<>();
            failureDetail.put("commandType", cmd.getCommandType());
            failureDetail.put("setting", cmd.getSettingName() != null ? cmd.getSettingName() : "N/A");
            failureDetail.put("reason", cmd.getFailureReason() != null ? cmd.getFailureReason() : "Unknown");
            failures.put(entry.getKey(), failureDetail);
        }

        log.info("Compliance result for device {}: compliant={}, failures={}", deviceUdid, isCompliant, failures.size());
        return new ComplianceResult(isCompliant, failures);
    }

    /**
     * Clears tracking for a device (call after processing result).
     */
    public void clearTracking(String deviceUdid) {
        String key = getRedisKey(deviceUdid);
        redisTemplate.delete(key);
        log.info("Cleared compliance tracking for device UDID: {}", deviceUdid);
    }

    /**
     * Gets tracking data from Redis.
     */
    private PolicyComplianceData getTrackingData(String deviceUdid) {
        String key = getRedisKey(deviceUdid);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj instanceof PolicyComplianceData) {
            return (PolicyComplianceData) obj;
        }
        return null;
    }

    /**
     * Generates Redis key for a device.
     */
    private String getRedisKey(String deviceUdid) {
        return REDIS_KEY_PREFIX + deviceUdid;
    }

    /**
     * Result object for compliance status.
     */
    public record ComplianceResult(boolean isCompliant, Map<String, Object> failures) {
    }
}
