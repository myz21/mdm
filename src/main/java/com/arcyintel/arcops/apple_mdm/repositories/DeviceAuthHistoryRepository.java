package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.DeviceAuthHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeviceAuthHistoryRepository extends JpaRepository<DeviceAuthHistory, UUID> {
    List<DeviceAuthHistory> findByDevice_IdOrderByCreatedAtDesc(UUID deviceId);
    Page<DeviceAuthHistory> findByDevice_Id(UUID deviceId, Pageable pageable);

    // Count by event type
    @Query(nativeQuery = true, value = "SELECT event_type, COUNT(*) as cnt FROM device_auth_history WHERE created_at >= CAST(:from AS timestamp) GROUP BY event_type")
    List<Object[]> countByEventType(@Param("from") String from);

    // Unique devices
    @Query(nativeQuery = true, value = "SELECT COUNT(DISTINCT device_identifier) FROM device_auth_history WHERE created_at >= CAST(:from AS timestamp) AND event_type = 'SIGN_IN'")
    long countUniqueDevices(@Param("from") String from);

    // Top failed IPs
    @Query(nativeQuery = true, value = "SELECT ip_address, COUNT(*) as cnt FROM device_auth_history WHERE event_type = 'SIGN_IN_FAILED' AND created_at >= CAST(:from AS timestamp) AND ip_address IS NOT NULL GROUP BY ip_address ORDER BY cnt DESC LIMIT :limit")
    List<Object[]> findTopFailedIps(@Param("from") String from, @Param("limit") int limit);

    // Daily trend
    @Query(nativeQuery = true, value = "SELECT CAST(created_at AS date) as day, event_type, COUNT(*) as cnt FROM device_auth_history WHERE created_at >= CAST(:from AS timestamp) GROUP BY day, event_type ORDER BY day")
    List<Object[]> findDailyTrend(@Param("from") String from);

    // Recent events (all types, paginated)
    Page<DeviceAuthHistory> findByCreatedAtAfterOrderByCreatedAtDesc(Instant after, Pageable pageable);

    // ─── Filterable Auth Log (paginated, with device info JOIN) ──────────────────────────

    @Query(nativeQuery = true, value = "SELECT dah.id, dah.username, dah.device_identifier, dah.event_type, " +
        "dah.ip_address, dah.agent_version, dah.failure_reason, dah.created_at, dah.auth_source, " +
        "COALESCE(ad.product_name, '') as product_name, " +
        "COALESCE(adi.model_name, '') as model_name, COALESCE(adi.model, '') as model " +
        "FROM device_auth_history dah " +
        "LEFT JOIN apple_device ad ON (dah.device_id = ad.id OR (dah.device_id IS NULL AND dah.device_identifier IS NOT NULL AND dah.device_identifier != 'unknown' AND dah.device_identifier = ad.udid)) " +
        "LEFT JOIN apple_device_information adi ON ad.id = adi.id " +
        "WHERE (:username IS NULL OR dah.username ILIKE '%' || :username || '%') " +
        "AND (:ip IS NULL OR dah.ip_address ILIKE '%' || :ip || '%') " +
        "AND (:eventType IS NULL OR dah.event_type = :eventType) " +
        "AND (:dateFrom IS NULL OR dah.created_at >= CAST(:dateFrom AS timestamp)) " +
        "AND (:dateTo IS NULL OR dah.created_at <= CAST(:dateTo AS timestamp)) " +
        "ORDER BY dah.created_at DESC",
        countQuery = "SELECT COUNT(*) FROM device_auth_history dah " +
        "WHERE (:username IS NULL OR dah.username ILIKE '%' || :username || '%') " +
        "AND (:ip IS NULL OR dah.ip_address ILIKE '%' || :ip || '%') " +
        "AND (:eventType IS NULL OR dah.event_type = :eventType) " +
        "AND (:dateFrom IS NULL OR dah.created_at >= CAST(:dateFrom AS timestamp)) " +
        "AND (:dateTo IS NULL OR dah.created_at <= CAST(:dateTo AS timestamp))")
    Page<Object[]> findFilteredWithDevice(
        @Param("username") String username,
        @Param("ip") String ip,
        @Param("eventType") String eventType,
        @Param("dateFrom") String dateFrom,
        @Param("dateTo") String dateTo,
        Pageable pageable);
}
