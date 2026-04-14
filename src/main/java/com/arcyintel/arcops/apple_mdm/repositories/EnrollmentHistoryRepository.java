package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EnrollmentHistoryRepository extends JpaRepository<EnrollmentHistory, UUID> {

    List<EnrollmentHistory> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);

    List<EnrollmentHistory> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    @Query(value = """
            SELECT
              COUNT(*) FILTER (WHERE eh.enrolled_at IS NOT NULL
                AND (CAST(:dateFrom AS TIMESTAMP) IS NULL OR eh.enrolled_at >= CAST(:dateFrom AS TIMESTAMP))
                AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR eh.enrolled_at < CAST(:dateTo AS TIMESTAMP))) AS new_enrollments,
              COUNT(*) FILTER (WHERE eh.unenrolled_at IS NOT NULL
                AND (CAST(:dateFrom AS TIMESTAMP) IS NULL OR eh.unenrolled_at >= CAST(:dateFrom AS TIMESTAMP))
                AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR eh.unenrolled_at < CAST(:dateTo AS TIMESTAMP))) AS unenrollments
            FROM enrollment_history eh
            WHERE (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN eh.product_name ILIKE '%iPhone%' OR eh.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN eh.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN eh.product_name ILIKE '%Mac%' OR eh.product_name ILIKE '%iMac%' OR eh.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN eh.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN eh.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN eh.product_name ILIKE '%Reality%' OR eh.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            """, nativeQuery = true)
    List<Object[]> getEnrollmentCounts(
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("platform") String platform
    );

    @Query(value = """
            SELECT d.enrollment_type, COUNT(*) AS cnt
            FROM apple_device d
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
            GROUP BY d.enrollment_type ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> getEnrollmentTypeDistribution(@Param("platform") String platform);

    @Query(value = """
            WITH dates AS (
                SELECT CAST(eh.enrolled_at AS DATE) AS dt, 'enrolled' AS event_type
                FROM enrollment_history eh
                WHERE eh.enrolled_at IS NOT NULL
                  AND (CAST(:dateFrom AS TIMESTAMP) IS NULL OR eh.enrolled_at >= CAST(:dateFrom AS TIMESTAMP))
                  AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR eh.enrolled_at < CAST(:dateTo AS TIMESTAMP))
                  AND (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                                WHEN eh.product_name ILIKE '%iPhone%' OR eh.product_name ILIKE '%iPod%' THEN 'iOS'
                                                WHEN eh.product_name ILIKE '%iPad%' THEN 'iOS'
                                                WHEN eh.product_name ILIKE '%Mac%' OR eh.product_name ILIKE '%iMac%' OR eh.product_name ILIKE '%MacBook%' THEN 'macOS'
                                                WHEN eh.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                                WHEN eh.product_name ILIKE '%Watch%' THEN 'watchOS'
                                                WHEN eh.product_name ILIKE '%Reality%' OR eh.product_name ILIKE '%Vision%' THEN 'visionOS'
                                                ELSE 'unknown'
                                              END) = CAST(:platform AS VARCHAR))
                UNION ALL
                SELECT CAST(eh.unenrolled_at AS DATE) AS dt, 'unenrolled' AS event_type
                FROM enrollment_history eh
                WHERE eh.unenrolled_at IS NOT NULL
                  AND (CAST(:dateFrom AS TIMESTAMP) IS NULL OR eh.unenrolled_at >= CAST(:dateFrom AS TIMESTAMP))
                  AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR eh.unenrolled_at < CAST(:dateTo AS TIMESTAMP))
                  AND (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                                WHEN eh.product_name ILIKE '%iPhone%' OR eh.product_name ILIKE '%iPod%' THEN 'iOS'
                                                WHEN eh.product_name ILIKE '%iPad%' THEN 'iOS'
                                                WHEN eh.product_name ILIKE '%Mac%' OR eh.product_name ILIKE '%iMac%' OR eh.product_name ILIKE '%MacBook%' THEN 'macOS'
                                                WHEN eh.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                                WHEN eh.product_name ILIKE '%Watch%' THEN 'watchOS'
                                                WHEN eh.product_name ILIKE '%Reality%' OR eh.product_name ILIKE '%Vision%' THEN 'visionOS'
                                                ELSE 'unknown'
                                              END) = CAST(:platform AS VARCHAR))
            )
            SELECT
              dt,
              COUNT(*) FILTER (WHERE event_type = 'enrolled') AS enrollments,
              COUNT(*) FILTER (WHERE event_type = 'unenrolled') AS unenrollments
            FROM dates
            GROUP BY dt ORDER BY dt ASC
            """, nativeQuery = true)
    List<Object[]> getEnrollmentDailyTrend(
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("platform") String platform
    );

    @Query(value = """
            SELECT
              eh.id, eh.device_id, eh.serial_number, COALESCE(di.device_name, eh.product_name) AS product_name,
              (CASE
                 WHEN eh.product_name ILIKE '%iPhone%' OR eh.product_name ILIKE '%iPod%' THEN 'iOS'
                 WHEN eh.product_name ILIKE '%iPad%' THEN 'iOS'
                 WHEN eh.product_name ILIKE '%Mac%' OR eh.product_name ILIKE '%iMac%' OR eh.product_name ILIKE '%MacBook%' THEN 'macOS'
                 WHEN eh.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                 WHEN eh.product_name ILIKE '%Watch%' THEN 'watchOS'
                 WHEN eh.product_name ILIKE '%Reality%' OR eh.product_name ILIKE '%Vision%' THEN 'visionOS'
                 ELSE 'unknown'
               END) AS platform,
              eh.os_version, eh.enrollment_type, eh.status,
              eh.enrolled_at, eh.unenrolled_at, eh.unenroll_reason,
              COUNT(*) OVER() AS total_count
            FROM enrollment_history eh
            LEFT JOIN apple_device ad ON ad.serial_number = eh.serial_number AND ad.status <> 'DELETED'
            LEFT JOIN apple_device_information di ON di.id = ad.id
            WHERE (CAST(:dateFrom AS TIMESTAMP) IS NULL OR eh.enrolled_at >= CAST(:dateFrom AS TIMESTAMP) OR eh.unenrolled_at >= CAST(:dateFrom AS TIMESTAMP))
              AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR eh.enrolled_at < CAST(:dateTo AS TIMESTAMP) OR eh.unenrolled_at < CAST(:dateTo AS TIMESTAMP))
              AND (CAST(:enrollmentType AS VARCHAR) IS NULL OR eh.enrollment_type = CAST(:enrollmentType AS VARCHAR))
              AND (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN eh.product_name ILIKE '%iPhone%' OR eh.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN eh.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN eh.product_name ILIKE '%Mac%' OR eh.product_name ILIKE '%iMac%' OR eh.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN eh.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN eh.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN eh.product_name ILIKE '%Reality%' OR eh.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            ORDER BY
                CASE WHEN :sortBy = 'enrolledAt' AND :sortDesc = FALSE THEN eh.enrolled_at END ASC,
                CASE WHEN :sortBy = 'enrolledAt' AND :sortDesc = TRUE THEN eh.enrolled_at END DESC,
                CASE WHEN :sortBy = 'productName' AND :sortDesc = FALSE THEN eh.product_name END ASC,
                CASE WHEN :sortBy = 'productName' AND :sortDesc = TRUE THEN eh.product_name END DESC,
                CASE WHEN :sortBy = 'enrollmentType' AND :sortDesc = FALSE THEN eh.enrollment_type END ASC,
                CASE WHEN :sortBy = 'enrollmentType' AND :sortDesc = TRUE THEN eh.enrollment_type END DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> getEnrollmentHistoryItems(
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("enrollmentType") String enrollmentType,
            @Param("platform") String platform,
            @Param("sortBy") String sortBy,
            @Param("sortDesc") boolean sortDesc,
            @Param("size") int size,
            @Param("offset") int offset
    );
}
