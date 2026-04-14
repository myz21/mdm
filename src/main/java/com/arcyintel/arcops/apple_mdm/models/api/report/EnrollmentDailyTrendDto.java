package com.arcyintel.arcops.apple_mdm.models.api.report;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnrollmentDailyTrendDto {

    private LocalDate date;
    private long enrollments;
    private long unenrollments;
}
