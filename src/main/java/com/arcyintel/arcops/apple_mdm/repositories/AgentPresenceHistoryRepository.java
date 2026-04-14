package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AgentPresenceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentPresenceHistoryRepository extends JpaRepository<AgentPresenceHistory, UUID>, AgentPresenceHistoryRepositoryCustom {

    @Modifying
    @Query("DELETE FROM AgentPresenceHistory e WHERE e.timestamp < :before")
    int deleteByTimestampBefore(Instant before);

    /**
     * Find the most recent event for a device identifier (to calculate duration since last transition).
     */
    Optional<AgentPresenceHistory> findFirstByDeviceIdentifierOrderByTimestampDesc(
            String deviceIdentifier);

    /**
     * Find all events for a device, ordered by timestamp descending.
     */
    List<AgentPresenceHistory> findByDeviceIdOrderByTimestampDesc(UUID deviceId);

    List<AgentPresenceHistory> findByDeviceIdentifierOrderByTimestampDesc(String deviceIdentifier);

    List<AgentPresenceHistory> findByDeviceIdentifierAndTimestampBetweenOrderByTimestampAsc(
            String deviceIdentifier, Instant from, Instant to);

    /**
     * Find events for a device within a time range.
     */
    List<AgentPresenceHistory> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            UUID deviceId, Instant from, Instant to);

    /**
     * Count events for a device within a time range.
     */
    long countByDeviceIdAndTimestampBetween(UUID deviceId, Instant from, Instant to);
}
