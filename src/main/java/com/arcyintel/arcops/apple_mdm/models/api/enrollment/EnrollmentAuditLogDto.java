package com.arcyintel.arcops.apple_mdm.models.api.enrollment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentAuditLogDto {
    private UUID id;
    private Date creationDate;
    private String action;
    private String targetType;
    private String status;
    private String message;
    private String details;
    private String errorMessage;
    private String performedBy;
    private String ipAddress;
}
