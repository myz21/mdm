package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AbmProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AbmProfileRepository extends JpaRepository<AbmProfile, UUID> {
    Optional<AbmProfile> findByProfileUuid(String profileUuid);
}