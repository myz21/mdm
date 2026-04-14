package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.dashboard.DashboardStatsDto;
import com.arcyintel.arcops.apple_mdm.services.dashboard.DashboardStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard statistics and aggregations")
public class DashboardController {

    private final DashboardStatsService dashboardStatsService;

    @GetMapping("/stats")
    @Operation(summary = "Get all dashboard statistics", description = "Returns all aggregated statistics for the dashboard (legacy).")
    public ResponseEntity<DashboardStatsDto> getStats() {
        return ResponseEntity.ok(dashboardStatsService.getStats());
    }

    @GetMapping("/device-stats")
    @Operation(summary = "Get device statistics")
    public ResponseEntity<DashboardStatsDto.DeviceStatsDto> getDeviceStats() {
        return ResponseEntity.ok(dashboardStatsService.buildDeviceStats());
    }

    @GetMapping("/command-stats")
    @Operation(summary = "Get command statistics")
    public ResponseEntity<DashboardStatsDto.CommandStatsDto> getCommandStats() {
        return ResponseEntity.ok(dashboardStatsService.buildCommandStats());
    }

    @GetMapping("/top-apps")
    @Operation(summary = "Get top managed apps")
    public ResponseEntity<List<DashboardStatsDto.TopManagedAppDto>> getTopApps() {
        return ResponseEntity.ok(dashboardStatsService.buildTopManagedApps());
    }

    @GetMapping("/os-versions")
    @Operation(summary = "Get OS version distribution")
    public ResponseEntity<List<DashboardStatsDto.OsVersionCountDto>> getOsVersions() {
        return ResponseEntity.ok(dashboardStatsService.buildOsVersions());
    }

    @GetMapping("/model-distribution")
    @Operation(summary = "Get device model distribution")
    public ResponseEntity<List<DashboardStatsDto.ModelCountDto>> getModelDistribution() {
        return ResponseEntity.ok(dashboardStatsService.buildModelDistribution());
    }

    @GetMapping("/fleet-telemetry")
    @Operation(summary = "Get fleet telemetry data, optionally filtered by device group")
    public ResponseEntity<DashboardStatsDto.FleetTelemetryDto> getFleetTelemetry(
            @RequestParam(required = false) UUID deviceGroupId) {
        if (deviceGroupId != null) {
            Set<UUID> deviceIds = dashboardStatsService.resolveDeviceGroupDeviceIds(deviceGroupId);
            return ResponseEntity.ok(dashboardStatsService.buildFleetTelemetry(deviceIds));
        }
        return ResponseEntity.ok(dashboardStatsService.buildFleetTelemetry(null));
    }

    @GetMapping("/online-status")
    @Operation(summary = "Get online/offline device status")
    public ResponseEntity<DashboardStatsDto.OnlineStatusDto> getOnlineStatus() {
        return ResponseEntity.ok(dashboardStatsService.buildOnlineStatus());
    }

    @GetMapping("/enrollment-breakdown")
    @Operation(summary = "Get enrollment type distribution")
    public ResponseEntity<List<DashboardStatsDto.EnrollmentTypeCountDto>> getEnrollmentBreakdown() {
        return ResponseEntity.ok(dashboardStatsService.buildEnrollmentTypeDistribution());
    }

    @GetMapping("/security-posture")
    @Operation(summary = "Get security posture data")
    public ResponseEntity<DashboardStatsDto.SecurityPostureDto> getSecurityPosture() {
        return ResponseEntity.ok(dashboardStatsService.buildSecurityPosture());
    }

    @GetMapping("/recent-commands")
    @Operation(summary = "Get recent commands")
    public ResponseEntity<List<DashboardStatsDto.RecentCommandDto>> getRecentCommands() {
        return ResponseEntity.ok(dashboardStatsService.buildRecentCommands());
    }

    @GetMapping("/device-locations")
    @Operation(summary = "Get device locations")
    public ResponseEntity<List<DashboardStatsDto.DeviceLocationPointDto>> getDeviceLocations() {
        return ResponseEntity.ok(dashboardStatsService.buildDeviceLocations());
    }

    @GetMapping("/command-trend")
    @Operation(summary = "Get 7-day command trend")
    public ResponseEntity<DashboardStatsDto.CommandTrendDto> getCommandTrend() {
        return ResponseEntity.ok(dashboardStatsService.buildCommandTrend());
    }

    @GetMapping("/command-analytics")
    @Operation(summary = "Get command type distribution and success rates")
    public ResponseEntity<DashboardStatsDto.CommandAnalyticsDto> getCommandAnalytics() {
        return ResponseEntity.ok(dashboardStatsService.buildCommandAnalytics());
    }

    @GetMapping("/telemetry-analytics")
    @Operation(summary = "Get telemetry analytics (battery, wifi, carrier, language, timezone)")
    public ResponseEntity<DashboardStatsDto.TelemetryAnalyticsDto> getTelemetryAnalytics() {
        return ResponseEntity.ok(dashboardStatsService.buildTelemetryAnalytics());
    }

    @GetMapping("/storage-tiers")
    @Operation(summary = "Get storage capacity tier distribution")
    public ResponseEntity<List<DashboardStatsDto.StorageTierCountDto>> getStorageTiers() {
        return ResponseEntity.ok(dashboardStatsService.buildStorageTiers());
    }

    @GetMapping("/device-features")
    @Operation(summary = "Get device feature enablement counts")
    public ResponseEntity<DashboardStatsDto.DeviceFeatureEnablementDto> getDeviceFeatures() {
        return ResponseEntity.ok(dashboardStatsService.buildDeviceFeatures());
    }

    @GetMapping("/enrollment-trend")
    @Operation(summary = "Get 30-day enrollment trend")
    public ResponseEntity<DashboardStatsDto.EnrollmentTrendDto> getEnrollmentTrend() {
        return ResponseEntity.ok(dashboardStatsService.buildEnrollmentTrend());
    }
}
