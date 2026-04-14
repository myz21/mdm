package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class EnrollmentHistoryItemDto {

    private String id;
    private String deviceId;
    private String serialNumber;
    private String productName;
    private String platform;
    private String osVersion;
    private String enrollmentType;
    private String status;
    private Instant enrolledAt;
    private Instant unenrolledAt;
    private String unenrollReason;
}
