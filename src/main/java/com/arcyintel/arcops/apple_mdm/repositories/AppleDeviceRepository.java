package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppleDeviceRepository extends JpaRepository<AppleDevice, UUID> {
    Optional<AppleDevice> findByUdid(String udid);

    Optional<AppleDevice> findBySerialNumber(String serialNumber);

    /**
     * Find device by EnrollmentID (used for User Enrollment / BYOD devices).
     */
    Optional<AppleDevice> findByEnrollmentId(String enrollmentId);

    @Query("""
            select (count(a) > 0) from AppleDevice a inner join a.appleCommands appleCommands
            where a.id = ?1 and appleCommands.commandType = ?2""")
    boolean existsByIdAndAppleCommands_CommandType(UUID id, String commandType);

    List<AppleDevice> findByAgentOnlineTrue();

    List<AppleDevice> findByAgentOnlineTrueAndAgentLastSeenAtBefore(Instant threshold);

    long countByStatusNot(String status);

    long countByIsCompliantTrueAndStatusNot(String status);

    long countByIsCompliantFalseAndStatusNot(String status);

    @Query(value = "SELECT d.os_version, COUNT(*) AS cnt, " +
            "CASE " +
            "  WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPad%' OR d.product_name ILIKE '%iPod%' THEN 'iOS' " +
            "  WHEN d.product_name ILIKE '%Mac%' THEN 'macOS' " +
            "  WHEN d.product_name ILIKE '%TV%' THEN 'tvOS' " +
            "  WHEN d.product_name ILIKE '%Vision%' THEN 'visionOS' " +
            "  ELSE 'Apple' " +
            "END AS platform " +
            "FROM apple_device d " +
            "WHERE d.status <> 'DELETED' AND d.os_version IS NOT NULL " +
            "GROUP BY d.os_version, platform ORDER BY cnt DESC LIMIT 15", nativeQuery = true)
    List<Object[]> findOsVersionDistribution();

    long countByAgentOnlineTrueAndStatusNot(String status);

    long countByAgentOnlineFalseAndStatusNot(String status);

    long countByAgentLastSeenAtIsNullAndStatusNot(String status);

    @Query(value = "SELECT d.enrollment_type, COUNT(*) AS cnt FROM apple_device d " +
            "WHERE d.status <> 'DELETED' AND d.enrollment_type IS NOT NULL " +
            "GROUP BY d.enrollment_type ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> findEnrollmentTypeDistribution();

    @Query(value = """
            SELECT CAST(d.creation_date AS DATE) AS enroll_date, COUNT(*) AS cnt
            FROM apple_device d WHERE d.status <> 'DELETED' AND d.creation_date >= :since
            GROUP BY CAST(d.creation_date AS DATE) ORDER BY enroll_date ASC
            """, nativeQuery = true)
    List<Object[]> findDailyEnrollmentCounts(@Param("since") Instant since);

    @Query(value = """
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE d.is_compliant = true) AS compliant,
              COUNT(*) FILTER (WHERE d.is_compliant = false) AS non_compliant,
              COUNT(*) FILTER (WHERE d.applied_policy IS NULL) AS no_policy
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
            """, nativeQuery = true)
    List<Object[]> getComplianceSummary(@Param("platform") String platform);

    @Query(value = """
            SELECT
              d.compliance_failures
            FROM apple_device d
            WHERE d.status <> 'DELETED'
              AND d.is_compliant = false
              AND d.compliance_failures IS NOT NULL
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
    List<String> getComplianceFailuresJson(@Param("platform") String platform);

    @Query(value = """
            SELECT
              d.id, d.serial_number, COALESCE(di.device_name, d.product_name) AS product_name,
              (CASE
                 WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                 WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                 WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                 WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                 WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                 WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                 ELSE 'unknown'
               END) AS platform,
              d.os_version, d.enrollment_type, d.is_compliant,
              d.compliance_failures,
              p.name AS policy_name,
              d.last_modified_date,
              COUNT(*) OVER() AS total_count
            FROM apple_device d
            LEFT JOIN apple_device_information di ON di.id = d.id
            LEFT JOIN policy p ON p.id = CAST(d.applied_policy->>'policyId' AS UUID) AND p.status = 'ACTIVE'
            WHERE d.status <> 'DELETED'
              AND (CAST(:complianceStatus AS VARCHAR) = 'ALL'
                   OR (CAST(:complianceStatus AS VARCHAR) = 'COMPLIANT' AND d.is_compliant = true)
                   OR (CAST(:complianceStatus AS VARCHAR) = 'NON_COMPLIANT' AND d.is_compliant = false)
                   OR (CAST(:complianceStatus AS VARCHAR) = 'NO_POLICY' AND d.applied_policy IS NULL))
              AND (CAST(:platform AS VARCHAR) IS NULL OR (CASE
                                            WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%iPad%' THEN 'iOS'
                                            WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
                                            WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                                            WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
                                            WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
                                            ELSE 'unknown'
                                          END) = CAST(:platform AS VARCHAR))
            ORDER BY
                CASE WHEN :sortBy = 'productName' AND :sortDesc = FALSE THEN d.product_name END ASC,
                CASE WHEN :sortBy = 'productName' AND :sortDesc = TRUE THEN d.product_name END DESC,
                CASE WHEN :sortBy = 'osVersion' AND :sortDesc = FALSE THEN d.os_version END ASC,
                CASE WHEN :sortBy = 'osVersion' AND :sortDesc = TRUE THEN d.os_version END DESC,
                CASE WHEN :sortBy = 'enrollmentType' AND :sortDesc = FALSE THEN d.enrollment_type END ASC,
                CASE WHEN :sortBy = 'enrollmentType' AND :sortDesc = TRUE THEN d.enrollment_type END DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> getComplianceDevices(
            @Param("complianceStatus") String complianceStatus,
            @Param("platform") String platform,
            @Param("sortBy") String sortBy,
            @Param("sortDesc") boolean sortDesc,
            @Param("size") int size,
            @Param("offset") int offset
    );
}