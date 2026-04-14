package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.models.api.device.BulkCommandResponse;
import com.arcyintel.arcops.apple_mdm.models.api.device.BulkNotificationRequest;
import com.arcyintel.arcops.apple_mdm.models.api.device.BulkNotificationResponse;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentActivityLogService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceLookupService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentPushService;
import com.arcyintel.arcops.apple_mdm.services.agent.PendingNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_SEND_NOTIFICATION;

@RestController
@RequestMapping("/agent/notifications")
@RequiredArgsConstructor
@Tag(name = "Agent Notifications", description = "Send push notifications to agent devices")
public class AgentNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(AgentNotificationController.class);

    private final AgentCommandService agentCommandService;
    private final AgentPushService agentPushService;
    private final PendingNotificationService pendingNotificationService;
    private final DeviceLookupService deviceLookupService;
    private final AgentActivityLogService agentActivityLogService;

    @Operation(summary = "Send a notification to a device",
            description = "Sends a visible notification via MQTT if agent is online, or queues it for delivery when agent reconnects.")
    @PostMapping("/{udid}/send")
    public ResponseEntity<?> sendNotification(
            @PathVariable String udid,
            @RequestBody SendNotificationRequest request) {

        AppleDevice device = deviceLookupService.findByUdid(udid)
                .orElseThrow(() -> new EntityNotFoundException("Device", udid));

        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        Map<String, Object> payload = Map.of(
                "title", request.title(),
                "body", request.body() != null ? request.body() : "",
                "category", request.category() != null ? request.category() : "info"
        );

        if (Boolean.TRUE.equals(device.getAgentOnline())) {
            var command = agentCommandService.sendCommand(udid, CMD_SEND_NOTIFICATION, payload);
            logger.info("Notification sent via MQTT to device {} — commandId={}", udid, command.getCommandUuid());
            agentActivityLogService.logNotification(udid, payload, "mqtt", "admin");
            return ResponseEntity.ok(Map.of(
                    "commandId", command.getCommandUuid(),
                    "status", command.getStatus(),
                    "channel", "mqtt"
            ));
        } else {
            // Queue in Redis for in-app notification list when app opens
            pendingNotificationService.queueNotification(udid, payload);

            // Send visible alert push so iOS displays the notification regardless of app state
            String agentPushToken = device.getAgentPushToken();
            boolean pushSent = false;
            if (agentPushService.isEnabled() && agentPushToken != null && !agentPushToken.isBlank()) {
                agentPushService.sendAlertNotification(agentPushToken, device.getAgentPlatform(),
                        request.title(),
                        request.body() != null ? request.body() : "",
                        Map.of("type", "notification"));
                pushSent = true;
                logger.info("Notification queued + alert push sent for offline device {}", udid);
            } else {
                logger.info("Notification queued for offline device {} (no push available)", udid);
            }

            agentActivityLogService.logNotification(udid, payload,
                    pushSent ? "pending_with_push" : "pending", "admin");
            return ResponseEntity.accepted().body(Map.of(
                    "status", "queued",
                    "channel", pushSent ? "pending_with_push" : "pending",
                    "message", pushSent
                            ? "Device is offline. Alert push sent — notification will appear on device."
                            : "Device is offline. Notification will be delivered when agent reconnects."
            ));
        }
    }

    public record SendNotificationRequest(String title, String body, String category) {}

    @Operation(summary = "Send bulk notification",
            description = "Sends a notification to multiple devices. Online devices receive via MQTT, offline devices are queued with optional APNs push.")
    @PostMapping("/bulk/send")
    public ResponseEntity<BulkNotificationResponse> bulkSendNotification(
            @Valid @RequestBody BulkNotificationRequest request) {

        int total = request.getUdids().size();
        int sent = 0;
        int queued = 0;
        int failed = 0;
        List<BulkCommandResponse.FailureDetail> failures = new ArrayList<>();

        Map<String, Object> payload = Map.of(
                "title", request.getTitle(),
                "body", request.getBody() != null ? request.getBody() : "",
                "category", request.getCategory() != null ? request.getCategory() : "info"
        );

        for (String udid : request.getUdids()) {
            AppleDevice device = deviceLookupService.findByUdid(udid).orElse(null);
            if (device == null) {
                failed++;
                failures.add(BulkCommandResponse.FailureDetail.builder()
                        .udid(udid).reason("Device not found").build());
                continue;
            }

            try {
                if (Boolean.TRUE.equals(device.getAgentOnline())) {
                    agentCommandService.sendCommand(udid, CMD_SEND_NOTIFICATION, payload);
                    agentActivityLogService.logNotification(udid, payload, "mqtt", "admin");
                    sent++;
                } else {
                    pendingNotificationService.queueNotification(udid, payload);
                    String agentPushToken = device.getAgentPushToken();
                    boolean pushSent = false;
                    if (agentPushService.isEnabled() && agentPushToken != null && !agentPushToken.isBlank()) {
                        agentPushService.sendAlertNotification(agentPushToken, device.getAgentPlatform(),
                                request.getTitle(),
                                request.getBody() != null ? request.getBody() : "",
                                Map.of("type", "notification"));
                        pushSent = true;
                    }
                    agentActivityLogService.logNotification(udid, payload,
                            pushSent ? "pending_with_push" : "pending", "admin");
                    queued++;
                }
            } catch (Exception e) {
                failed++;
                failures.add(BulkCommandResponse.FailureDetail.builder()
                        .udid(udid).reason(e.getMessage()).build());
                logger.warn("Bulk notification failed for UDID {}: {}", udid, e.getMessage());
            }
        }

        logger.info("Bulk notification: total={}, sent={}, queued={}, failed={}", total, sent, queued, failed);
        return ResponseEntity.ok(BulkNotificationResponse.builder()
                .total(total).sent(sent).queued(queued).failed(failed).failures(failures)
                .build());
    }
}
