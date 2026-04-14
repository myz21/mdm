package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppleAccountRepository extends JpaRepository<AppleAccount, UUID>, JpaSpecificationExecutor<AppleAccount> {
    Optional<AppleAccount> findByManagedAppleId(String managedAppleId);
    Optional<AppleAccount> findByEmail(String email);
    Optional<AppleAccount> findByUsername(String username);
    List<AppleAccount> findByStatus(String status);
    Optional<AppleAccount> findByIdentityId(UUID identityId);
}