package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentActivityLogService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentPushService;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceLookupService;
import com.arcyintel.arcops.apple_mdm.models.session.VncSession;
import com.arcyintel.arcops.apple_mdm.services.vnc.VncSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.arcyintel.arcops.commons.license.RequiresFeature;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_START_VNC;
import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_STOP_VNC;

@RestController
@RequestMapping("/vnc")
@RequiredArgsConstructor
@Tag(name = "VNC Remote Desktop", description = "VNC-based remote desktop session management for macOS devices")
@RequiresFeature("REMOTE_ACCESS")
public class VncController {

    private static final Logger logger = LoggerFactory.getLogger(VncController.class);

    private final VncSessionService sessionService;
    private final AgentCommandService agentCommandService;
    private final DeviceLookupService deviceLookupService;
    private final AgentPushService agentPushService;
    private final AgentActivityLogService agentActivityLogService;

    @Operation(summary = "Start VNC remote desktop session",
            description = "Creates a VNC tunnel session and sends start_vnc command to the macOS device via MQTT. " +
                    "If the device is offline but has a push token, sends a wake-up push and returns 202 Accepted.")
    @PostMapping("/start/{udid}")
    public ResponseEntity<?> startSession(@PathVariable String udid) {
        AppleDevice device = deviceLookupService.findByUdid(udid)
                .orElseThrow(() -> new EntityNotFoundException("Device", udid));

        // VNC is only supported on macOS
        if (!"macos".equalsIgnoreCase(device.getAgentPlatform())) {
            throw new IllegalArgumentException("VNC remote desktop is only supported on macOS devices");
        }

        // Device is online — proceed immediately
        if (Boolean.TRUE.equals(device.getAgentOnline())) {
            VncSession session = sessionService.createSession(udid);
            agentCommandService.sendCommand(udid, CMD_START_VNC, Map.of(
                    "sessionId", session.sessionId()
            ));
            agentActivityLogService.logStart(udid, "VNC_REMOTE_DESKTOP", session.sessionId(),
                    Map.of(), "admin");
            return ResponseEntity.ok(Map.of("sessionId", session.sessionId()));
        }

        // Device is offline — send alert push
        String agentPushToken = device.getAgentPushToken();
        if (agentPushService.isEnabled() && agentPushToken != null && !agentPushToken.isBlank()) {
            VncSession session = sessionService.createSession(udid);
            sessionService.updateState(session.sessionId(), VncSession.STATE_WAKING);

            agentPushService.sendAlertNotification(agentPushToken, device.getAgentPlatform(),
                    "Remote Desktop Request",
                    "Administrator is requesting remote desktop access",
                    Map.of("type", "vnc_remote_desktop", "sessionId", session.sessionId()));
            agentActivityLogService.logStart(udid, "VNC_REMOTE_DESKTOP", session.sessionId(),
                    Map.of(), "admin");

            logger.info("VNC session queued + alert push sent for offline device {}", udid);
            return ResponseEntity.accepted().body(Map.of(
                    "sessionId", session.sessionId(),
                    "status", "waking",
                    "message", "Device is offline. Alert push sent for remote desktop request."
            ));
        }

        throw new IllegalStateException("Device agent is not online and cannot be woken up (no push token available)");
    }

    @Operation(summary = "Stop VNC remote desktop session",
            description = "Sends stop command to the device and ends the session.")
    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<?> stopSession(@PathVariable String sessionId) {
        VncSession session = sessionService.getSession(sessionId);
        if (session == null) {
            // Session already ended (viewer disconnect or TTL expired) — idempotent OK
            return ResponseEntity.ok(Map.of("status", "already_stopped"));
        }

        agentCommandService.sendCommand(session.deviceUdid(), CMD_STOP_VNC,
                Map.of("sessionId", sessionId));
        sessionService.endSession(sessionId);
        agentActivityLogService.logComplete(sessionId);

        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @Operation(summary = "Get VNC session status")
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> getStatus(@PathVariable String sessionId) {
        VncSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new EntityNotFoundException("VncSession", sessionId);
        }

        return ResponseEntity.ok(Map.of(
                "sessionId", session.sessionId(),
                "deviceUdid", session.deviceUdid(),
                "state", session.state(),
                "createdAt", session.createdAt(),
                "updatedAt", session.updatedAt()
        ));
    }
}
