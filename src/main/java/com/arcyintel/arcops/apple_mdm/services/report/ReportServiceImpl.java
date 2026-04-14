package com.arcyintel.arcops.apple_mdm.services.report;

import com.arcyintel.arcops.apple_mdm.domains.BulkCommand;
import com.arcyintel.arcops.apple_mdm.models.api.report.*;
import com.arcyintel.arcops.apple_mdm.repositories.*;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportServiceImpl.class);
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.3;
    private static final Set<String> VALID_APP_SORT_FIELDS = Set.of("creationDate", "osVersion", "enrollmentType");
    private static final Set<String> VALID_COMMAND_SORT_FIELDS = Set.of("requestTime", "commandType", "status", "executionDurationMs");
    private static final Set<String> VALID_FLEET_SORT_FIELDS = Set.of("batteryLevel", "storageUsagePercent", "thermalState", "agentLastSeenAt");
    private static final Set<String> VALID_COMPLIANCE_SORT_FIELDS = Set.of("productName", "osVersion", "enrollmentType");
    private static final Set<String> VALID_ENROLLMENT_SORT_FIELDS = Set.of("enrolledAt", "productName", "enrollmentType");
    private static final Set<String> VALID_SECURITY_SORT_FIELDS = Set.of("productName", "osVersion");
    private static final String[] BUCKET_RANGES = {"0-10", "10-20", "20-30", "30-40", "40-50", "50-60", "60-70", "70-80", "80-90", "90-100"};

    private final AppleDeviceAppRepository appleDeviceAppRepository;
    private final AppleCommandRepository appleCommandRepository;
    private final AgentTelemetryRepository agentTelemetryRepository;
    private final AppleDeviceRepository appleDeviceRepository;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;
    private final AppleDeviceInformationRepository appleDeviceInformationRepository;
    private final BulkCommandRepository bulkCommandRepository;
    private final AgentPresenceHistoryRepository agentPresenceHistoryRepository;
    private final AgentLocationRepository agentLocationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AppSearchResultDto> searchApps(String query, String platform) {
        if (query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }

        String normalizedPlatform = normalizePlatform(platform);
        List<Object[]> results = appleDeviceAppRepository.searchApps(
                query.trim(), normalizedPlatform, DEFAULT_SIMILARITY_THRESHOLD);

        return results.stream()
                .map(row -> AppSearchResultDto.builder()
                        .name((String) row[0])
                        .bundleIdentifier((String) row[1])
                        .installCount(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppVersionDto> findVersionsByBundleIdentifier(String bundleIdentifier, String platform) {
        String normalizedPlatform = normalizePlatform(platform);
        List<Object[]> results = appleDeviceAppRepository.findVersionsByBundleIdentifier(
                bundleIdentifier, normalizedPlatform);

        return results.stream()
                .map(row -> AppVersionDto.builder()
                        .version((String) row[0])
                        .shortVersion((String) row[1])
                        .deviceCount(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedModel<AppDeviceReportDto> findDevicesByApp(String bundleIdentifier, AppDeviceReportRequestDto request) {
        String normalizedPlatform = normalizePlatform(request.getPlatform());
        String sortBy = normalizeAppSortBy(request.getSortBy());
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(100, request.getSize()));
        int offset = page * size;

        List<Object[]> results = appleDeviceAppRepository.findDevicesByApp(
                bundleIdentifier,
                request.getVersion(),
                normalizedPlatform,
                sortBy,
                request.isSortDesc(),
                size,
                offset);

        if (results.isEmpty()) {
            return new PagedModel<>(Page.empty(PageRequest.of(page, size)));
        }

        long totalCount = ((Number) results.getFirst()[17]).longValue();

        List<AppDeviceReportDto> devices = results.stream()
                .map(this::mapToDeviceReport)
                .toList();

        PageImpl<AppDeviceReportDto> pageResult = new PageImpl<>(devices, PageRequest.of(page, size), totalCount);
        return new PagedModel<>(pageResult);
    }

    @Override
    @Transactional(readOnly = true)
    public CommandReportSummaryDto getCommandReportSummary(String dateFrom, String dateTo, String commandType, String status, String platform) {
        Instant from = parseDateToInstant(dateFrom);
        Instant to = parseDateToEndOfDay(dateTo);
        String normalizedPlatform = normalizePlatform(platform);

        List<Object[]> result = appleCommandRepository.getCommandSummary(from, to, commandType, status, normalizedPlatform);

        if (result.isEmpty() || result.getFirst() == null || result.getFirst()[0] == null) {
            return CommandReportSummaryDto.builder()
                    .totalCommands(0)
                    .completedCommands(0)
                    .failedCommands(0)
                    .pendingCommands(0)
                    .executingCommands(0)
                    .canceledCommands(0)
                    .avgExecutionTimeMs(0.0)
                    .successRate(0.0)
                    .build();
        }

        Object[] row = result.getFirst();
        long totalCommands = ((Number) row[0]).longValue();
        long completedCommands = ((Number) row[1]).longValue();
        long failedCommands = ((Number) row[2]).longValue();
        long pendingCommands = ((Number) row[3]).longValue();
        long executingCommands = ((Number) row[4]).longValue();
        long canceledCommands = ((Number) row[5]).longValue();
        Double avgExecutionTimeMs = row[6] != null ? ((Number) row[6]).doubleValue() : null;

        double successRate = 0.0;
        long totalHandledCommands = completedCommands + failedCommands;
        if (totalHandledCommands > 0) {
            successRate = (double) completedCommands * 100.0 / totalHandledCommands;
        }

        return CommandReportSummaryDto.builder()
                .totalCommands(totalCommands)
                .completedCommands(completedCommands)
                .failedCommands(failedCommands)
                .pendingCommands(pendingCommands)
                .executingCommands(executingCommands)
                .canceledCommands(canceledCommands)
                .avgExecutionTimeMs(avgExecutionTimeMs)
                .successRate(successRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandDailyTrendDto> getCommandDailyTrend(String dateFrom, String dateTo, String commandType, String platform) {
        Instant from = parseDateToInstant(dateFrom);
        Instant to = parseDateToEndOfDay(dateTo);
        String normalizedPlatform = normalizePlatform(platform);

        List<Object[]> results = appleCommandRepository.getCommandDailyTrend(from, to, commandType, normalizedPlatform);

        return results.stream()
                .map(row -> CommandDailyTrendDto.builder()
                        .date(row[0] != null ? ((java.sql.Date) row[0]).toLocalDate() : null)
                        .total(((Number) row[1]).longValue())
                        .completed(((Number) row[2]).longValue())
                        .failed(((Number) row[3]).longValue())
                        .canceled(((Number) row[4]).longValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandTypeBreakdownDto> getCommandTypeBreakdown(String dateFrom, String dateTo, String platform) {
        Instant from = parseDateToInstant(dateFrom);
        Instant to = parseDateToEndOfDay(dateTo);
        String normalizedPlatform = normalizePlatform(platform);

        List<Object[]> results = appleCommandRepository.getCommandTypeBreakdown(from, to, normalizedPlatform);

        return results.stream()
                .map(row -> CommandTypeBreakdownDto.builder()
                        .commandType((String) row[0])
                        .total(((Number) row[1]).longValue())
                        .completed(((Number) row[2]).longValue())
                        .failed(((Number) row[3]).longValue())
                        .canceled(((Number) row[4]).longValue())
                        .pending(((Number) row[5]).longValue())
                        .avgExecutionTimeMs(row[6] != null ? ((Number) row[6]).doubleValue() : null)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedModel<CommandReportItemDto> getCommandReportItems(CommandReportRequestDto request) {
        Instant from = parseDateToInstant(request.getDateFrom());
        Instant to = parseDateToEndOfDay(request.getDateTo());
        String normalizedPlatform = normalizePlatform(request.getPlatform());
        String sortBy = normalizeCommandSortBy(request.getSortBy());

        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(100, request.getSize()));
        int offset = page * size;

        List<Object[]> results = appleCommandRepository.getCommandReportItems(
                from, to, request.getCommandType(), request.getStatus(), normalizedPlatform,
                sortBy, request.isSortDesc(), size, offset
        );

        if (results.isEmpty()) {
            return new PagedModel<>(Page.empty(PageRequest.of(page, size)));
        }

        long totalCount = ((Number) results.getFirst()[14]).longValue();

        List<CommandReportItemDto> items = results.stream()
                .map(this::mapToCommandReportItem)
                .toList();

        PageImpl<CommandReportItemDto> pageResult = new PageImpl<>(items, PageRequest.of(page, size), totalCount);
        return new PagedModel<>(pageResult);
    }

    // ─── Command User Stats ───

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommandUserStats(String dateFrom, String dateTo, int limit) {
        return appleCommandRepository.getCommandUserStats(dateFrom, dateTo, limit);
    }

    // ─── Bulk Command Detail ───

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getBulkCommandDetail(UUID bulkCommandId) {
        BulkCommand bulk = bulkCommandRepository.findById(bulkCommandId)
                .orElseThrow(() -> new EntityNotFoundException("BulkCommand", bulkCommandId.toString()));

        List<Object[]> summaryResult = appleCommandRepository.getBulkCommandSummary(bulkCommandId);
        if (summaryResult.isEmpty()) {
            throw new EntityNotFoundException("BulkCommand summary", bulkCommandId.toString());
        }

        Object[] summary = summaryResult.getFirst();
        long total = ((Number) summary[0]).longValue();
        long completed = ((Number) summary[1]).longValue();
        long failed = ((Number) summary[2]).longValue();
        long pending = ((Number) summary[3]).longValue();
        double successRate = total > 0 ? (double) completed / total * 100 : 0;

        List<Object[]> failureRows = appleCommandRepository.getBulkCommandFailureReasons(bulkCommandId);
        List<Map<String, Object>> failureReasons = failureRows.stream()
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reason", row[0]);
                    m.put("count", row[1]);
                    return m;
                })
                .toList();

        List<Object[]> timelineRows = appleCommandRepository.getBulkCommandTimeline(bulkCommandId);
        List<Map<String, Object>> timeline = timelineRows.stream()
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("minute", row[0]);
                    m.put("completed", row[1]);
                    m.put("failed", row[2]);
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", bulk.getId());
        result.put("commandType", bulk.getCommandType());
        result.put("totalDevices", bulk.getTotalDevices());
        result.put("initiatedBy", bulk.getInitiatedBy());
        result.put("payload", bulk.getPayload());
        result.put("createdAt", bulk.getCreatedAt());
        result.put("completed", completed);
        result.put("failed", failed);
        result.put("pending", pending);
        result.put("successRate", Math.round(successRate * 10) / 10.0);
        result.put("avgExecutionTimeMs", ((Number) summary[4]).longValue());
        result.put("minExecutionTimeMs", ((Number) summary[5]).longValue());
        result.put("maxExecutionTimeMs", ((Number) summary[6]).longValue());
        result.put("failureReasons", failureReasons);
        result.put("completionTimeline", timeline);

        return result;
    }

    // ─── Bulk Command Devices ───

    @Override
    @Transactional(readOnly = true)
    public PagedModel<Map<String, Object>> getBulkCommandDevices(UUID bulkCommandId, int page, int size, String status, String search) {
        bulkCommandRepository.findById(bulkCommandId)
                .orElseThrow(() -> new EntityNotFoundException("BulkCommand", bulkCommandId.toString()));

        long totalElements = appleCommandRepository.countBulkCommandDevices(bulkCommandId, status, search);
        int offset = page * size;
        List<Map<String, Object>> devices = appleCommandRepository.getBulkCommandDevices(bulkCommandId, status, search, size, offset);

        return new PagedModel<>(new PageImpl<>(devices, PageRequest.of(page, size), totalElements));
    }

    // ─── Device Activity ───

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> queryDeviceActivity(Map<String, Object> request) {
        String dateFrom = (String) request.get("dateFrom");
        String dateTo = (String) request.get("dateTo");

        List<Map<String, Object>> heatmap = agentPresenceHistoryRepository.getOnlineHeatmap(dateFrom, dateTo);
        List<Map<String, Object>> deviceUptime = agentPresenceHistoryRepository.getDeviceUptime(dateFrom, dateTo);

        // Compute total period seconds for uptime percentage
        long periodSeconds = 0;
        if (dateFrom != null && dateTo != null) {
            try {
                var from = Instant.parse(dateFrom);
                var to = Instant.parse(dateTo);
                periodSeconds = Duration.between(from, to).getSeconds();
            } catch (Exception ignored) {}
        }

        // Add uptimePercent to each device
        for (var row : deviceUptime) {
            long onlineSec = ((Number) row.get("total_online_seconds")).longValue();
            row.put("uptimePercent", periodSeconds > 0 ? Math.round((double) onlineSec / periodSeconds * 1000) / 10.0 : 0);
        }

        // Summary stats
        long totalDevices = deviceUptime.size();
        double avgUptimePercent = deviceUptime.stream()
                .mapToDouble(r -> ((Number) r.get("uptimePercent")).doubleValue())
                .average().orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDevices", totalDevices);
        result.put("avgUptimePercent", Math.round(avgUptimePercent * 10) / 10.0);
        result.put("periodSeconds", periodSeconds);
        result.put("heatmap", heatmap);
        result.put("deviceUptime", deviceUptime);
        return result;
    }

    // ─── Map Analysis ───

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> queryLocationsByArea(Map<String, Object> request) {
        String dateFrom = (String) request.get("dateFrom");
        String dateTo = (String) request.get("dateTo");
        String areaType = (String) request.get("areaType");
        Map<String, Object> geometry = (Map<String, Object>) request.get("geometry");

        var spatialConditions = new ArrayList<String>();
        var spatialParams = new ArrayList<Object>();

        if (areaType != null && geometry != null) {
            switch (areaType.toUpperCase()) {
                case "RECTANGLE" -> {
                    var bounds = (Map<String, Object>) geometry.get("bounds");
                    var sw = (List<Number>) bounds.get("sw");
                    var ne = (List<Number>) bounds.get("ne");
                    spatialConditions.add("al.latitude BETWEEN ? AND ? AND al.longitude BETWEEN ? AND ?");
                    spatialParams.add(sw.get(1).doubleValue());
                    spatialParams.add(ne.get(1).doubleValue());
                    spatialParams.add(sw.get(0).doubleValue());
                    spatialParams.add(ne.get(0).doubleValue());
                }
                case "CIRCLE" -> {
                    var center = (List<Number>) geometry.get("center");
                    double radius = ((Number) geometry.get("radius")).doubleValue();
                    double lng = center.get(0).doubleValue(), lat = center.get(1).doubleValue();
                    double latDelta = radius / 110540.0;
                    double lngDelta = radius / (111320.0 * Math.cos(Math.toRadians(lat)));
                    spatialConditions.add("al.latitude BETWEEN ? AND ? AND al.longitude BETWEEN ? AND ?");
                    spatialParams.add(lat - latDelta);
                    spatialParams.add(lat + latDelta);
                    spatialParams.add(lng - lngDelta);
                    spatialParams.add(lng + lngDelta);
                    spatialConditions.add("""
                            (6371000 * ACOS(LEAST(1.0,
                              SIN(RADIANS(?)) * SIN(RADIANS(al.latitude)) +
                              COS(RADIANS(?)) * COS(RADIANS(al.latitude)) * COS(RADIANS(al.longitude) - RADIANS(?))
                            ))) <= ?""");
                    spatialParams.add(lat);
                    spatialParams.add(lat);
                    spatialParams.add(lng);
                    spatialParams.add(radius);
                }
                case "POLYGON" -> {
                    var coordinates = (List<List<List<Number>>>) geometry.get("coordinates");
                    var ring = coordinates.getFirst();
                    double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
                    double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
                    for (var pt : ring) {
                        double pLng = pt.get(0).doubleValue(), pLat = pt.get(1).doubleValue();
                        minLat = Math.min(minLat, pLat);
                        maxLat = Math.max(maxLat, pLat);
                        minLng = Math.min(minLng, pLng);
                        maxLng = Math.max(maxLng, pLng);
                    }
                    spatialConditions.add("al.latitude BETWEEN ? AND ? AND al.longitude BETWEEN ? AND ?");
                    spatialParams.add(minLat);
                    spatialParams.add(maxLat);
                    spatialParams.add(minLng);
                    spatialParams.add(maxLng);
                }
            }
        }

        int limit = request.containsKey("limit") ? ((Number) request.get("limit")).intValue() : 5000;

        List<Map<String, Object>> locations = agentLocationRepository.queryLocations(
                dateFrom, dateTo, spatialConditions, spatialParams, limit);

        // Polygon post-filter (ray-casting point-in-polygon)
        if ("POLYGON".equalsIgnoreCase(areaType) && geometry != null) {
            var coordinates = (List<List<List<Number>>>) geometry.get("coordinates");
            var ring = coordinates.getFirst();
            double[][] polygon = ring.stream()
                    .map(pt -> new double[]{pt.get(0).doubleValue(), pt.get(1).doubleValue()})
                    .toArray(double[][]::new);
            locations = locations.stream().filter(loc -> {
                double lngVal = ((Number) loc.get("longitude")).doubleValue();
                double latVal = ((Number) loc.get("latitude")).doubleValue();
                return pointInPolygon(lngVal, latVal, polygon);
            }).toList();
        }

        // Group by device for summary
        var deviceSummary = new LinkedHashMap<String, Map<String, Object>>();
        for (var loc : locations) {
            String devId = (String) loc.get("device_identifier");
            deviceSummary.computeIfAbsent(devId, k -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("deviceIdentifier", devId);
                m.put("serialNumber", loc.get("serial_number"));
                m.put("productName", loc.get("product_name"));
                m.put("pointCount", 0);
                return m;
            });
            deviceSummary.get(devId).merge("pointCount", 1, (a, b) -> ((int) a) + ((int) b));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPoints", locations.size());
        result.put("totalDevices", deviceSummary.size());
        result.put("locations", locations);
        result.put("deviceSummary", new ArrayList<>(deviceSummary.values()));
        return result;
    }

    /** Ray-casting point-in-polygon test */
    private boolean pointInPolygon(double x, double y, double[][] polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double xi = polygon[i][0], yi = polygon[i][1];
            double xj = polygon[j][0], yj = polygon[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private CommandReportItemDto mapToCommandReportItem(Object[] row) {
        // Handle BigDecimal for executionDurationMs
        Long duration = null;
        if (row[10] instanceof BigDecimal bd) {
            duration = bd.longValue();
        } else if (row[10] instanceof Number n) {
            duration = n.longValue();
        }

        return CommandReportItemDto.builder()
                .id(row[0] != null ? UUID.fromString(row[0].toString()) : null)
                .commandType((String) row[1])
                .status((String) row[2])
                .deviceId(row[3] != null ? UUID.fromString(row[3].toString()) : null)
                .serialNumber((String) row[4])
                .productName((String) row[5])
                .platform((String) row[6])
                .requestTime(toInstant(row[7]))
                .executionTime(toInstant(row[8]))
                .completionTime(toInstant(row[9]))
                .executionDurationMs(duration)
                .failureReason((String) row[11])
                .policyId(row[12] != null ? UUID.fromString(row[12].toString()) : null)
                .policyName((String) row[13])
                // row[14] = total_count (window function)
                .bulkCommandId(row.length > 15 && row[15] != null ? UUID.fromString(row[15].toString()) : null)
                .createdBy(row.length > 16 ? (String) row[16] : null)
                .build();
    }

    private AppDeviceReportDto mapToDeviceReport(Object[] row) {
        return AppDeviceReportDto.builder()
                .deviceId(row[0] != null ? row[0].toString() : null)
                .serialNumber((String) row[1])
                .udid((String) row[2])
                .productName((String) row[3])
                .osVersion((String) row[4])
                .enrollmentType(row[5] != null ? row[5].toString() : null)
                .status(row[6] != null ? row[6].toString() : null)
                .agentOnline(row[7] != null && (Boolean) row[7])
                .agentLastSeenAt(toDate(row[8]))
                .creationDate(toDate(row[9]))
                .platform((String) row[10])
                .appName((String) row[11])
                .appVersion((String) row[12])
                .appShortVersion((String) row[13])
                .bundleIdentifier((String) row[14])
                .isManaged(row[15] != null && (Boolean) row[15])
                .bundleSize(row[16] != null ? ((Number) row[16]).intValue() : null)
                .deviceName(row.length > 18 ? (String) row[18] : null)
                .build();
    }

    private Date toDate(Object value) {
        if (value instanceof Timestamp ts) {
            return new Date(ts.getTime());
        }
        if (value instanceof Date d) {
            return d;
        }
        return null;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof Date d) {
            return d.toInstant();
        }
        return null;
    }

    private Instant parseDateToInstant(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateString).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_DATE_FORMAT", "Invalid date format: '" + dateString + "'. Expected format: YYYY-MM-DD");
        }
    }

    /**
     * Parse dateTo as end-of-day (start of next day) so that records from the dateTo day are included.
     * SQL uses strict {@code <} comparison, so "2026-03-14" becomes "2026-03-15T00:00:00Z".
     */
    private Instant parseDateToEndOfDay(String dateString) {
        Instant instant = parseDateToInstant(dateString);
        return instant != null ? instant.plus(1, ChronoUnit.DAYS) : null;
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return switch (platform.toLowerCase()) {
            case "ios" -> "iOS";
            case "macos" -> "macOS";
            case "tvos" -> "tvOS";
            case "watchos" -> "watchOS";
            case "visionos" -> "visionOS";
            default -> throw new BusinessException("INVALID_PLATFORM",
                    "Invalid platform: '" + platform + "'. Allowed values: iOS, macOS, tvOS, watchOS, visionOS");
        };
    }

    private String normalizeAppSortBy(String sortBy) {
        if (sortBy == null || !VALID_APP_SORT_FIELDS.contains(sortBy)) {
            return "creationDate";
        }
        return sortBy;
    }

    private String normalizeCommandSortBy(String sortBy) {
        if (sortBy == null || !VALID_COMMAND_SORT_FIELDS.contains(sortBy)) {
            return "requestTime";
        }
        return sortBy;
    }

    // ─── Fleet Health ───

    @Override
    @Transactional(readOnly = true)
    public FleetHealthSummaryDto getFleetHealthSummary(String platform) {
        String normalizedPlatform = normalizePlatform(platform);

        List<Object[]> summaryResult = agentTelemetryRepository.getFleetHealthSummary(normalizedPlatform);

        if (summaryResult.isEmpty() || summaryResult.getFirst()[0] == null) {
            return FleetHealthSummaryDto.builder()
                    .totalDevicesWithTelemetry(0)
                    .avgBatteryLevel(0)
                    .lowBatteryCount(0)
                    .criticalStorageCount(0)
                    .thermalWarningCount(0)
                    .networkDistribution(Map.of())
                    .batteryDistribution(List.of())
                    .storageDistribution(List.of())
                    .build();
        }

        Object[] row = summaryResult.getFirst();
        long total = ((Number) row[0]).longValue();
        double avgBattery = ((Number) row[1]).doubleValue();
        long lowBattery = ((Number) row[2]).longValue();
        long criticalStorage = ((Number) row[3]).longValue();
        long thermalWarning = ((Number) row[4]).longValue();

        Map<String, Long> networkDist = new LinkedHashMap<>();
        networkDist.put("wifi", ((Number) row[5]).longValue());
        networkDist.put("cellular", ((Number) row[6]).longValue());
        networkDist.put("ethernet", ((Number) row[7]).longValue());
        networkDist.put("none", ((Number) row[8]).longValue());

        List<FleetHealthSummaryDto.DistributionBucketDto> batteryDist = buildDistribution(
                agentTelemetryRepository.getFleetBatteryDistribution(normalizedPlatform));
        List<FleetHealthSummaryDto.DistributionBucketDto> storageDist = buildDistribution(
                agentTelemetryRepository.getFleetStorageDistribution(normalizedPlatform));

        return FleetHealthSummaryDto.builder()
                .totalDevicesWithTelemetry(total)
                .avgBatteryLevel(avgBattery)
                .lowBatteryCount(lowBattery)
                .criticalStorageCount(criticalStorage)
                .thermalWarningCount(thermalWarning)
                .networkDistribution(networkDist)
                .batteryDistribution(batteryDist)
                .storageDistribution(storageDist)
                .build();
    }

    private List<FleetHealthSummaryDto.DistributionBucketDto> buildDistribution(List<Object[]> rows) {
        Map<Integer, Long> bucketMap = new HashMap<>();
        for (Object[] row : rows) {
            int bucket = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            bucketMap.put(bucket, count);
        }
        List<FleetHealthSummaryDto.DistributionBucketDto> result = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            result.add(FleetHealthSummaryDto.DistributionBucketDto.builder()
                    .range(BUCKET_RANGES[i - 1])
                    .count(bucketMap.getOrDefault(i, 0L))
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedModel<FleetHealthDeviceDto> getFleetHealthDevices(FleetHealthDeviceRequestDto request) {
        String normalizedPlatform = normalizePlatform(request.getPlatform());
        String sortBy = normalizeFleetSortBy(request.getSortBy());
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(100, request.getSize()));
        int offset = page * size;

        List<Object[]> results = agentTelemetryRepository.getFleetHealthDevices(
                normalizedPlatform, sortBy, request.isSortDesc(), size, offset);

        if (results.isEmpty()) {
            return new PagedModel<>(Page.empty(PageRequest.of(page, size)));
        }

        long totalCount = ((Number) results.getFirst()[15]).longValue();

        List<FleetHealthDeviceDto> items = results.stream()
                .map(row -> FleetHealthDeviceDto.builder()
                        .deviceId(row[0] != null ? row[0].toString() : null)
                        .serialNumber((String) row[1])
                        .productName((String) row[2])
                        .platform((String) row[3])
                        .batteryLevel(row[4] != null ? ((Number) row[4]).intValue() : 0)
                        .batteryCharging(row[5] != null && (Boolean) row[5])
                        .batteryState(row[6] != null ? row[6].toString() : null)
                        .storageTotalBytes(row[7] != null ? ((Number) row[7]).longValue() : 0)
                        .storageUsedBytes(row[8] != null ? ((Number) row[8]).longValue() : 0)
                        .storageUsagePercent(row[9] != null ? ((Number) row[9]).intValue() : 0)
                        .thermalState(row[10] != null ? row[10].toString() : null)
                        .networkType(row[11] != null ? row[11].toString() : null)
                        .wifiSsid((String) row[12])
                        .vpnActive(row[13] != null && (Boolean) row[13])
                        .agentLastSeenAt(toInstant(row[14]))
                        .build())
                .toList();

        return new PagedModel<>(new PageImpl<>(items, PageRequest.of(page, size), totalCount));
    }

    private String normalizeFleetSortBy(String sortBy) {
        if (sortBy == null || !VALID_FLEET_SORT_FIELDS.contains(sortBy)) {
            return "batteryLevel";
        }
        return sortBy;
    }

    // ─── Compliance ───

    @Override
    @Transactional(readOnly = true)
    public ComplianceSummaryDto getComplianceSummary(String platform) {
        String normalizedPlatform = normalizePlatform(platform);

        List<Object[]> result = appleDeviceRepository.getComplianceSummary(normalizedPlatform);

        if (result.isEmpty() || result.getFirst()[0] == null) {
            return ComplianceSummaryDto.builder()
                    .totalDevices(0).compliantCount(0).nonCompliantCount(0).noPolicyCount(0)
                    .complianceRate(0.0).topFailureReasons(List.of()).build();
        }

        Object[] row = result.getFirst();
        long total = ((Number) row[0]).longValue();
        long compliant = ((Number) row[1]).longValue();
        long nonCompliant = ((Number) row[2]).longValue();
        long noPolicy = ((Number) row[3]).longValue();

        double complianceRate = 0.0;
        long handled = compliant + nonCompliant;
        if (handled > 0) {
            complianceRate = compliant * 100.0 / handled;
        }

        // Top failure reasons from compliance_failures JSONB
        List<ComplianceSummaryDto.ComplianceFailureReasonDto> topFailures = aggregateComplianceFailures(normalizedPlatform);

        return ComplianceSummaryDto.builder()
                .totalDevices(total)
                .compliantCount(compliant)
                .nonCompliantCount(nonCompliant)
                .noPolicyCount(noPolicy)
                .complianceRate(complianceRate)
                .topFailureReasons(topFailures)
                .build();
    }

    private List<ComplianceSummaryDto.ComplianceFailureReasonDto> aggregateComplianceFailures(String platform) {
        List<String> failuresJsonList = appleDeviceRepository.getComplianceFailuresJson(platform);
        Map<String, Long> failureCounts = new HashMap<>();

        for (String json : failuresJsonList) {
            try {
                Map<String, Object> failures = objectMapper.readValue(json, new TypeReference<>() {});
                for (String key : failures.keySet()) {
                    failureCounts.merge(key, 1L, Long::sum);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse compliance_failures JSON: {}", json, e);
            }
        }

        return failureCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> ComplianceSummaryDto.ComplianceFailureReasonDto.builder()
                        .reason(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedModel<ComplianceDeviceDto> getComplianceDevices(ComplianceDeviceRequestDto request) {
        String normalizedPlatform = normalizePlatform(request.getPlatform());
        String complianceStatus = request.getComplianceStatus() != null ? request.getComplianceStatus() : "ALL";
        String sortBy = normalizeComplianceSortBy(request.getSortBy());
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(100, request.getSize()));
        int offset = page * size;

        List<Object[]> results = appleDeviceRepository.getComplianceDevices(
                complianceStatus, normalizedPlatform, sortBy, request.isSortDesc(), size, offset);

        if (results.isEmpty()) {
            return new PagedModel<>(Page.empty(PageRequest.of(page, size)));
        }

        long totalCount = ((Number) results.getFirst()[10]).longValue();

        List<ComplianceDeviceDto> items = results.stream()
                .map(this::mapToComplianceDevice)
                .toList();

        return new PagedModel<>(new PageImpl<>(items, PageRequest.of(page, size), totalCount));
    }

    private ComplianceDeviceDto mapToComplianceDevice(Object[] row) {
        List<ComplianceDeviceDto.ComplianceFailureItemDto> failures = List.of();
        if (row[7] != null) {
            try {
                Map<String, String> failuresMap = objectMapper.readValue(row[7].toString(), new TypeReference<>() {});
                failures = failuresMap.entrySet().stream()
                        .map(e -> ComplianceDeviceDto.ComplianceFailureItemDto.builder()
                                .commandType(e.getKey())
                                .failureReason(e.getValue())
                                .build())
                        .toList();
            } catch (Exception e) {
                logger.warn("Failed to parse compliance_failures: {}", row[7], e);
            }
        }

        return ComplianceDeviceDto.builder()
                .deviceId(row[0] != null ? row[0].toString() : null)
                .serialNumber((String) row[1])
                .productName((String) row[2])
                .platform((String) row[3])
                .osVersion((String) row[4])
                .enrollmentType(row[5] != null ? row[5].toString() : null)
                .isCompliant(row[6] != null && (Boolean) row[6])
                .complianceFailures(failures)
                .appliedPolicyName((String) row[8])
                .lastModifiedDate(toInstant(row[9]))
                .build();
    }

    private String normalizeComplianceSortBy(String sortBy) {
        if (sortBy == null || !VALID_COMPLIANCE_SORT_FIELDS.contains(sortBy)) {
            return "productName";
        }
        return sortBy;
    }

    // ─── Enrollment ───

    @Override
    @Transactional(readOnly = true)
    public EnrollmentSummaryDto getEnrollmentSummary(String dateFrom, String dateTo, String platform) {
        Instant from = parseDateToInstant(dateFrom);
        Instant to = parseDateToEndOfDay(dateTo);
        String normalizedPlatform = normalizePlatform(platform);

        // Total active devices
        long totalActive = appleDeviceRepository.countByStatusNot("DELETED");

        // New enrollments and unenrollments in date range
        List<Object[]> counts = enrollmentHistoryRepository.getEnrollmentCounts(from, to, normalizedPlatform);
        long newEnrollments = 0;
        long unenrollments = 0;
        if (!counts.isEmpty() && counts.getFirst() != null) {
            newEnrollments = ((Number) counts.getFirst()[0]).longValue();
            unenrollments = ((Number) counts.getFirst()[1]).longValue();
        }

        // Type distribution
        List<Object[]> typeDist = enrollmentHistoryRepository.getEnrollmentTypeDistribution(normalizedPlatform);
        List<EnrollmentSummaryDto.EnrollmentTypeDistributionDto> typeDistribution = typeDist.stream()
                .map(row -> EnrollmentSummaryDto.EnrollmentTypeDistributionDto.builder()
                        .enrollmentType(row[0] != null ? row[0].toString() : "UNKNOWN")
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        return EnrollmentSummaryDto.builder()
                .totalActiveDevices(totalActive)
                .newEnrollments(newEnrollments)
                .unenrollments(unenrollments)
                .netChange(newEnrollments - unenrollments)
                .typeDistribution(typeDistribution)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentDailyTrendDto> getEnrollmentDailyTrend(String dateFrom, String dateTo, String platform) {
        Instant from = parseDateToInstant(dateFrom);
        Instant to = parseDateToEndOfDay(dateTo);
        String normalizedPlatform = normalizePlatform(platform);

        List<Object[]> results = enrollmentHistoryRepository.getEnrollmentDailyTrend(from, to, normalizedPlatform);

        return results.stream()
                .map(row -> EnrollmentDailyTrendDto.builder()
                        .date(row[0] != null ? ((java.sql.Date) row[0]).toLocalDate() : null)
                        .enrollments(((Number) row[1]).longValue())
                        .unenrollments(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedModel<EnrollmentHistoryItemDto> getEnrollmentHistory(EnrollmentHistoryRequestDto request) {
        Instant from = parseDateToInstant(request.getDateFrom());
        Instant to = parseDateToEndOfDay(request.getDateTo());
        String normalizedPlatform = normalizePlatform(request.getPlatform());
        String sortBy = normalizeEnrollmentSortBy(request.getSortBy());
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(100, request.getSize()));
        int offset = page * size;

        List<Object[]> results = enrollmentHistoryRepository.getEnrollmentHistoryItems(
                from, to, request.getEnrollmentType(), normalizedPlatform,
                sortBy, request.isSortDesc(), size, offset);

        if (results.isEmpty()) {
            return new PagedModel<>(Page.empty(PageRequest.of(page, size)));
        }

        long totalCount = ((Number) results.getFirst()[11]).longValue();

        List<EnrollmentHistoryItemDto> items = results.stream()
                .map(row -> EnrollmentHistoryItemDto.builder()
                        .id(row[0] != null ? row[0].toString() : null)
                        .deviceId(row[1] != null ? row[1].toString() : null)
                        .serialNumber((String) row[2])
                        .productName((String) row[3])
                        .platform((String) row[4])
                        .osVersion((String) row[5])
                        .enrollmentType(row[6] != null ? row[6].toString() : null)
                        .status(row[7] != null ? row[7].toString() : null)
                        .enrolledAt(toInstant(row[8]))
                        .unenrolledAt(toInstant(row[9]))
                        .unenrollReason((String) row[10])
                        .build())
                .toList();

        return new PagedModel<>(new PageImpl<>(items, PageRequest.of(page, size), totalCount));
    }

    private String normalizeEnrollmentSortBy(String sortBy) {
        if (sortBy == null || !VALID_ENROLLMENT_SORT_FIELDS.contains(sortBy)) {
            return "enrolledAt";
        }
        return sortBy;
    }

    // ─── Security Posture ───

    @Override
    @Transactional(readOnly = true)
    public SecuritySummaryDto getSecuritySummary(String platform) {
        String normalizedPlatform = normalizePlatform(platform);

        List<Object[]> infoResult = appleDeviceInformationRepository.getSecuritySummary(normalizedPlatform);
        List<Object[]> telemetryResult = agentTelemetryRepository.getSecurityTelemetryCounts(normalizedPlatform);

        long total = 0, supervised = 0, activationLock = 0, cloudBackup = 0, findMy = 0, appAnalytics = 0, diagnosticSubmission = 0;
        if (!infoResult.isEmpty() && infoResult.getFirst()[0] != null) {
            Object[] row = infoResult.getFirst();
            total = ((Number) row[0]).longValue();
            supervised = ((Number) row[1]).longValue();
            activationLock = ((Number) row[2]).longValue();
            cloudBackup = ((Number) row[3]).longValue();
            findMy = ((Number) row[4]).longValue();
            appAnalytics = ((Number) row[5]).longValue();
            diagnosticSubmission = ((Number) row[6]).longValue();
        }

        long jailbreak = 0, vpn = 0;
        if (!telemetryResult.isEmpty() && telemetryResult.getFirst()[0] != null) {
            jailbreak = ((Number) telemetryResult.getFirst()[0]).longValue();
            vpn = ((Number) telemetryResult.getFirst()[1]).longValue();
        }

        return SecuritySummaryDto.builder()
                .totalDevices(total)
                .supervisedCount(supervised)
                .activationLockCount(activationLock)
                .cloudBackupCount(cloudBackup)
                .findMyCount(findMy)
                .jailbreakCount(jailbreak)
                .vpnActiveCount(vpn)
                .passcodeCompliantCount(total) // all considered compliant by default
                .appAnalyticsCount(appAnalytics)
                .diagnosticSubmissionCount(diagnosticSubmission)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedModel<SecurityDeviceDto> getSecurityDevices(SecurityDeviceRequestDto request) {
        String normalizedPlatform = normalizePlatform(request.getPlatform());
        String filter = request.getFilter();
        String sortBy = normalizeSecuritySortBy(request.getSortBy());
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(100, request.getSize()));
        int offset = page * size;

        List<Object[]> results = appleDeviceInformationRepository.getSecurityDevices(
                normalizedPlatform, filter, sortBy, request.isSortDesc(), size, offset);

        if (results.isEmpty()) {
            return new PagedModel<>(Page.empty(PageRequest.of(page, size)));
        }

        long totalCount = ((Number) results.getFirst()[12]).longValue();

        List<SecurityDeviceDto> items = results.stream()
                .map(row -> SecurityDeviceDto.builder()
                        .deviceId(row[0] != null ? row[0].toString() : null)
                        .serialNumber((String) row[1])
                        .productName((String) row[2])
                        .platform((String) row[3])
                        .osVersion((String) row[4])
                        .supervised(row[5] != null && (Boolean) row[5])
                        .activationLockEnabled(row[6] != null && (Boolean) row[6])
                        .cloudBackupEnabled(row[7] != null && (Boolean) row[7])
                        .findMyEnabled(row[8] != null && (Boolean) row[8])
                        .jailbreakDetected(row[9] != null && (Boolean) row[9])
                        .vpnActive(row[10] != null && (Boolean) row[10])
                        .passcodeCompliant(row[11] != null && (Boolean) row[11])
                        .build())
                .toList();

        return new PagedModel<>(new PageImpl<>(items, PageRequest.of(page, size), totalCount));
    }

    private String normalizeSecuritySortBy(String sortBy) {
        if (sortBy == null || !VALID_SECURITY_SORT_FIELDS.contains(sortBy)) {
            return "productName";
        }
        return sortBy;
    }
}
