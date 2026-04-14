package com.arcyintel.arcops.apple_mdm.services.report;

import com.arcyintel.arcops.apple_mdm.models.api.report.*;
import org.springframework.data.web.PagedModel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReportService {

    List<AppSearchResultDto> searchApps(String query, String platform);

    List<AppVersionDto> findVersionsByBundleIdentifier(String bundleIdentifier, String platform);

    PagedModel<AppDeviceReportDto> findDevicesByApp(String bundleIdentifier, AppDeviceReportRequestDto request);

    CommandReportSummaryDto getCommandReportSummary(String dateFrom, String dateTo, String commandType, String status, String platform);

    List<CommandDailyTrendDto> getCommandDailyTrend(String dateFrom, String dateTo, String commandType, String platform);

    List<CommandTypeBreakdownDto> getCommandTypeBreakdown(String dateFrom, String dateTo, String platform);

    PagedModel<CommandReportItemDto> getCommandReportItems(CommandReportRequestDto request);

    // Command User Stats
    List<Map<String, Object>> getCommandUserStats(String dateFrom, String dateTo, int limit);

    // Bulk Command Detail
    Map<String, Object> getBulkCommandDetail(UUID bulkCommandId);

    // Bulk Command Devices
    PagedModel<Map<String, Object>> getBulkCommandDevices(UUID bulkCommandId, int page, int size, String status, String search);

    // Device Activity
    Map<String, Object> queryDeviceActivity(Map<String, Object> request);

    // Map Analysis
    Map<String, Object> queryLocationsByArea(Map<String, Object> request);

    // Fleet Health
    FleetHealthSummaryDto getFleetHealthSummary(String platform);

    PagedModel<FleetHealthDeviceDto> getFleetHealthDevices(FleetHealthDeviceRequestDto request);

    // Compliance
    ComplianceSummaryDto getComplianceSummary(String platform);

    PagedModel<ComplianceDeviceDto> getComplianceDevices(ComplianceDeviceRequestDto request);

    // Enrollment
    EnrollmentSummaryDto getEnrollmentSummary(String dateFrom, String dateTo, String platform);

    List<EnrollmentDailyTrendDto> getEnrollmentDailyTrend(String dateFrom, String dateTo, String platform);

    PagedModel<EnrollmentHistoryItemDto> getEnrollmentHistory(EnrollmentHistoryRequestDto request);

    // Security Posture
    SecuritySummaryDto getSecuritySummary(String platform);

    PagedModel<SecurityDeviceDto> getSecurityDevices(SecurityDeviceRequestDto request);
}
