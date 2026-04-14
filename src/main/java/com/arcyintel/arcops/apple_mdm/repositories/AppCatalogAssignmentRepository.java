package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppCatalogAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AppCatalogAssignmentRepository extends JpaRepository<AppCatalogAssignment, UUID> {

    List<AppCatalogAssignment> findByAppGroup_Id(UUID appGroupId);

    @Query("SELECT a FROM AppCatalogAssignment a WHERE a.targetType = :targetType AND a.targetId = :targetId")
    List<AppCatalogAssignment> findByTargetTypeAndTargetId(
            @Param("targetType") String targetType, @Param("targetId") UUID targetId);

    @Query("SELECT a FROM AppCatalogAssignment a WHERE " +
            "(a.targetType = 'ACCOUNT' AND a.targetId = :accountId) OR " +
            "(a.targetType = 'ACCOUNT_GROUP' AND a.targetId IN :accountGroupIds)")
    List<AppCatalogAssignment> findByAccountOrGroups(
            @Param("accountId") UUID accountId, @Param("accountGroupIds") List<UUID> accountGroupIds);
}
