package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppleDeviceInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppleDeviceInformationRepository extends JpaRepository<AppleDeviceInformation, UUID> {

    @Query("SELECT adi FROM AppleDeviceInformation adi WHERE adi.id = ?1")
    Optional<AppleDeviceInformation> findByAppleDevice(UUID uuid);

    @Query(value = "SELECT COUNT(*) FROM apple_device_information i " +
            "JOIN apple_device d ON d.id = i.id WHERE d.status <> 'DELETED' AND i.supervised = true", nativeQuery = true)
    long countSupervisedDevices();

    @Query(value = "SELECT COUNT(*) FROM apple_device_information i " +
            "JOIN apple_device d ON d.id = i.id WHERE d.status <> 'DELETED' AND i.awaiting_configuration = true", nativeQuery = true)
    long countAwaitingConfigurationDevices();

    @Query(value = "SELECT i.model_name, COUNT(*) AS cnt FROM apple_device_information i " +
            "JOIN apple_device d ON d.id = i.id WHERE d.status <> 'DELETED' AND i.model_name IS NOT NULL " +
            "GROUP BY i.model_name ORDER BY cnt DESC LIMIT 10", nativeQuery = true)
    List<Object[]> findModelDistribution();

    @Query(value = "SELECT COUNT(*) FROM apple_device_information i " +
            "JOIN apple_device d ON d.id = i.id WHERE d.status <> 'DELETED' AND i.activation_lock_enabled = true", nativeQuery = true)
    long countActivationLockEnabled();

    @Query(value = "SELECT COUNT(*) FROM apple_device_information i " +
            "JOIN apple_device d ON d.id = i.id WHERE d.status <> 'DELETED' AND i.cloud_backup_enabled = true", nativeQuery = true)
    long countCloudBackupEnabled();

    @Query("SELECT i FROM AppleDeviceInformation i JOIN AppleDevice d ON d.id = i.id " +
            "WHERE d.status <> 'DELETED' AND i.deviceCapacity IS NOT NULL")
    List<AppleDeviceInformation> findAllWithCapacity();

    @Query(value = """
            SELECT
                COUNT(*) FILTER (WHERE i.activation_lock_enabled = true),
                COUNT(*) FILTER (WHERE i.activation_lock_enabled IS NOT true),
                COUNT(*) FILTER (WHERE i.cloud_backup_enabled = true),
                COUNT(*) FILTER (WHERE i.cloud_backup_enabled IS NOT true),
                COUNT(*) FILTER (WHERE i.device_locator_service_enabled = true),
                COUNT(*) FILTER (WHERE i.device_locator_service_enabled IS NOT true),
                COUNT(*) FILTER (WHERE i.app_analytics_enabled = true),
                COUNT(*) FILTER (WHERE i.app_analytics_enabled IS NOT true),
                COUNT(*) FILTER (WHERE i.diagnostic_submission_enabled = true),
                COUNT(*) FILTER (WHERE i.diagnostic_submission_enabled IS NOT true)
            FROM apple_device_information i JOIN apple_device d ON d.id = i.id
            WHERE d.status <> 'DELETED'
            """, nativeQuery = true)
    List<Object[]> findFeatureEnablementCounts();

    @Query(value = """
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE i.supervised = true) AS supervised,
              COUNT(*) FILTER (WHERE i.activation_lock_enabled = true) AS activation_lock,
              COUNT(*) FILTER (WHERE i.cloud_backup_enabled = true) AS cloud_backup,
              COUNT(*) FILTER (WHERE i.device_locator_service_enabled = true) AS find_my,
              COUNT(*) FILTER (WHERE i.app_analytics_enabled = true) AS app_analytics,
              COUNT(*) FILTER (WHERE i.diagnostic_submission_enabled = true) AS diagnostic_submission
            FROM apple_device_information i
            JOIN apple_device d ON d.id = i.id
            WHERE d.status <> 'DELETED'
              AND (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            """, nativeQuery = true)
    List<Object[]> getSecuritySummary(@Param("platform") String platform);

    @Query(value = """
            SELECT
              d.id, d.serial_number, COALESCE(i.device_name, d.product_name) AS product_name,
              (CASE
                 WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                 WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                 WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                 WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                 WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                 WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                 ELSE 'unknown'
               END) AS platform,
              d.os_version,
              COALESCE(i.supervised, false) AS supervised,
              COALESCE(i.activation_lock_enabled, false) AS activation_lock_enabled,
              COALESCE(i.cloud_backup_enabled, false) AS cloud_backup_enabled,
              COALESCE(i.device_locator_service_enabled, false) AS find_my_enabled,
              COALESCE(t.jailbreak_detected, false) AS jailbreak_detected,
              COALESCE(t.vpn_active, false) AS vpn_active,
              true AS passcode_compliant,
              COUNT(*) OVER() AS total_count
            FROM apple_device d
            LEFT JOIN apple_device_information i ON i.id = d.id
            LEFT JOIN LATERAL (
                SELECT lt.jailbreak_detected, lt.vpn_active
                FROM agent_telemetry lt
                WHERE lt.device_id = d.id
                ORDER BY lt.server_received_at DESC LIMIT 1
            ) t ON true
            WHERE d.status <> 'DELETED'
              AND (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
              AND (CAST(:filter AS VARCHAR) IS NULL
                   OR (CAST(:filter AS VARCHAR) = 'SUPERVISED' AND i.supervised = true)
                   OR (CAST(:filter AS VARCHAR) = 'NOT_SUPERVISED' AND (i.supervised IS NULL OR i.supervised = false))
                   OR (CAST(:filter AS VARCHAR) = 'ACTIVATION_LOCK' AND i.activation_lock_enabled = true)
                   OR (CAST(:filter AS VARCHAR) = 'NO_ACTIVATION_LOCK' AND (i.activation_lock_enabled IS NULL OR i.activation_lock_enabled = false))
                   OR (CAST(:filter AS VARCHAR) = 'JAILBREAK' AND t.jailbreak_detected = true)
                   OR (CAST(:filter AS VARCHAR) = 'NO_CLOUD_BACKUP' AND (i.cloud_backup_enabled IS NULL OR i.cloud_backup_enabled = false)))
            ORDER BY
                CASE WHEN :sortBy = 'productName' AND :sortDesc = FALSE THEN d.product_name END ASC,
                CASE WHEN :sortBy = 'productName' AND :sortDesc = TRUE THEN d.product_name END DESC,
                CASE WHEN :sortBy = 'osVersion' AND :sortDesc = FALSE THEN d.os_version END ASC,
                CASE WHEN :sortBy = 'osVersion' AND :sortDesc = TRUE THEN d.os_version END DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> getSecurityDevices(
            @Param("platform") String platform,
            @Param("filter") String filter,
            @Param("sortBy") String sortBy,
            @Param("sortDesc") boolean sortDesc,
            @Param("size") int size,
            @Param("offset") int offset
    );
}