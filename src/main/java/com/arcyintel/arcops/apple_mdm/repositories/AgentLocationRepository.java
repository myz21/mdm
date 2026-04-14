package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AgentLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentLocationRepository extends JpaRepository<AgentLocation, UUID>, AgentLocationRepositoryCustom {

    @Modifying
    @Query("DELETE FROM AgentLocation e WHERE e.deviceCreatedAt < :before")
    int deleteByDeviceCreatedAtBefore(Instant before);

    List<AgentLocation> findByDeviceIdOrderByDeviceCreatedAtDesc(UUID deviceId);

    List<AgentLocation> findByDeviceIdentifierOrderByDeviceCreatedAtDesc(String deviceIdentifier);

    Optional<AgentLocation> findFirstByDeviceIdentifierOrderByDeviceCreatedAtDesc(String deviceIdentifier);

    List<AgentLocation> findByDeviceIdAndDeviceCreatedAtBetweenOrderByDeviceCreatedAtAsc(
            UUID deviceId, Instant from, Instant to);

    long countByDeviceIdAndDeviceCreatedAtBetween(UUID deviceId, Instant from, Instant to);

    Optional<AgentLocation> findFirstByDeviceIdentifierAndSourceOrderByDeviceCreatedAtDesc(
            String deviceIdentifier, String source);

    List<AgentLocation> findByDeviceIdentifierAndDeviceCreatedAtBetweenOrderByDeviceCreatedAtAsc(
            String deviceIdentifier, Instant from, Instant to);

    @Query(value = """
            SELECT l.* FROM agent_location l
            INNER JOIN (
                SELECT device_identifier, MAX(server_received_at) as max_time
                FROM agent_location
                GROUP BY device_identifier
            ) latest ON l.device_identifier = latest.device_identifier
                    AND l.server_received_at = latest.max_time
            """, nativeQuery = true)
    List<AgentLocation> findLatestPerDevice();
}
