package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.repositories.DeviceAuthHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth-analytics")
@RequiredArgsConstructor
@Tag(name = "Agent Auth Analytics")
public class AgentAuthAnalyticsController {

    private final DeviceAuthHistoryRepository repository;

    @GetMapping
    @Operation(summary = "Get agent auth analytics")
    public ResponseEntity<?> getAuthAnalytics(@RequestParam(defaultValue = "7") int days) {
        String from = Instant.now().minus(Duration.ofDays(days)).toString();

        // Count by event type
        var eventCounts = repository.countByEventType(from);
        long signIn = 0, signInFailed = 0, signOut = 0, otpFailed = 0;
        for (Object[] row : eventCounts) {
            String type = (String) row[0];
            long count = ((Number) row[1]).longValue();
            switch (type) {
                case "SIGN_IN" -> signIn = count;
                case "SIGN_IN_FAILED" -> signInFailed = count;
                case "SIGN_OUT" -> signOut = count;
                case "OTP_FAILED" -> otpFailed = count;
            }
        }

        long uniqueDevices = repository.countUniqueDevices(from);

        var dailyTrend = repository.findDailyTrend(from).stream()
                .map(r -> Map.of("date", r[0].toString(), "eventType", r[1], "count", ((Number) r[2]).longValue()))
                .toList();

        var topFailedIps = repository.findTopFailedIps(from, 10).stream()
                .map(r -> Map.of("ip", r[0], "count", ((Number) r[1]).longValue()))
                .toList();

        var recentEvents = repository.findByCreatedAtAfterOrderByCreatedAtDesc(
                Instant.now().minus(Duration.ofDays(days)),
                PageRequest.of(0, 20)
        ).getContent().stream().map(e -> Map.of(
                "username", e.getUsername() != null ? e.getUsername() : "",
                "deviceIdentifier", e.getDeviceIdentifier() != null ? e.getDeviceIdentifier() : "",
                "eventType", e.getEventType(),
                "ip", e.getIpAddress() != null ? e.getIpAddress() : "",
                "agentVersion", e.getAgentVersion() != null ? e.getAgentVersion() : "",
                "failureReason", e.getFailureReason() != null ? e.getFailureReason() : "",
                "timestamp", e.getCreatedAt().toString()
        )).toList();

        return ResponseEntity.ok(Map.of(
                "summary", Map.of(
                        "totalSignIn", signIn,
                        "totalFailed", signInFailed,
                        "totalSignOut", signOut,
                        "totalOtpFailed", otpFailed,
                        "uniqueDevices", uniqueDevices
                ),
                "dailyTrend", dailyTrend,
                "topFailedIps", topFailedIps,
                "recentEvents", recentEvents
        ));
    }

    @GetMapping("/auth-log")
    @Operation(summary = "Paginated, filterable auth event log with device info")
    public ResponseEntity<?> getAuthLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        var result = repository.findFilteredWithDevice(username, ip, eventType, dateFrom, dateTo,
                PageRequest.of(page, size));

        // Column order: 0:id, 1:username, 2:device_identifier, 3:event_type,
        // 4:ip_address, 5:agent_version, 6:failure_reason, 7:created_at, 8:auth_source,
        // 9:product_name, 10:model_name, 11:model
        var content = result.getContent().stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row[0] != null ? row[0].toString() : "");
            m.put("username", row[1] != null ? row[1].toString() : "");
            m.put("deviceIdentifier", row[2] != null ? row[2].toString() : "");
            m.put("eventType", row[3] != null ? row[3].toString() : "");
            m.put("ip", row[4] != null ? row[4].toString() : "");
            m.put("agentVersion", row[5] != null ? row[5].toString() : "");
            m.put("failureReason", row[6] != null ? row[6].toString() : "");
            m.put("timestamp", row[7] != null ? row[7].toString() : "");
            m.put("authSource", row[8] != null ? row[8].toString() : "");
            m.put("productName", row[9] != null ? row[9].toString() : "");
            m.put("modelName", row[10] != null ? row[10].toString() : "");
            m.put("model", row[11] != null ? row[11].toString() : "");
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "content", content,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "totalElements", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )
        ));
    }
}
