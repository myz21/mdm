package com.arcyintel.arcops.apple_mdm.services.mappers;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.EnrollmentAuditLogDto;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentAuditLogMapper {

    public EnrollmentAuditLogDto toDto(EnrollmentAuditLog log) {
        return EnrollmentAuditLogDto.builder()
                .id(log.getId())
                .creationDate(log.getCreationDate())
                .action(log.getAction().name())
                .targetType(log.getTargetType().name())
                .status(log.getStatus().name())
                .message(log.getMessage())
                .details(log.getDetails())
                .errorMessage(log.getErrorMessage())
                .performedBy(log.getPerformedBy())
                .ipAddress(log.getIpAddress())
                .build();
    }
}
