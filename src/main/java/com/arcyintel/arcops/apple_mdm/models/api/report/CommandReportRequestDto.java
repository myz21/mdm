package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CommandReportRequestDto {

    private String dateFrom; // ISO date string
    private String dateTo;   // ISO date string
    private String commandType;
    private String status;
    private String platform;

    // Pagination fields
    private int page = 0;
    private int size = 25;
    private String sortBy = "requestTime";
    private boolean sortDesc = true;
}
