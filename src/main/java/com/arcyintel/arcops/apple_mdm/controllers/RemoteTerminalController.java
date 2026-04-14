package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentActivityLogService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentPushService;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceLookupService;
import com.arcyintel.arcops.apple_mdm.models.session.RemoteTerminalSession;
import com.arcyintel.arcops.apple_mdm.services.terminal.PendingTerminalService;
import com.arcyintel.arcops.apple_mdm.services.terminal.RemoteTerminalSessionService;
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

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_START_TERMINAL;
import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_STOP_TERMINAL;

@RestController
@RequestMapping("/remote-terminal")
@RequiredArgsConstructor
@Tag(name = "Remote Terminal", description = "Interactive remote terminal session management")
@RequiresFeature("REMOTE_ACCESS")
public class RemoteTerminalController {

    private static final Logger logger = LoggerFactory.getLogger(RemoteTerminalController.class);

    private final RemoteTerminalSessionService sessionService;
    private final AgentCommandService agentCommandService;
    private final DeviceLookupService deviceLookupService;
    private final AgentPushService agentPushService;
    private final AgentActivityLogService agentActivityLogService;
    private final PendingTerminalService pendingTerminalService;

    @Operation(summary = "Start remote terminal session",
            description = "Creates a terminal session and sends start_terminal command to the device via MQTT. " +
                    "If the device is offline but has a push token, sends a wake-up push and returns 202 Accepted.")
    @PostMapping("/start/{udid}")
    public ResponseEntity<?> startSession(@PathVariable String udid) {
        AppleDevice device = deviceLookupService.findByUdid(udid)
                .orElseThrow(() -> new EntityNotFoundException("Device", udid));

        if (Boolean.TRUE.equals(device.getAgentOnline())) {
            RemoteTerminalSession session = sessionService.createSession(udid);
            agentCommandService.sendCommand(udid, CMD_START_TERMINAL, Map.of(
                    "sessionId", session.sessionId()
            ));
            agentActivityLogService.logStart(udid, "REMOTE_TERMINAL", session.sessionId(),
                    Map.of(), "admin");
            return ResponseEntity.ok(Map.of("sessionId", session.sessionId()));
        }

        String agentPushToken = device.getAgentPushToken();
        if (agentPushService.isEnabled() && agentPushToken != null && !agentPushToken.isBlank()) {
            RemoteTerminalSession session = sessionService.createSession(udid);
            pendingTerminalService.setPending(udid, session.sessionId());
            agentPushService.sendAlertNotification(agentPushToken, device.getAgentPlatform(),
                    "Remote Terminal Request",
                    "Administrator is requesting a remote terminal session",
                    Map.of("type", "remote_terminal", "sessionId", session.sessionId()));
            agentActivityLogService.logStart(udid, "REMOTE_TERMINAL", session.sessionId(),
                    Map.of(), "admin");

            logger.info("Terminal session queued + alert push sent for offline device {}", udid);
            return ResponseEntity.accepted().body(Map.of(
                    "sessionId", session.sessionId(),
                    "status", "waking",
                    "message", "Device is offline. Alert push sent for terminal request."
            ));
        }

        throw new IllegalStateException("Device agent is not online and cannot be woken up (no push token available)");
    }

    @Operation(summary = "Stop remote terminal session",
            description = "Sends stop command to device and ends the session.")
    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<?> stopSession(@PathVariable String sessionId) {
        RemoteTerminalSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new EntityNotFoundException("RemoteTerminalSession", sessionId);
        }

        agentCommandService.sendCommand(session.deviceUdid(), CMD_STOP_TERMINAL,
                Map.of("sessionId", sessionId));
        sessionService.endSession(sessionId);
        agentActivityLogService.logComplete(sessionId);

        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @Operation(summary = "Get remote terminal session status")
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> getStatus(@PathVariable String sessionId) {
        RemoteTerminalSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new EntityNotFoundException("RemoteTerminalSession", sessionId);
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
