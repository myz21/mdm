package com.arcyintel.arcops.apple_mdm.models.api.report;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommandDailyTrendDto {

    private LocalDate date;
    private long total;
    private long completed;
    private long failed;
    private long canceled;
}
