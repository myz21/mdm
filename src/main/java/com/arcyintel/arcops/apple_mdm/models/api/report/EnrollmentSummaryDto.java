package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EnrollmentSummaryDto {

    private long totalActiveDevices;
    private long newEnrollments;
    private long unenrollments;
    private long netChange;
    private List<EnrollmentTypeDistributionDto> typeDistribution;

    @Data
    @Builder
    public static class EnrollmentTypeDistributionDto {
        private String enrollmentType;
        private long count;
    }
}
