package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentActivityLogService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceLookupService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentPushService;
import com.arcyintel.arcops.apple_mdm.services.screenshare.PendingScreenShareService;
import com.arcyintel.arcops.apple_mdm.models.session.ScreenShareSession;
import com.arcyintel.arcops.apple_mdm.services.screenshare.ScreenShareSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.arcyintel.arcops.commons.license.RequiresFeature;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_START_SCREEN_SHARE;
import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_STOP_SCREEN_SHARE;

@RestController
@RequestMapping("/screen-share")
@RequiredArgsConstructor
@Tag(name = "Screen Share", description = "WebRTC screen sharing session management")
@RequiresFeature("REMOTE_ACCESS")
public class ScreenShareController {

    private static final Logger logger = LoggerFactory.getLogger(ScreenShareController.class);

    private static final List<String> STUN_SERVERS = List.of(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302"
    );

    @org.springframework.beans.factory.annotation.Value("${webrtc.turn-url:}")
    private String turnUrl;
    @org.springframework.beans.factory.annotation.Value("${webrtc.turn-username:}")
    private String turnUsername;
    @org.springframework.beans.factory.annotation.Value("${webrtc.turn-credential:}")
    private String turnCredential;

    private final ScreenShareSessionService sessionService;
    private final AgentCommandService agentCommandService;
    private final DeviceLookupService deviceLookupService;
    private final AgentPushService agentPushService;
    private final PendingScreenShareService pendingScreenShareService;
    private final AgentActivityLogService agentActivityLogService;

    @Operation(summary = "Start screen share session",
            description = "Creates a signaling session and sends start_screen_share command to the device via MQTT. " +
                    "If the device is offline but has a push token, sends a silent wake-up push and returns 202 Accepted.")
    @PostMapping("/start/{udid}")
    public ResponseEntity<?> startSession(
            @PathVariable String udid,
            @RequestParam(defaultValue = "screen") String captureType) {
        AppleDevice device = deviceLookupService.findByUdid(udid)
                .orElseThrow(() -> new EntityNotFoundException("Device", udid));

        // Device is online — proceed immediately
        if (Boolean.TRUE.equals(device.getAgentOnline())) {
            ScreenShareSession session = sessionService.createSession(udid);
            agentCommandService.sendCommand(udid, CMD_START_SCREEN_SHARE, Map.of(
                    "sessionId", session.sessionId(),
                    "stunServers", STUN_SERVERS,
                    "turnServers", turnUrl.isBlank() ? List.of() : List.of(Map.of("url", turnUrl, "username", turnUsername, "credential", turnCredential)),
                    "captureType", captureType
            ));
            agentActivityLogService.logStart(udid, "SCREEN_SHARE", session.sessionId(),
                    Map.of("captureType", captureType), "admin");

            // Build ICE servers for browser (same STUN/TURN the agent gets)
            var iceServers = new java.util.ArrayList<Map<String, Object>>();
            for (String stun : STUN_SERVERS) {
                iceServers.add(Map.of("urls", stun));
            }
            if (!turnUrl.isBlank()) {
                iceServers.add(Map.of("urls", turnUrl, "username", turnUsername, "credential", turnCredential));
            }

            return ResponseEntity.ok(Map.of("sessionId", session.sessionId(), "iceServers", iceServers));
        }

        // Device is offline — send alert push to notify user about screen share request
        String agentPushToken = device.getAgentPushToken();
        if (agentPushService.isEnabled() && agentPushToken != null && !agentPushToken.isBlank()) {
            ScreenShareSession session = sessionService.createSession(udid);
            sessionService.updateState(session.sessionId(), ScreenShareSession.STATE_WAKING);

            pendingScreenShareService.setPending(udid, session.sessionId(), captureType);
            agentPushService.sendAlertNotification(agentPushToken, device.getAgentPlatform(),
                    "Screen Share Request",
                    "Administrator is requesting screen sharing",
                    Map.of("type", "screen_share", "sessionId", session.sessionId()));
            agentActivityLogService.logStart(udid, "SCREEN_SHARE", session.sessionId(),
                    Map.of("captureType", captureType), "admin");

            var wakeIceServers = new java.util.ArrayList<Map<String, Object>>();
            for (String stun : STUN_SERVERS) {
                wakeIceServers.add(Map.of("urls", stun));
            }
            if (!turnUrl.isBlank()) {
                wakeIceServers.add(Map.of("urls", turnUrl, "username", turnUsername, "credential", turnCredential));
            }

            logger.info("Screen share queued + alert push sent for offline device {}", udid);
            return ResponseEntity.accepted().body(Map.of(
                    "sessionId", session.sessionId(),
                    "status", "waking",
                    "iceServers", wakeIceServers,
                    "message", "Device is offline. Alert push sent for screen share request."
            ));
        }

        // Cannot wake the device
        throw new IllegalStateException("Device agent is not online and cannot be woken up (no push token available)");
    }

    @Operation(summary = "Stop screen share session",
            description = "Sends stop command to device and ends the session.")
    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<?> stopSession(@PathVariable String sessionId) {
        ScreenShareSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new EntityNotFoundException("ScreenShareSession", sessionId);
        }

        agentCommandService.sendCommand(session.deviceUdid(), CMD_STOP_SCREEN_SHARE,
                Map.of("sessionId", sessionId));
        sessionService.endSession(sessionId);
        agentActivityLogService.logComplete(sessionId);

        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @Operation(summary = "Get screen share session status")
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> getStatus(@PathVariable String sessionId) {
        ScreenShareSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new EntityNotFoundException("ScreenShareSession", sessionId);
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
