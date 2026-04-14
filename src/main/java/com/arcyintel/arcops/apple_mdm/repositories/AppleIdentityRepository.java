package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppleIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppleIdentityRepository extends JpaRepository<AppleIdentity, UUID> {

    Optional<AppleIdentity> findByEmail(String email);

    Optional<AppleIdentity> findByExternalId(String externalId);

    Optional<AppleIdentity> findByUsername(String username);
}
