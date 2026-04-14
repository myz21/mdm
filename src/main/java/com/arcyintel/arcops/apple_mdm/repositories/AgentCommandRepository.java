package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AgentCommand;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentCommandRepository extends JpaRepository<AgentCommand, UUID> {

    Optional<AgentCommand> findByCommandUuid(String commandUuid);

    List<AgentCommand> findByDeviceIdentifierOrderByRequestTimeDesc(String deviceIdentifier, Pageable pageable);

    List<AgentCommand> findByStatus(String status);
}
