package com.arcyintel.arcops.apple_mdm.services.dashboard;

import com.arcyintel.arcops.apple_mdm.domains.*;
import com.arcyintel.arcops.apple_mdm.models.api.dashboard.DashboardStatsDto;
import com.arcyintel.arcops.apple_mdm.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private static final String DELETED = "DELETED";

    private final AppleDeviceAppRepository appleDeviceAppRepository;
    private final AppleCommandRepository appleCommandRepository;
    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleDeviceInformationRepository appleDeviceInformationRepository;
    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final EnterpriseAppRepository enterpriseAppRepository;
    private final AgentTelemetryRepository agentTelemetryRepository;
    private final AgentLocationRepository agentLocationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final com.arcyintel.arcops.apple_mdm.clients.BackCoreClient backCoreClient;

    /** Signaling command types excluded from reports (not real device commands). */
    private static final String AGENT_CMD_SIGNALING_FILTER =
            "AND command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')";

    /**
     * Resolves device IDs belonging to a device group by calling back_core.
     */
    public Set<UUID> resolveDeviceGroupDeviceIds(UUID deviceGroupId) {
        return backCoreClient.getDeviceGroupDeviceIds(deviceGroupId);
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto getStats() {
        log.debug("Building dashboard statistics");

        return DashboardStatsDto.builder()
                .topManagedApps(buildTopManagedApps())
                .commandStats(buildCommandStats())
                .deviceStats(buildDeviceStats())
                .osVersions(buildOsVersions())
                .modelDistribution(buildModelDistribution())
                .fleetTelemetry(buildFleetTelemetry())
                .onlineStatus(buildOnlineStatus())
                .enrollmentTypeDistribution(buildEnrollmentTypeDistribution())
                .securityPosture(buildSecurityPosture())
                .recentCommands(buildRecentCommands())
                .deviceLocations(buildDeviceLocations())
                .commandTrend(buildCommandTrend())
                .build();
    }

    public List<DashboardStatsDto.TopManagedAppDto> buildTopManagedApps() {
        List<Object[]> rows = appleDeviceAppRepository.findTopManagedApps();
        if (rows.isEmpty()) {
            return List.of();
        }

        // Collect bundle IDs for icon resolution
        List<String> bundleIds = rows.stream()
                .map(r -> (String) r[0])
                .toList();

        // Batch fetch icons from iTunes and Enterprise
        Map<String, ItunesAppMeta> itunesMap = itunesAppMetaRepository.findAllByBundleIdIn(bundleIds)
                .stream()
                .collect(Collectors.toMap(ItunesAppMeta::getBundleId, Function.identity(), (a, b) -> a));

        Map<String, EnterpriseApp> enterpriseMap = enterpriseAppRepository.findAllByBundleIdIn(bundleIds)
                .stream()
                .collect(Collectors.toMap(EnterpriseApp::getBundleId, Function.identity(), (a, b) -> a));

        List<DashboardStatsDto.TopManagedAppDto> result = new ArrayList<>();
        for (Object[] row : rows) {
            String bundleId = (String) row[0];
            String name = (String) row[1];
            long installCount = ((Number) row[2]).longValue();

            String iconUrl = null;
            ItunesAppMeta itunes = itunesMap.get(bundleId);
            if (itunes != null && itunes.getArtworkUrl512() != null) {
                iconUrl = itunes.getArtworkUrl512();
            } else {
                EnterpriseApp enterprise = enterpriseMap.get(bundleId);
                if (enterprise != null && enterprise.getIconBase64() != null) {
                    iconUrl = "data:image/png;base64," + enterprise.getIconBase64();
                }
            }

            result.add(DashboardStatsDto.TopManagedAppDto.builder()
                    .bundleId(bundleId)
                    .name(name != null ? name : bundleId)
                    .installCount(installCount)
                    .iconUrl(iconUrl)
                    .build());
        }
        return result;
    }

    public DashboardStatsDto.CommandStatsDto buildCommandStats() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                       COUNT(*) FILTER (WHERE status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed,
                       COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
                       COUNT(*) FILTER (WHERE status = 'EXECUTING') AS executing,
                       COUNT(*) FILTER (WHERE status = 'CANCELED') AS canceled
                FROM (
                    SELECT status FROM apple_command
                    UNION ALL
                    SELECT CASE WHEN status = 'SENT' THEN 'PENDING' ELSE status END AS status
                    FROM agent_command
                    WHERE command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
                ) u
                """);

        return DashboardStatsDto.CommandStatsDto.builder()
                .total(((Number) row.get("total")).longValue())
                .completed(((Number) row.get("completed")).longValue())
                .failed(((Number) row.get("failed")).longValue())
                .pending(((Number) row.get("pending")).longValue())
                .executing(((Number) row.get("executing")).longValue())
                .canceled(((Number) row.get("canceled")).longValue())
                .build();
    }

    public DashboardStatsDto.DeviceStatsDto buildDeviceStats() {
        long totalDevices = appleDeviceRepository.countByStatusNot(DELETED);
        long compliantCount = appleDeviceRepository.countByIsCompliantTrueAndStatusNot(DELETED);
        long nonCompliantCount = appleDeviceRepository.countByIsCompliantFalseAndStatusNot(DELETED);
        long supervisedCount = appleDeviceInformationRepository.countSupervisedDevices();
        long awaitingCount = appleDeviceInformationRepository.countAwaitingConfigurationDevices();

        return DashboardStatsDto.DeviceStatsDto.builder()
                .totalDevices(totalDevices)
                .compliantCount(compliantCount)
                .nonCompliantCount(nonCompliantCount)
                .supervisedCount(supervisedCount)
                .awaitingConfigurationCount(awaitingCount)
                .build();
    }

    public List<DashboardStatsDto.OsVersionCountDto> buildOsVersions() {
        return appleDeviceRepository.findOsVersionDistribution().stream()
                .map(row -> DashboardStatsDto.OsVersionCountDto.builder()
                        .version((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .platform((String) row[2])
                        .build())
                .toList();
    }

    public List<DashboardStatsDto.ModelCountDto> buildModelDistribution() {
        return appleDeviceInformationRepository.findModelDistribution().stream()
                .map(row -> DashboardStatsDto.ModelCountDto.builder()
                        .modelName((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();
    }

    // ──── New build methods for enhanced dashboard ────

    public DashboardStatsDto.FleetTelemetryDto buildFleetTelemetry() {
        return buildFleetTelemetry(null);
    }

    public DashboardStatsDto.FleetTelemetryDto buildFleetTelemetry(Set<UUID> deviceIds) {
        List<AgentTelemetry> latestEntries = agentTelemetryRepository.findLatestPerDevice();
        // Filter by device group if specified
        if (deviceIds != null && !deviceIds.isEmpty()) {
            latestEntries = latestEntries.stream()
                    .filter(t -> t.getDevice() != null && deviceIds.contains(t.getDevice().getId()))
                    .toList();
        }
        if (latestEntries.isEmpty()) {
            return DashboardStatsDto.FleetTelemetryDto.builder()
                    .networkTypeDistribution(List.of())
                    .thermalStateDistribution(List.of())
                    .build();
        }

        double batterySum = 0;
        int batteryCount = 0;
        long lowBattery = 0;
        long criticalBattery = 0;
        double storageSum = 0;
        int storageCount = 0;
        long storageWarning = 0;
        long storageCritical = 0;
        long vpnActive = 0;
        long lowPowerMode = 0;
        Map<String, Long> networkTypes = new HashMap<>();
        Map<String, Long> thermalStates = new HashMap<>();

        for (AgentTelemetry t : latestEntries) {
            if (t.getBatteryLevel() != null) {
                batterySum += t.getBatteryLevel();
                batteryCount++;
                if (t.getBatteryLevel() < 20) lowBattery++;
                if (t.getBatteryLevel() < 10) criticalBattery++;
            }
            if (t.getStorageUsagePercent() != null) {
                storageSum += t.getStorageUsagePercent();
                storageCount++;
                if (t.getStorageUsagePercent() > 80) storageWarning++;
                if (t.getStorageUsagePercent() > 90) storageCritical++;
            }
            if (Boolean.TRUE.equals(t.getVpnActive())) vpnActive++;
            if (Boolean.TRUE.equals(t.getLowPowerMode())) lowPowerMode++;

            String nt = t.getNetworkType() != null ? t.getNetworkType().toUpperCase() : "UNKNOWN";
            networkTypes.merge(nt, 1L, Long::sum);

            String ts = t.getThermalState() != null ? t.getThermalState().toUpperCase() : "UNKNOWN";
            thermalStates.merge(ts, 1L, Long::sum);
        }

        return DashboardStatsDto.FleetTelemetryDto.builder()
                .avgBatteryLevel(batteryCount > 0 ? Math.round(batterySum / batteryCount * 10.0) / 10.0 : 0)
                .lowBatteryCount(lowBattery)
                .criticalBatteryCount(criticalBattery)
                .avgStorageUsagePercent(storageCount > 0 ? Math.round(storageSum / storageCount * 10.0) / 10.0 : 0)
                .storageWarningCount(storageWarning)
                .storageCriticalCount(storageCritical)
                .vpnActiveCount(vpnActive)
                .lowPowerModeCount(lowPowerMode)
                .networkTypeDistribution(networkTypes.entrySet().stream()
                        .map(e -> DashboardStatsDto.NetworkTypeCountDto.builder()
                                .networkType(e.getKey())
                                .count(e.getValue())
                                .build())
                        .sorted(Comparator.comparingLong(DashboardStatsDto.NetworkTypeCountDto::getCount).reversed())
                        .toList())
                .thermalStateDistribution(thermalStates.entrySet().stream()
                        .map(e -> DashboardStatsDto.ThermalStateCountDto.builder()
                                .thermalState(e.getKey())
                                .count(e.getValue())
                                .build())
                        .sorted(Comparator.comparingLong(DashboardStatsDto.ThermalStateCountDto::getCount).reversed())
                        .toList())
                .build();
    }

    public DashboardStatsDto.OnlineStatusDto buildOnlineStatus() {
        long online = appleDeviceRepository.countByAgentOnlineTrueAndStatusNot(DELETED);
        long offline = appleDeviceRepository.countByAgentOnlineFalseAndStatusNot(DELETED);
        long neverSeen = appleDeviceRepository.countByAgentLastSeenAtIsNullAndStatusNot(DELETED);

        return DashboardStatsDto.OnlineStatusDto.builder()
                .onlineCount(online)
                .offlineCount(offline)
                .neverSeenCount(neverSeen)
                .build();
    }

    public List<DashboardStatsDto.EnrollmentTypeCountDto> buildEnrollmentTypeDistribution() {
        return appleDeviceRepository.findEnrollmentTypeDistribution().stream()
                .map(row -> DashboardStatsDto.EnrollmentTypeCountDto.builder()
                        .enrollmentType((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();
    }

    public DashboardStatsDto.SecurityPostureDto buildSecurityPosture() {
        long totalDevices = appleDeviceRepository.countByStatusNot(DELETED);
        long supervisedCount = appleDeviceInformationRepository.countSupervisedDevices();
        long activationLock = appleDeviceInformationRepository.countActivationLockEnabled();
        long cloudBackup = appleDeviceInformationRepository.countCloudBackupEnabled();
        long compliantCount = appleDeviceRepository.countByIsCompliantTrueAndStatusNot(DELETED);

        // Jailbreak and debugger counts from latest telemetry
        List<AgentTelemetry> latestEntries = agentTelemetryRepository.findLatestPerDevice();
        long jailbreakCount = latestEntries.stream()
                .filter(t -> Boolean.TRUE.equals(t.getJailbreakDetected()))
                .count();
        long debuggerCount = latestEntries.stream()
                .filter(t -> Boolean.TRUE.equals(t.getDebuggerAttached()))
                .count();

        return DashboardStatsDto.SecurityPostureDto.builder()
                .totalDevices(totalDevices)
                .supervisedPercent(totalDevices > 0 ? Math.round((double) supervisedCount / totalDevices * 100 * 10) / 10.0 : 0)
                .activationLockCount(activationLock)
                .cloudBackupEnabledCount(cloudBackup)
                .jailbreakDetectedCount(jailbreakCount)
                .debuggerAttachedCount(debuggerCount)
                .compliantPercent(totalDevices > 0 ? Math.round((double) compliantCount / totalDevices * 100 * 10) / 10.0 : 0)
                .build();
    }

    public List<DashboardStatsDto.RecentCommandDto> buildRecentCommands() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, command_type, status, device_name, device_udid, request_time, completion_time, failure_reason
                FROM (
                    SELECT ac.id, ac.command_type, ac.status,
                           di.device_name,
                           ac.apple_device_udid AS device_udid,
                           ac.request_time, ac.completion_time, ac.failure_reason
                    FROM apple_command ac
                    LEFT JOIN apple_device ad ON ad.udid = ac.apple_device_udid
                    LEFT JOIN apple_device_information di ON di.id = ad.id
                    UNION ALL
                    SELECT agc.id, agc.command_type,
                           CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status,
                           di2.device_name,
                           agc.device_identifier AS device_udid,
                           agc.request_time, agc.response_time AS completion_time, agc.error_message AS failure_reason
                    FROM agent_command agc
                    LEFT JOIN apple_device ad2 ON ad2.udid = agc.device_identifier
                    LEFT JOIN apple_device_information di2 ON di2.id = ad2.id
                    WHERE agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')
                ) u
                ORDER BY request_time DESC
                LIMIT 15
                """);

        return rows.stream()
                .map(r -> DashboardStatsDto.RecentCommandDto.builder()
                        .id(r.get("id") != null ? r.get("id").toString() : null)
                        .commandType((String) r.get("command_type"))
                        .status((String) r.get("status"))
                        .deviceName((String) r.get("device_name"))
                        .deviceUdid((String) r.get("device_udid"))
                        .requestTime(r.get("request_time") != null ? ((java.sql.Timestamp) r.get("request_time")).toInstant() : null)
                        .completionTime(r.get("completion_time") != null ? ((java.sql.Timestamp) r.get("completion_time")).toInstant() : null)
                        .failureReason((String) r.get("failure_reason"))
                        .build())
                .toList();
    }

    public List<DashboardStatsDto.DeviceLocationPointDto> buildDeviceLocations() {
        List<AgentLocation> locations = agentLocationRepository.findLatestPerDevice();
        return locations.stream()
                .map(l -> {
                    boolean online = false;
                    String deviceName = null;
                    if (l.getDevice() != null) {
                        online = Boolean.TRUE.equals(l.getDevice().getAgentOnline());
                        if (l.getDevice().getDeviceProperties() != null) {
                            deviceName = l.getDevice().getDeviceProperties().getDeviceName();
                        }
                    }
                    return DashboardStatsDto.DeviceLocationPointDto.builder()
                            .latitude(l.getLatitude())
                            .longitude(l.getLongitude())
                            .deviceName(deviceName)
                            .deviceIdentifier(l.getDeviceIdentifier())
                            .agentOnline(online)
                            .build();
                })
                .toList();
    }

    public DashboardStatsDto.CommandTrendDto buildCommandTrend() {
        Instant since = LocalDate.now(ZoneOffset.UTC).minusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Object[]> rows = appleCommandRepository.findDailyCommandCounts(since);
        List<DashboardStatsDto.DailyCommandCountDto> dailyCounts = rows.stream()
                .map(row -> DashboardStatsDto.DailyCommandCountDto.builder()
                        .date(row[0].toString())
                        .total(((Number) row[1]).longValue())
                        .completed(((Number) row[2]).longValue())
                        .failed(((Number) row[3]).longValue())
                        .build())
                .toList();

        return DashboardStatsDto.CommandTrendDto.builder()
                .dailyCounts(dailyCounts)
                .build();
    }

    // ──── New build methods for dashboard charts ────

    public DashboardStatsDto.CommandAnalyticsDto buildCommandAnalytics() {
        List<DashboardStatsDto.CommandTypeCountDto> typeDistribution = appleCommandRepository.findCommandTypeDistribution()
                .stream()
                .map(row -> DashboardStatsDto.CommandTypeCountDto.builder()
                        .commandType((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        List<DashboardStatsDto.CommandSuccessRateDto> successRates = appleCommandRepository.findCommandSuccessRatesByType()
                .stream()
                .map(row -> DashboardStatsDto.CommandSuccessRateDto.builder()
                        .commandType((String) row[0])
                        .total(((Number) row[1]).longValue())
                        .completed(((Number) row[2]).longValue())
                        .failed(((Number) row[3]).longValue())
                        .build())
                .toList();

        return DashboardStatsDto.CommandAnalyticsDto.builder()
                .commandTypeDistribution(typeDistribution)
                .commandSuccessRates(successRates)
                .build();
    }

    public DashboardStatsDto.TelemetryAnalyticsDto buildTelemetryAnalytics() {
        List<AgentTelemetry> latestEntries = agentTelemetryRepository.findLatestPerDevice();
        if (latestEntries.isEmpty()) {
            return DashboardStatsDto.TelemetryAnalyticsDto.builder()
                    .batteryHistogram(List.of())
                    .batteryStateDistribution(List.of())
                    .topWifiNetworks(List.of())
                    .carrierDistribution(List.of())
                    .radioTechDistribution(List.of())
                    .languageDistribution(List.of())
                    .timezoneDistribution(List.of())
                    .build();
        }

        // Battery histogram buckets
        long[] batteryBuckets = new long[10]; // 0-10, 10-20, ..., 90-100
        Map<String, Long> batteryStates = new HashMap<>();
        Map<String, Long> wifiNetworks = new HashMap<>();
        Map<String, Long> carriers = new HashMap<>();
        Map<String, Long> radioTechs = new HashMap<>();
        Map<String, Long> languages = new HashMap<>();
        Map<String, Long> timezones = new HashMap<>();

        for (AgentTelemetry t : latestEntries) {
            if (t.getBatteryLevel() != null) {
                int bucket = Math.min((int) (t.getBatteryLevel() / 10), 9);
                batteryBuckets[bucket]++;
            }
            if (t.getBatteryState() != null && !t.getBatteryState().isBlank()) {
                batteryStates.merge(t.getBatteryState(), 1L, Long::sum);
            }
            if (t.getWifiSsid() != null && !t.getWifiSsid().isBlank()) {
                wifiNetworks.merge(t.getWifiSsid(), 1L, Long::sum);
            }
            if (t.getCarrierName() != null && !t.getCarrierName().isBlank()) {
                carriers.merge(t.getCarrierName(), 1L, Long::sum);
            }
            if (t.getRadioTechnology() != null && !t.getRadioTechnology().isBlank()) {
                radioTechs.merge(t.getRadioTechnology(), 1L, Long::sum);
            }
            if (t.getLocaleLanguage() != null && !t.getLocaleLanguage().isBlank()) {
                languages.merge(t.getLocaleLanguage(), 1L, Long::sum);
            }
            if (t.getLocaleTimezone() != null && !t.getLocaleTimezone().isBlank()) {
                timezones.merge(t.getLocaleTimezone(), 1L, Long::sum);
            }
        }

        // Build battery histogram
        List<DashboardStatsDto.LabelCountDto> batteryHistogram = new ArrayList<>();
        String[] bucketLabels = {"0-10%", "10-20%", "20-30%", "30-40%", "40-50%", "50-60%", "60-70%", "70-80%", "80-90%", "90-100%"};
        for (int i = 0; i < 10; i++) {
            batteryHistogram.add(DashboardStatsDto.LabelCountDto.builder()
                    .label(bucketLabels[i])
                    .count(batteryBuckets[i])
                    .build());
        }

        return DashboardStatsDto.TelemetryAnalyticsDto.builder()
                .batteryHistogram(batteryHistogram)
                .batteryStateDistribution(toSortedLabelCounts(batteryStates))
                .topWifiNetworks(toTopN(wifiNetworks, 10))
                .carrierDistribution(toSortedLabelCounts(carriers))
                .radioTechDistribution(toSortedLabelCounts(radioTechs))
                .languageDistribution(toTopN(languages, 10))
                .timezoneDistribution(toTopN(timezones, 10))
                .build();
    }

    public List<DashboardStatsDto.StorageTierCountDto> buildStorageTiers() {
        List<AppleDeviceInformation> devices = appleDeviceInformationRepository.findAllWithCapacity();
        if (devices.isEmpty()) {
            return List.of();
        }

        // Tier definitions in order
        String[] tierLabels = {"16 GB", "32 GB", "64 GB", "128 GB", "256 GB", "512 GB", "1 TB+"};
        double[] tierThresholds = {32, 64, 128, 256, 512, 1024, Double.MAX_VALUE};
        long[] counts = new long[tierLabels.length];

        for (AppleDeviceInformation d : devices) {
            double cap = d.getDeviceCapacity().doubleValue();
            for (int i = 0; i < tierThresholds.length; i++) {
                if (cap < tierThresholds[i]) {
                    counts[i]++;
                    break;
                }
            }
        }

        List<DashboardStatsDto.StorageTierCountDto> result = new ArrayList<>();
        for (int i = 0; i < tierLabels.length; i++) {
            if (counts[i] > 0) {
                result.add(DashboardStatsDto.StorageTierCountDto.builder()
                        .tier(tierLabels[i])
                        .count(counts[i])
                        .build());
            }
        }
        return result;
    }

    public DashboardStatsDto.DeviceFeatureEnablementDto buildDeviceFeatures() {
        List<Object[]> rows = appleDeviceInformationRepository.findFeatureEnablementCounts();
        if (rows.isEmpty()) {
            return DashboardStatsDto.DeviceFeatureEnablementDto.builder()
                    .features(List.of())
                    .build();
        }

        Object[] row = rows.get(0);
        String[] featureNames = {"Activation Lock", "Cloud Backup", "Find My", "App Analytics", "Diagnostic Submission"};
        List<DashboardStatsDto.FeatureEnablementDto> features = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            features.add(DashboardStatsDto.FeatureEnablementDto.builder()
                    .featureName(featureNames[i])
                    .enabledCount(((Number) row[i * 2]).longValue())
                    .disabledCount(((Number) row[i * 2 + 1]).longValue())
                    .build());
        }

        return DashboardStatsDto.DeviceFeatureEnablementDto.builder()
                .features(features)
                .build();
    }

    public DashboardStatsDto.EnrollmentTrendDto buildEnrollmentTrend() {
        Instant since = Instant.now().minus(java.time.Duration.ofDays(30));

        List<DashboardStatsDto.DailyEnrollmentCountDto> dailyCounts = appleDeviceRepository.findDailyEnrollmentCounts(since)
                .stream()
                .map(row -> DashboardStatsDto.DailyEnrollmentCountDto.builder()
                        .date(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        return DashboardStatsDto.EnrollmentTrendDto.builder()
                .dailyCounts(dailyCounts)
                .build();
    }

    // ──── Helpers ────

    private List<DashboardStatsDto.LabelCountDto> toSortedLabelCounts(Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> DashboardStatsDto.LabelCountDto.builder()
                        .label(e.getKey())
                        .count(e.getValue())
                        .build())
                .toList();
    }

    private List<DashboardStatsDto.LabelCountDto> toTopN(Map<String, Long> map, int n) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .map(e -> DashboardStatsDto.LabelCountDto.builder()
                        .label(e.getKey())
                        .count(e.getValue())
                        .build())
                .toList();
    }
}
