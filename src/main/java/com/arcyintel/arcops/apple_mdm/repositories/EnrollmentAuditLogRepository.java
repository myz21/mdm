package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EnrollmentAuditLogRepository extends JpaRepository<EnrollmentAuditLog, UUID> {

    Page<EnrollmentAuditLog> findAllByOrderByCreationDateDesc(Pageable pageable);

    Page<EnrollmentAuditLog> findByTargetTypeOrderByCreationDateDesc(
            EnrollmentAuditLog.AuditTargetType targetType,
            Pageable pageable
    );

    Page<EnrollmentAuditLog> findByActionOrderByCreationDateDesc(
            EnrollmentAuditLog.AuditAction action,
            Pageable pageable
    );
}
