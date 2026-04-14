package com.arcyintel.arcops.apple_mdm.services.enrollment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCleanupService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Builds a snapshot of all device data before cleanup.
     * Captures row counts and key identifiers for audit trail.
     */
    public Map<String, Object> buildDeviceSnapshot(UUID deviceId, String udid) {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        // Device basic info
        var deviceRows = jdbcTemplate.queryForList(
                "SELECT id, serial_number, product_name, udid, os_version, build_version, " +
                "enrollment_type, status, agent_online, agent_version, agent_platform, agent_last_seen_at, " +
                "creation_date, last_modified_date " +
                "FROM apple_device WHERE id = ?", deviceId);
        if (!deviceRows.isEmpty()) {
            snapshot.put("device", deviceRows.get(0));
        }

        // Device information
        var infoRows = jdbcTemplate.queryForList(
                "SELECT * FROM apple_device_information WHERE id = ?", deviceId);
        if (!infoRows.isEmpty()) {
            snapshot.put("deviceInformation", infoRows.get(0));
        }

        // App count + list
        var apps = jdbcTemplate.queryForList(
                "SELECT app_name, bundle_identifier, version FROM apple_device_apps WHERE device_id = ?", deviceId);
        snapshot.put("installedApps", apps);
        snapshot.put("installedAppCount", apps.size());

        // Agent data counts
        snapshot.put("locationCount", countRows("agent_location", deviceId));
        snapshot.put("telemetryCount", countRows("agent_telemetry", deviceId));
        snapshot.put("presenceHistoryCount", countRows("agent_presence_history", deviceId));
        snapshot.put("activityLogCount", countRows("agent_activity_log", deviceId));

        // Command summary
        if (udid != null) {
            snapshot.put("commandCount", jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM agent_command WHERE device_identifier = ?",
                    Integer.class, udid));
        }

        // Auth history count
        snapshot.put("authHistoryCount", countRows("device_auth_history", deviceId));

        // Account associations
        var accounts = jdbcTemplate.queryForList(
                "SELECT aa.id, aa.username, aa.managed_apple_id FROM apple_account_devices aad " +
                "JOIN apple_account aa ON aa.id = aad.account_id WHERE aad.device_id = ?", deviceId);
        snapshot.put("accounts", accounts);

        return snapshot;
    }

    /**
     * Cleans up all device-related data from agent tables.
     * Must be called AFTER snapshot and BEFORE/AFTER setting device status to DELETED.
     */
    public void cleanupDeviceData(UUID deviceId, String udid) {
        int locations = jdbcTemplate.update("DELETE FROM agent_location WHERE device_id = ?", deviceId);
        int telemetry = jdbcTemplate.update("DELETE FROM agent_telemetry WHERE device_id = ?", deviceId);
        int presence = jdbcTemplate.update("DELETE FROM agent_presence_history WHERE device_id = ?", deviceId);
        int activity = jdbcTemplate.update("DELETE FROM agent_activity_log WHERE device_id = ?", deviceId);
        int authHistory = jdbcTemplate.update("DELETE FROM device_auth_history WHERE device_id = ?", deviceId);
        int apps = jdbcTemplate.update("DELETE FROM apple_device_apps WHERE device_id = ?", deviceId);
        int deviceLocations = jdbcTemplate.update("DELETE FROM apple_device_location WHERE device_id = ?", deviceId);
        int accountDevices = jdbcTemplate.update("DELETE FROM apple_account_devices WHERE device_id = ?", deviceId);

        // apple_device_information shares PK with apple_device — delete it
        int deviceInfo = jdbcTemplate.update("DELETE FROM apple_device_information WHERE id = ?", deviceId);

        // agent_command and apple_command use device_identifier / apple_device_udid (udid)
        int agentCommands = 0;
        int appleCommands = 0;
        if (udid != null) {
            agentCommands = jdbcTemplate.update("DELETE FROM agent_command WHERE device_identifier = ?", udid);
            appleCommands = jdbcTemplate.update("DELETE FROM apple_command WHERE apple_device_udid = ?", udid);
        }

        log.info("Device {} cleanup complete: locations={}, telemetry={}, presence={}, activity={}, " +
                 "authHistory={}, apps={}, deviceLocations={}, accountDevices={}, deviceInfo={}, " +
                 "agentCommands={}, appleCommands={}",
                deviceId, locations, telemetry, presence, activity, authHistory, apps,
                deviceLocations, accountDevices, deviceInfo, agentCommands, appleCommands);
    }

    private int countRows(String table, UUID deviceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE device_id = ?", Integer.class, deviceId);
        return count != null ? count : 0;
    }
}
