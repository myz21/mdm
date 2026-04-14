package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppleDeviceApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppleDeviceAppRepository extends JpaRepository<AppleDeviceApp, UUID> {

    /**
     * Finds a specific app on a specific device using the bundle identifier.
     * Useful for checking if an app exists before updating it individually.
     *
     * @param deviceId         The UUID of the AppleDevice.
     * @param bundleIdentifier The unique bundle ID of the app (e.g., com.whatsapp).
     * @return Optional containing the app if found.
     */
    Optional<AppleDeviceApp> findByAppleDevice_IdAndBundleIdentifier(UUID deviceId, String bundleIdentifier);

    /**
     * Retrieves all applications installed on a specific device.
     * Used for bulk operations and synchronization to avoid N+1 query issues.
     *
     * @param deviceId The UUID of the AppleDevice.
     * @return List of installed apps.
     */
    List<AppleDeviceApp> findAllByAppleDevice_Id(UUID deviceId);

    /**
     * Deletes all applications associated with a specific device.
     * Use with caution. This is typically used when a device is unenrolled or wiped.
     *
     * @param deviceId The UUID of the AppleDevice.
     */
    @Modifying
    @Query("DELETE FROM AppleDeviceApp a WHERE a.appleDevice.id = :deviceId")
    void deleteAllByDeviceId(@Param("deviceId") UUID deviceId);

    @Query(value = "SELECT a.bundle_identifier, a.name, COUNT(*) AS install_count " +
            "FROM apple_device_apps a WHERE a.is_managed = true " +
            "GROUP BY a.bundle_identifier, a.name ORDER BY install_count DESC LIMIT 10", nativeQuery = true)
    List<Object[]> findTopManagedApps();

    /**
     * Fuzzy search apps by name or bundle identifier using pg_trgm similarity.
     * Returns name, bundleIdentifier, installCount grouped and ordered by install count.
     * Platform filter is resolved from apple_device.product_name.
     */
    @Query(value = """
            SELECT a.name, a.bundle_identifier, COUNT(DISTINCT a.device_id) AS install_count
            FROM apple_device_apps a
            JOIN apple_device d ON d.id = a.device_id
            WHERE (similarity(a.name, :query) > :threshold OR a.name ILIKE '%' || :query || '%'
                   OR similarity(a.bundle_identifier, :query) > :threshold OR a.bundle_identifier ILIKE '%' || :query || '%')
              AND d.status != 'DELETED'
              AND (CAST(:platform AS VARCHAR) IS NULL OR :platform = '' OR
                   CASE
                     WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' OR d.product_name ILIKE '%iPad%' THEN 'iOS'
                     WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                     WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                     WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                     WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                     ELSE 'unknown'
                   END = CAST(:platform AS VARCHAR))
            GROUP BY a.name, a.bundle_identifier
            ORDER BY install_count DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Object[]> searchApps(@Param("query") String query,
                              @Param("platform") String platform,
                              @Param("threshold") double threshold);

    /**
     * Find all versions of an app by bundle identifier, with device count per version.
     * Platform filter is resolved from apple_device.product_name.
     */
    @Query(value = """
            SELECT a.version, a.short_version, COUNT(DISTINCT a.device_id) AS device_count
            FROM apple_device_apps a
            JOIN apple_device d ON d.id = a.device_id
            WHERE a.bundle_identifier = :bundleIdentifier
              AND d.status != 'DELETED'
              AND (CAST(:platform AS VARCHAR) IS NULL OR :platform = '' OR
                   CASE
                     WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' OR d.product_name ILIKE '%iPad%' THEN 'iOS'
                     WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                     WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                     WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                     WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                     ELSE 'unknown'
                   END = CAST(:platform AS VARCHAR))
            GROUP BY a.version, a.short_version
            ORDER BY device_count DESC
            """, nativeQuery = true)
    List<Object[]> findVersionsByBundleIdentifier(@Param("bundleIdentifier") String bundleIdentifier,
                                                   @Param("platform") String platform);

    /**
     * Find devices that have a specific app installed, with pagination and sorting.
     * Returns device info + app info as Object[] for flexible mapping.
     * Uses COUNT(*) OVER() for total count without separate query.
     */
    @Query(value = """
            SELECT d.id AS device_id, d.serial_number, d.udid, d.product_name, d.os_version,
                   d.enrollment_type, d.status, d.agent_online, d.agent_last_seen_at, d.creation_date,
                   CASE
                     WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' OR d.product_name ILIKE '%iPad%' THEN 'iOS'
                     WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                     WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                     WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                     WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                     ELSE 'unknown'
                   END AS platform,
                   a.name AS app_name, a.version AS app_version, a.short_version AS app_short_version,
                   a.bundle_identifier, a.is_managed, a.bundle_size,
                   COUNT(*) OVER() AS total_count,
                   di.device_name
            FROM apple_device_apps a
            JOIN apple_device d ON d.id = a.device_id
            LEFT JOIN apple_device_information di ON di.id = d.id
            WHERE a.bundle_identifier = :bundleIdentifier
              AND d.status != 'DELETED'
              AND (CAST(:version AS VARCHAR) IS NULL OR :version = '' OR a.version = CAST(:version AS VARCHAR))
              AND (CAST(:platform AS VARCHAR) IS NULL OR :platform = '' OR
                   CASE
                     WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' OR d.product_name ILIKE '%iPad%' THEN 'iOS'
                     WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                     WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                     WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                     WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                     ELSE 'unknown'
                   END = CAST(:platform AS VARCHAR))
            ORDER BY
              CASE WHEN :sortBy = 'osVersion' AND :sortDesc = true THEN d.os_version END DESC,
              CASE WHEN :sortBy = 'osVersion' AND :sortDesc = false THEN d.os_version END ASC,
              CASE WHEN :sortBy = 'enrollmentType' AND :sortDesc = true THEN d.enrollment_type END DESC,
              CASE WHEN :sortBy = 'enrollmentType' AND :sortDesc = false THEN d.enrollment_type END ASC,
              CASE WHEN :sortBy = 'creationDate' AND :sortDesc = false THEN d.creation_date END ASC,
              CASE WHEN (:sortBy IS NULL OR :sortBy = '' OR :sortBy = 'creationDate') AND :sortDesc = true THEN d.creation_date END DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findDevicesByApp(@Param("bundleIdentifier") String bundleIdentifier,
                                    @Param("version") String version,
                                    @Param("platform") String platform,
                                    @Param("sortBy") String sortBy,
                                    @Param("sortDesc") boolean sortDesc,
                                    @Param("size") int size,
                                    @Param("offset") int offset);
}