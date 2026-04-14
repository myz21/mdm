package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.report.*;
import com.arcyintel.arcops.apple_mdm.services.report.ReportService;
import com.arcyintel.arcops.commons.license.RequiresFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Report and analytics endpoints")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/apps/search")
    @Operation(summary = "Search apps by name or bundle identifier", description = "Fuzzy search using pg_trgm similarity with optional platform filter")
    @RequiresFeature("REPORT_APP")
    public ResponseEntity<List<AppSearchResultDto>> searchApps(
            @RequestParam("q") String query,
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.searchApps(query, platform));
    }

    @GetMapping("/apps/{bundleIdentifier}/versions")
    @Operation(summary = "Get versions of an app", description = "Returns all versions of the specified app with device count per version")
    @RequiresFeature("REPORT_APP")
    public ResponseEntity<List<AppVersionDto>> getAppVersions(
            @PathVariable String bundleIdentifier,
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.findVersionsByBundleIdentifier(bundleIdentifier, platform));
    }

    @PostMapping("/apps/{bundleIdentifier}/devices")
    @Operation(summary = "Get devices with a specific app", description = "Returns paginated list of devices that have the specified app installed")
    @RequiresFeature("REPORT_APP")
    public ResponseEntity<PagedModel<AppDeviceReportDto>> getAppDevices(
            @PathVariable String bundleIdentifier,
            @RequestBody AppDeviceReportRequestDto request) {
        return ResponseEntity.ok(reportService.findDevicesByApp(bundleIdentifier, request));
    }

    @GetMapping("/commands/summary")
    @Operation(summary = "Get command report summary", description = "Provides statistics on MDM commands based on filter criteria.")
    @RequiresFeature("REPORT_COMMAND")
    public ResponseEntity<CommandReportSummaryDto> getCommandReportSummary(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "commandType", required = false) String commandType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getCommandReportSummary(dateFrom, dateTo, commandType, status, platform));
    }

    @GetMapping("/commands/trend")
    @Operation(summary = "Get daily command trend", description = "Provides daily counts of total, completed, failed, and canceled commands.")
    @RequiresFeature("REPORT_COMMAND")
    public ResponseEntity<List<CommandDailyTrendDto>> getCommandDailyTrend(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "commandType", required = false) String commandType,
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getCommandDailyTrend(dateFrom, dateTo, commandType, platform));
    }

    @GetMapping("/commands/types")
    @Operation(summary = "Get command type breakdown", description = "Provides a breakdown of command statistics grouped by command type.")
    @RequiresFeature("REPORT_COMMAND")
    public ResponseEntity<List<CommandTypeBreakdownDto>> getCommandTypeBreakdown(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getCommandTypeBreakdown(dateFrom, dateTo, platform));
    }

    @PostMapping("/commands/list")
    @Operation(summary = "Get paginated list of commands", description = "Returns a paginated list of commands based on filter criteria.")
    @RequiresFeature("REPORT_COMMAND")
    public ResponseEntity<PagedModel<CommandReportItemDto>> getCommandReportItems(
            @RequestBody CommandReportRequestDto request) {
        return ResponseEntity.ok(reportService.getCommandReportItems(request));
    }

    @GetMapping("/commands/user-stats")
    @Operation(summary = "Get command statistics grouped by user", description = "Returns top users by command count with success/failure breakdown.")
    @RequiresFeature("REPORT_COMMAND")
    public ResponseEntity<List<Map<String, Object>>> getCommandUserStats(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.getCommandUserStats(dateFrom, dateTo, limit));
    }

    @GetMapping("/commands/bulk/{bulkCommandId}")
    @Operation(summary = "Get bulk command detail", description = "Returns aggregated status with timing stats, failure reasons, and completion timeline.")
    @RequiresFeature("REPORT_COMMAND")
    public ResponseEntity<Map<String, Object>> getBulkCommandDetail(@PathVariable UUID bulkCommandId) {
        return ResponseEntity.ok(reportService.getBulkCommandDetail(bulkCommandId));
    }

    @GetMapping("/commands/bulk/{bulkCommandId}/devices")
    @Operation(summary = "Get bulk command devices (paginated + filterable)", description = "Returns paginated list of devices with optional status and search filters.")
    @RequiresFeature("REPORT_COMMAND")
    public ResponseEntity<PagedModel<Map<String, Object>>> getBulkCommandDevices(
            @PathVariable UUID bulkCommandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(reportService.getBulkCommandDevices(bulkCommandId, page, size, status, search));
    }

    // ─── Device Activity ───

    @PostMapping("/device-activity/query")
    @Operation(summary = "Query device activity heatmap and uptime", description = "Returns hourly heatmap of online device counts and per-device uptime in the given time range.")
    @RequiresFeature("REPORT_DEVICE_ACTIVITY")
    public ResponseEntity<Map<String, Object>> queryDeviceActivity(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(reportService.queryDeviceActivity(request));
    }

    // ─── Map Analysis ───

    @PostMapping("/map-analysis/query")
    @Operation(summary = "Query device locations by area and/or time range",
            description = "Returns device location points that match the given spatial area (circle/rectangle/polygon) and/or time range.")
    @RequiresFeature("MAP_ANALYSIS")
    public ResponseEntity<Map<String, Object>> queryLocationsByArea(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(reportService.queryLocationsByArea(request));
    }

    // ─── Fleet Health ───

    @GetMapping("/fleet-health/summary")
    @Operation(summary = "Get fleet health summary", description = "Provides fleet telemetry statistics: battery, storage, thermal, network.")
    @RequiresFeature("REPORT_FLEET_HEALTH")
    public ResponseEntity<FleetHealthSummaryDto> getFleetHealthSummary(
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getFleetHealthSummary(platform));
    }

    @PostMapping("/fleet-health/devices")
    @Operation(summary = "Get fleet health devices", description = "Returns paginated list of devices with latest telemetry data.")
    @RequiresFeature("REPORT_FLEET_HEALTH")
    public ResponseEntity<PagedModel<FleetHealthDeviceDto>> getFleetHealthDevices(
            @RequestBody FleetHealthDeviceRequestDto request) {
        return ResponseEntity.ok(reportService.getFleetHealthDevices(request));
    }

    // ─── Compliance ───

    @GetMapping("/compliance/summary")
    @Operation(summary = "Get compliance summary", description = "Provides compliance statistics across the device fleet.")
    @RequiresFeature("REPORT_COMPLIANCE")
    public ResponseEntity<ComplianceSummaryDto> getComplianceSummary(
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getComplianceSummary(platform));
    }

    @PostMapping("/compliance/devices")
    @Operation(summary = "Get compliance devices", description = "Returns paginated list of devices with compliance details.")
    @RequiresFeature("REPORT_COMPLIANCE")
    public ResponseEntity<PagedModel<ComplianceDeviceDto>> getComplianceDevices(
            @RequestBody ComplianceDeviceRequestDto request) {
        return ResponseEntity.ok(reportService.getComplianceDevices(request));
    }

    // ─── Enrollment ───

    @GetMapping("/enrollment/summary")
    @Operation(summary = "Get enrollment summary", description = "Provides enrollment statistics with type distribution.")
    @RequiresFeature("REPORT_ENROLLMENT")
    public ResponseEntity<EnrollmentSummaryDto> getEnrollmentSummary(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getEnrollmentSummary(dateFrom, dateTo, platform));
    }

    @GetMapping("/enrollment/trend")
    @Operation(summary = "Get enrollment daily trend", description = "Provides daily enrollment and unenrollment counts.")
    @RequiresFeature("REPORT_ENROLLMENT")
    public ResponseEntity<List<EnrollmentDailyTrendDto>> getEnrollmentDailyTrend(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getEnrollmentDailyTrend(dateFrom, dateTo, platform));
    }

    @PostMapping("/enrollment/history")
    @Operation(summary = "Get enrollment history", description = "Returns paginated enrollment history with filters.")
    @RequiresFeature("REPORT_ENROLLMENT")
    public ResponseEntity<PagedModel<EnrollmentHistoryItemDto>> getEnrollmentHistory(
            @RequestBody EnrollmentHistoryRequestDto request) {
        return ResponseEntity.ok(reportService.getEnrollmentHistory(request));
    }

    // ─── Security Posture ───

    @GetMapping("/security/summary")
    @Operation(summary = "Get security posture summary", description = "Provides security statistics: supervised, activation lock, jailbreak, etc.")
    @RequiresFeature("REPORT_SECURITY")
    public ResponseEntity<SecuritySummaryDto> getSecuritySummary(
            @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(reportService.getSecuritySummary(platform));
    }

    @PostMapping("/security/devices")
    @Operation(summary = "Get security devices", description = "Returns paginated list of devices with security details.")
    @RequiresFeature("REPORT_SECURITY")
    public ResponseEntity<PagedModel<SecurityDeviceDto>> getSecurityDevices(
            @RequestBody SecurityDeviceRequestDto request) {
        return ResponseEntity.ok(reportService.getSecurityDevices(request));
    }
}
