package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppGroupRepository extends JpaRepository<AppGroup, UUID>, JpaSpecificationExecutor<AppGroup> {
    @Query("select a from AppGroup a where a.id in ?1")
    List<AppGroup> findByIdIn(Collection<UUID> ids);
}