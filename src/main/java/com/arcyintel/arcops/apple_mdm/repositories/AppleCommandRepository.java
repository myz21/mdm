package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppleCommand;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppleCommandRepository extends JpaRepository<AppleCommand, UUID>, AppleCommandRepositoryCustom {

    @Modifying
    @Query(value = "UPDATE apple_command SET apple_device_udid = NULL WHERE apple_device_udid = :udid", nativeQuery = true)
    void detachCommandsFromDevice(@Param("udid") String udid);

    Optional<AppleCommand> findByCommandUUID(String commandUUID);

    @Query("SELECT c FROM AppleCommand c WHERE c.status IN ('PENDING', 'EXECUTING') ORDER BY c.requestTime ASC")
    List<AppleCommand> findPendingAndExecutingCommands();

    @Query("SELECT c FROM AppleCommand c WHERE c.appleDeviceUdid = :udid AND c.status IN ('PENDING', 'EXECUTING') ORDER BY c.requestTime ASC")
    List<AppleCommand> findPendingAndExecutingCommandsByUdid(String udid);

    /**
     * Find commands for a device, ordered by request time descending (most recent first).
     */
    @Query("SELECT c FROM AppleCommand c WHERE c.appleDevice.id = :deviceId ORDER BY c.requestTime DESC")
    List<AppleCommand> findByDeviceIdOrderByRequestTimeDesc(UUID deviceId, Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT c FROM AppleCommand c ORDER BY c.requestTime DESC")
    List<AppleCommand> findRecentCommands(Pageable pageable);

    // ─── Bulk Command Queries ───

    @Query(value = """
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                   COUNT(*) FILTER (WHERE status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed,
                   COUNT(*) FILTER (WHERE status IN ('PENDING', 'EXECUTING')) AS pending,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (completion_time - request_time)) * 1000)
                       FILTER (WHERE status = 'COMPLETED' AND completion_time IS NOT NULL AND request_time IS NOT NULL), 0) AS avg_ms,
                   COALESCE(MIN(EXTRACT(EPOCH FROM (completion_time - request_time)) * 1000)
                       FILTER (WHERE status = 'COMPLETED' AND completion_time IS NOT NULL AND request_time IS NOT NULL), 0) AS min_ms,
                   COALESCE(MAX(EXTRACT(EPOCH FROM (completion_time - request_time)) * 1000)
                       FILTER (WHERE status = 'COMPLETED' AND completion_time IS NOT NULL AND request_time IS NOT NULL), 0) AS max_ms
            FROM apple_command WHERE bulk_command_id = :bulkCommandId
            """, nativeQuery = true)
    List<Object[]> getBulkCommandSummary(@Param("bulkCommandId") UUID bulkCommandId);

    @Query(value = """
            SELECT COALESCE(failure_reason, 'Unknown') AS reason, COUNT(*) AS count
            FROM apple_command
            WHERE bulk_command_id = :bulkCommandId AND status IN ('FAILED', 'ERROR', 'TIMED_OUT') AND failure_reason IS NOT NULL
            GROUP BY failure_reason ORDER BY count DESC LIMIT 10
            """, nativeQuery = true)
    List<Object[]> getBulkCommandFailureReasons(@Param("bulkCommandId") UUID bulkCommandId);

    @Query(value = """
            SELECT TO_CHAR(completion_time, 'YYYY-MM-DD"T"HH24:MI') AS minute,
                   COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                   COUNT(*) FILTER (WHERE status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed
            FROM apple_command
            WHERE bulk_command_id = :bulkCommandId AND completion_time IS NOT NULL
            GROUP BY minute ORDER BY minute
            """, nativeQuery = true)
    List<Object[]> getBulkCommandTimeline(@Param("bulkCommandId") UUID bulkCommandId);

    @Modifying
    @Query(value = """
            UPDATE apple_command SET bulk_command_id = :bulkCommandId
            WHERE apple_device_udid IN (:udids)
              AND bulk_command_id IS NULL
              AND request_time >= now() - INTERVAL '30 seconds'
            """, nativeQuery = true)
    int tagCommandsWithBulkId(@Param("bulkCommandId") UUID bulkCommandId, @Param("udids") List<String> udids);

    @Query(value = """
            SELECT CAST(u.request_time AS DATE) as cmd_date,
                   COUNT(*) as total,
                   COUNT(*) FILTER (WHERE u.status = 'COMPLETED') as completed,
                   COUNT(*) FILTER (WHERE u.status IN ('FAILED', 'ERROR', 'TIMED_OUT')) as failed
            FROM (
                SELECT c.request_time, c.status FROM apple_command c WHERE c.request_time >= :since
                UNION ALL
                SELECT agc.request_time,
                       CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status
                FROM agent_command agc
                WHERE agc.request_time >= :since
                  AND agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
            ) u
            GROUP BY CAST(u.request_time AS DATE)
            ORDER BY cmd_date ASC
            """, nativeQuery = true)
    List<Object[]> findDailyCommandCounts(@Param("since") Instant since);

    @Query(value = """
            SELECT u.command_type, COUNT(*) AS cnt
            FROM (
                SELECT c.command_type FROM apple_command c
                UNION ALL
                SELECT agc.command_type FROM agent_command agc
                WHERE agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
            ) u
            GROUP BY u.command_type ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> findCommandTypeDistribution();

    @Query(value = """
            SELECT u.command_type, COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE u.status = 'COMPLETED') AS completed,
                   COUNT(*) FILTER (WHERE u.status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed
            FROM (
                SELECT c.command_type, c.status FROM apple_command c
                UNION ALL
                SELECT agc.command_type,
                       CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status
                FROM agent_command agc
                WHERE agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
            ) u
            GROUP BY u.command_type ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> findCommandSuccessRatesByType();

    @Query(value = """
            SELECT
              COUNT(*) AS total_commands,
              COUNT(*) FILTER (WHERE u.status = 'COMPLETED') AS completed_commands,
              COUNT(*) FILTER (WHERE u.status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed_commands,
              COUNT(*) FILTER (WHERE u.status = 'PENDING') AS pending_commands,
              COUNT(*) FILTER (WHERE u.status = 'EXECUTING') AS executing_commands,
              COUNT(*) FILTER (WHERE u.status = 'CANCELED') AS canceled_commands,
              AVG(u.exec_duration_ms) FILTER (WHERE u.status = 'COMPLETED' AND u.exec_duration_ms IS NOT NULL) AS avg_execution_time_ms
            FROM (
                SELECT ac.status, ac.request_time, ac.command_type,
                       EXTRACT(EPOCH FROM (ac.completion_time - ac.execution_time)) * 1000 AS exec_duration_ms,
                       (CASE
                         WHEN ad.product_name ILIKE '%iPhone%' OR ad.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%Mac%' OR ad.product_name ILIKE '%iMac%' OR ad.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad.product_name ILIKE '%Reality%' OR ad.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform
                FROM apple_command ac
                JOIN apple_device ad ON ad.udid = ac.apple_device_udid
                WHERE ad.status != 'DELETED'
                UNION ALL
                SELECT
                       CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status,
                       agc.request_time, agc.command_type,
                       EXTRACT(EPOCH FROM (agc.response_time - agc.request_time)) * 1000 AS exec_duration_ms,
                       (CASE
                         WHEN ad2.product_name ILIKE '%iPhone%' OR ad2.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%Mac%' OR ad2.product_name ILIKE '%iMac%' OR ad2.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad2.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad2.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad2.product_name ILIKE '%Reality%' OR ad2.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform
                FROM agent_command agc
                JOIN apple_device ad2 ON ad2.udid = agc.device_identifier
                WHERE ad2.status != 'DELETED'
                  AND agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
            ) u
            WHERE (CAST(:dateFrom AS TIMESTAMP) IS NULL OR u.request_time >= CAST(:dateFrom AS TIMESTAMP))
              AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR u.request_time < CAST(:dateTo AS TIMESTAMP))
              AND (CAST(:commandType AS VARCHAR) IS NULL OR u.command_type = CAST(:commandType AS VARCHAR))
              AND (CAST(:status AS VARCHAR) IS NULL OR u.status = CAST(:status AS VARCHAR))
              AND (CAST(:platform AS VARCHAR) IS NULL OR u.platform = CAST(:platform AS VARCHAR))
            """, nativeQuery = true)
    List<Object[]> getCommandSummary(
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("commandType") String commandType,
            @Param("status") String status,
            @Param("platform") String platform
    );

    @Query(value = """
            SELECT
              CAST(u.request_time AS DATE) AS cmd_date,
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE u.status = 'COMPLETED') AS completed,
              COUNT(*) FILTER (WHERE u.status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed,
              COUNT(*) FILTER (WHERE u.status = 'CANCELED') AS canceled
            FROM (
                SELECT ac.status, ac.request_time, ac.command_type,
                       (CASE
                         WHEN ad.product_name ILIKE '%iPhone%' OR ad.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%Mac%' OR ad.product_name ILIKE '%iMac%' OR ad.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad.product_name ILIKE '%Reality%' OR ad.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform
                FROM apple_command ac
                JOIN apple_device ad ON ad.udid = ac.apple_device_udid
                WHERE ad.status != 'DELETED'
                UNION ALL
                SELECT
                       CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status,
                       agc.request_time, agc.command_type,
                       (CASE
                         WHEN ad2.product_name ILIKE '%iPhone%' OR ad2.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%Mac%' OR ad2.product_name ILIKE '%iMac%' OR ad2.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad2.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad2.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad2.product_name ILIKE '%Reality%' OR ad2.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform
                FROM agent_command agc
                JOIN apple_device ad2 ON ad2.udid = agc.device_identifier
                WHERE ad2.status != 'DELETED'
                  AND agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
            ) u
            WHERE (CAST(:dateFrom AS TIMESTAMP) IS NULL OR u.request_time >= CAST(:dateFrom AS TIMESTAMP))
              AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR u.request_time < CAST(:dateTo AS TIMESTAMP))
              AND (CAST(:commandType AS VARCHAR) IS NULL OR u.command_type = CAST(:commandType AS VARCHAR))
              AND (CAST(:platform AS VARCHAR) IS NULL OR u.platform = CAST(:platform AS VARCHAR))
            GROUP BY CAST(u.request_time AS DATE)
            ORDER BY cmd_date ASC
            """, nativeQuery = true)
    List<Object[]> getCommandDailyTrend(
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("commandType") String commandType,
            @Param("platform") String platform
    );

    @Query(value = """
            SELECT
              u.command_type,
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE u.status = 'COMPLETED') AS completed,
              COUNT(*) FILTER (WHERE u.status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed,
              COUNT(*) FILTER (WHERE u.status = 'CANCELED') AS canceled,
              COUNT(*) FILTER (WHERE u.status IN ('PENDING', 'EXECUTING')) AS pending,
              AVG(u.exec_duration_ms) FILTER (WHERE u.status = 'COMPLETED' AND u.exec_duration_ms IS NOT NULL) AS avg_execution_time_ms
            FROM (
                SELECT ac.command_type, ac.status, ac.request_time,
                       EXTRACT(EPOCH FROM (ac.completion_time - ac.execution_time)) * 1000 AS exec_duration_ms,
                       (CASE
                         WHEN ad.product_name ILIKE '%iPhone%' OR ad.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%Mac%' OR ad.product_name ILIKE '%iMac%' OR ad.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad.product_name ILIKE '%Reality%' OR ad.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform
                FROM apple_command ac
                JOIN apple_device ad ON ad.udid = ac.apple_device_udid
                WHERE ad.status != 'DELETED'
                UNION ALL
                SELECT agc.command_type,
                       CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status,
                       agc.request_time,
                       EXTRACT(EPOCH FROM (agc.response_time - agc.request_time)) * 1000 AS exec_duration_ms,
                       (CASE
                         WHEN ad2.product_name ILIKE '%iPhone%' OR ad2.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%Mac%' OR ad2.product_name ILIKE '%iMac%' OR ad2.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad2.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad2.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad2.product_name ILIKE '%Reality%' OR ad2.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform
                FROM agent_command agc
                JOIN apple_device ad2 ON ad2.udid = agc.device_identifier
                WHERE ad2.status != 'DELETED'
                  AND agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
            ) u
            WHERE (CAST(:dateFrom AS TIMESTAMP) IS NULL OR u.request_time >= CAST(:dateFrom AS TIMESTAMP))
              AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR u.request_time < CAST(:dateTo AS TIMESTAMP))
              AND (CAST(:platform AS VARCHAR) IS NULL OR u.platform = CAST(:platform AS VARCHAR))
            GROUP BY u.command_type
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> getCommandTypeBreakdown(
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("platform") String platform
    );

    @Query(value = """
            SELECT u.id, u.command_type, u.status,
                   u.device_id, u.serial_number, u.product_name, u.platform,
                   u.request_time, u.execution_time, u.completion_time,
                   u.execution_duration_ms,
                   u.failure_reason,
                   u.policy_id, u.policy_name,
                   COUNT(*) OVER() AS total_count,
                   u.bulk_command_id,
                   u.created_by
            FROM (
                SELECT ac.id, ac.command_type, ac.status,
                       ad.id AS device_id, ad.serial_number, COALESCE(di.device_name, ad.product_name) AS product_name,
                       (CASE
                         WHEN ad.product_name ILIKE '%iPhone%' OR ad.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad.product_name ILIKE '%Mac%' OR ad.product_name ILIKE '%iMac%' OR ad.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad.product_name ILIKE '%Reality%' OR ad.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform,
                       ac.request_time, ac.execution_time, ac.completion_time,
                       CASE WHEN ac.execution_time IS NOT NULL AND ac.completion_time IS NOT NULL
                         THEN EXTRACT(EPOCH FROM (ac.completion_time - ac.execution_time)) * 1000
                         ELSE NULL END AS execution_duration_ms,
                       ac.failure_reason,
                       ac.policy_id, p.name AS policy_name,
                       ac.bulk_command_id,
                       ac.created_by
                FROM apple_command ac
                JOIN apple_device ad ON ad.udid = ac.apple_device_udid
                LEFT JOIN apple_device_information di ON di.id = ad.id
                LEFT JOIN policy p ON p.id = ac.policy_id AND p.status = 'ACTIVE'
                WHERE ad.status != 'DELETED'
                UNION ALL
                SELECT agc.id, agc.command_type,
                       CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status,
                       ad2.id AS device_id, ad2.serial_number, COALESCE(di2.device_name, ad2.product_name) AS product_name,
                       (CASE
                         WHEN ad2.product_name ILIKE '%iPhone%' OR ad2.product_name ILIKE '%iPod%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%iPad%' THEN 'iOS'
                         WHEN ad2.product_name ILIKE '%Mac%' OR ad2.product_name ILIKE '%iMac%' OR ad2.product_name ILIKE '%MacBook%' THEN 'macOS'
                         WHEN ad2.product_name ILIKE '%Apple TV%' THEN 'tvOS'
                         WHEN ad2.product_name ILIKE '%Watch%' THEN 'watchOS'
                         WHEN ad2.product_name ILIKE '%Reality%' OR ad2.product_name ILIKE '%Vision%' THEN 'visionOS'
                         ELSE 'unknown'
                       END) AS platform,
                       agc.request_time,
                       agc.request_time AS execution_time,
                       agc.response_time AS completion_time,
                       CASE WHEN agc.response_time IS NOT NULL
                         THEN EXTRACT(EPOCH FROM (agc.response_time - agc.request_time)) * 1000
                         ELSE NULL END AS execution_duration_ms,
                       agc.error_message AS failure_reason,
                       CAST(NULL AS UUID) AS policy_id, CAST(NULL AS VARCHAR) AS policy_name,
                       CAST(NULL AS UUID) AS bulk_command_id,
                       agc.created_by
                FROM agent_command agc
                JOIN apple_device ad2 ON ad2.udid = agc.device_identifier
                LEFT JOIN apple_device_information di2 ON di2.id = ad2.id
                WHERE ad2.status != 'DELETED'
                  AND agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
            ) u
            WHERE (CAST(:dateFrom AS TIMESTAMP) IS NULL OR u.request_time >= CAST(:dateFrom AS TIMESTAMP))
              AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR u.request_time < CAST(:dateTo AS TIMESTAMP))
              AND (CAST(:commandType AS VARCHAR) IS NULL OR u.command_type = CAST(:commandType AS VARCHAR))
              AND (CAST(:status AS VARCHAR) IS NULL OR u.status = CAST(:status AS VARCHAR))
              AND (CAST(:platform AS VARCHAR) IS NULL OR u.platform = CAST(:platform AS VARCHAR))
            ORDER BY
                CASE WHEN :sortBy = 'requestTime' AND :sortDesc = FALSE THEN u.request_time END ASC,
                CASE WHEN :sortBy = 'requestTime' AND :sortDesc = TRUE THEN u.request_time END DESC,
                CASE WHEN :sortBy = 'commandType' AND :sortDesc = FALSE THEN u.command_type END ASC,
                CASE WHEN :sortBy = 'commandType' AND :sortDesc = TRUE THEN u.command_type END DESC,
                CASE WHEN :sortBy = 'status' AND :sortDesc = FALSE THEN u.status END ASC,
                CASE WHEN :sortBy = 'status' AND :sortDesc = TRUE THEN u.status END DESC,
                CASE WHEN :sortBy = 'executionDurationMs' AND :sortDesc = FALSE THEN u.execution_duration_ms END ASC,
                CASE WHEN :sortBy = 'executionDurationMs' AND :sortDesc = TRUE THEN u.execution_duration_ms END DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> getCommandReportItems(
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("commandType") String commandType,
            @Param("status") String status,
            @Param("platform") String platform,
            @Param("sortBy") String sortBy,
            @Param("sortDesc") boolean sortDesc,
            @Param("size") int size,
            @Param("offset") int offset
    );

}
