package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AgentActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentActivityLogRepository extends JpaRepository<AgentActivityLog, UUID> {

    List<AgentActivityLog> findByDevice_IdOrderByCreatedAtDesc(UUID deviceId, Pageable pageable);

    Optional<AgentActivityLog> findBySessionIdAndStatus(String sessionId, String status);
}
