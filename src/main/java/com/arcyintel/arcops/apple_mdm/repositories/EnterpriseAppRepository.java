package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnterpriseAppRepository
        extends JpaRepository<EnterpriseApp, UUID>,
                JpaSpecificationExecutor<EnterpriseApp> {

    Optional<EnterpriseApp> findByBundleIdAndVersion(String bundleId, String version);

    Optional<EnterpriseApp> findByBundleId(String bundleId);

    List<EnterpriseApp> findAllByBundleIdIn(List<String> bundleIds);
}
