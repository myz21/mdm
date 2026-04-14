package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog.AuditAction;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog.AuditTargetType;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.EnrollmentAuditLogDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;

public interface EnrollmentAuditService {

    /**
     * Log a successful operation
     */
    void logSuccess(AuditAction action, AuditTargetType targetType, String message, String details, HttpServletRequest request);

    /**
     * Log a failed operation
     */
    void logFailure(AuditAction action, AuditTargetType targetType, String message, String errorMessage, HttpServletRequest request);

    /**
     * Get paginated audit logs
     */
    Page<EnrollmentAuditLogDto> getLogs(int page, int size);

    /**
     * Get paginated audit logs filtered by target type
     */
    Page<EnrollmentAuditLogDto> getLogsByTargetType(AuditTargetType targetType, int page, int size);
}
