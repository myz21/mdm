package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.BulkCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BulkCommandRepository extends JpaRepository<BulkCommand, UUID> {
    Page<BulkCommand> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
