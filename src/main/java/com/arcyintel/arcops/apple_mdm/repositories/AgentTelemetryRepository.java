package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AgentTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentTelemetryRepository extends JpaRepository<AgentTelemetry, UUID> {

    @Modifying
    @Query("DELETE FROM AgentTelemetry e WHERE e.deviceCreatedAt < :before")
    int deleteByDeviceCreatedAtBefore(Instant before);

    List<AgentTelemetry> findByDeviceIdOrderByDeviceCreatedAtDesc(UUID deviceId);

    List<AgentTelemetry> findByDeviceIdentifierOrderByDeviceCreatedAtDesc(String deviceIdentifier);

    Optional<AgentTelemetry> findFirstByDeviceIdentifierOrderByDeviceCreatedAtDesc(String deviceIdentifier);

    List<AgentTelemetry> findByDeviceIdAndDeviceCreatedAtBetweenOrderByDeviceCreatedAtAsc(
            UUID deviceId, Instant from, Instant to);

    long countByDeviceIdAndDeviceCreatedAtBetween(UUID deviceId, Instant from, Instant to);

    List<AgentTelemetry> findByDeviceIdentifierAndDeviceCreatedAtBetweenOrderByDeviceCreatedAtAsc(
            String deviceIdentifier, Instant from, Instant to);

    @Query(value = """
            SELECT t.* FROM agent_telemetry t
            INNER JOIN (
                SELECT device_identifier, MAX(server_received_at) as max_time
                FROM agent_telemetry
                GROUP BY device_identifier
            ) latest ON t.device_identifier = latest.device_identifier
                    AND t.server_received_at = latest.max_time
            """, nativeQuery = true)
    List<AgentTelemetry> findLatestPerDevice();

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (t.device_id) t.*
                FROM agent_telemetry t
                JOIN apple_device d ON d.id = t.device_id
                WHERE d.status <> 'DELETED'
                ORDER BY t.device_id, t.server_received_at DESC
            )
            SELECT
              COUNT(*) AS total,
              COALESCE(AVG(l.battery_level), 0) AS avg_battery,
              COUNT(*) FILTER (WHERE l.battery_level < 20) AS low_battery,
              COUNT(*) FILTER (WHERE l.storage_usage_percent > 90) AS critical_storage,
              COUNT(*) FILTER (WHERE l.thermal_state IN ('fair', 'serious', 'critical')) AS thermal_warning,
              COALESCE(COUNT(*) FILTER (WHERE l.network_type = 'wifi'), 0) AS net_wifi,
              COALESCE(COUNT(*) FILTER (WHERE l.network_type = 'cellular'), 0) AS net_cellular,
              COALESCE(COUNT(*) FILTER (WHERE l.network_type = 'ethernet'), 0) AS net_ethernet,
              COALESCE(COUNT(*) FILTER (WHERE l.network_type = 'none' OR l.network_type IS NULL), 0) AS net_none
            FROM latest l
            JOIN apple_device d ON d.id = l.device_id
            WHERE (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            """, nativeQuery = true)
    List<Object[]> getFleetHealthSummary(@Param("platform") String platform);

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (t.device_id) t.*
                FROM agent_telemetry t
                JOIN apple_device d ON d.id = t.device_id
                WHERE d.status <> 'DELETED'
                ORDER BY t.device_id, t.server_received_at DESC
            )
            SELECT
              WIDTH_BUCKET(l.battery_level, 0, 100, 10) AS bucket,
              COUNT(*) AS cnt
            FROM latest l
            JOIN apple_device d ON d.id = l.device_id
            WHERE (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> getFleetBatteryDistribution(@Param("platform") String platform);

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (t.device_id) t.*
                FROM agent_telemetry t
                JOIN apple_device d ON d.id = t.device_id
                WHERE d.status <> 'DELETED'
                ORDER BY t.device_id, t.server_received_at DESC
            )
            SELECT
              WIDTH_BUCKET(l.storage_usage_percent, 0, 100, 10) AS bucket,
              COUNT(*) AS cnt
            FROM latest l
            JOIN apple_device d ON d.id = l.device_id
            WHERE (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> getFleetStorageDistribution(@Param("platform") String platform);

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (t.device_id) t.*
                FROM agent_telemetry t
                JOIN apple_device d ON d.id = t.device_id
                WHERE d.status <> 'DELETED'
                ORDER BY t.device_id, t.server_received_at DESC
            )
            SELECT
              d.id AS device_id, d.serial_number, COALESCE(di.device_name, d.product_name) AS product_name,
              (CASE
                 WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                 WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                 WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                 WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                 WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                 WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                 ELSE 'unknown'
               END) AS platform,
              l.battery_level, l.battery_charging, l.battery_state,
              l.storage_total_bytes, l.storage_used_bytes, l.storage_usage_percent,
              l.thermal_state, l.network_type, l.wifi_ssid, l.vpn_active,
              d.agent_last_seen_at,
              COUNT(*) OVER() AS total_count
            FROM latest l
            JOIN apple_device d ON d.id = l.device_id
            LEFT JOIN apple_device_information di ON di.id = d.id
            WHERE (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            ORDER BY
                CASE WHEN :sortBy = 'batteryLevel' AND :sortDesc = FALSE THEN l.battery_level END ASC,
                CASE WHEN :sortBy = 'batteryLevel' AND :sortDesc = TRUE THEN l.battery_level END DESC,
                CASE WHEN :sortBy = 'storageUsagePercent' AND :sortDesc = FALSE THEN l.storage_usage_percent END ASC,
                CASE WHEN :sortBy = 'storageUsagePercent' AND :sortDesc = TRUE THEN l.storage_usage_percent END DESC,
                CASE WHEN :sortBy = 'thermalState' AND :sortDesc = FALSE THEN l.thermal_state END ASC,
                CASE WHEN :sortBy = 'thermalState' AND :sortDesc = TRUE THEN l.thermal_state END DESC,
                CASE WHEN :sortBy = 'agentLastSeenAt' AND :sortDesc = FALSE THEN d.agent_last_seen_at END ASC,
                CASE WHEN :sortBy = 'agentLastSeenAt' AND :sortDesc = TRUE THEN d.agent_last_seen_at END DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> getFleetHealthDevices(
            @Param("platform") String platform,
            @Param("sortBy") String sortBy,
            @Param("sortDesc") boolean sortDesc,
            @Param("size") int size,
            @Param("offset") int offset
    );

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (t.device_id) t.*
                FROM agent_telemetry t
                JOIN apple_device d ON d.id = t.device_id
                WHERE d.status <> 'DELETED'
                ORDER BY t.device_id, t.server_received_at DESC
            )
            SELECT
              COUNT(*) FILTER (WHERE l.jailbreak_detected = true) AS jailbreak_count,
              COUNT(*) FILTER (WHERE l.vpn_active = true) AS vpn_count
            FROM latest l
            JOIN apple_device d ON d.id = l.device_id
            WHERE (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            """, nativeQuery = true)
    List<Object[]> getSecurityTelemetryCounts(@Param("platform") String platform);
}
