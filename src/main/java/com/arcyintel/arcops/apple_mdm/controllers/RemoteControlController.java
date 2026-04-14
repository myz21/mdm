package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.arcyintel.arcops.commons.license.RequiresFeature;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.*;

@RestController
@RequestMapping("/remote-control")
@RequiredArgsConstructor
@Tag(name = "Remote Control", description = "Remote mouse and keyboard control for screen share sessions")
@RequiresFeature("REMOTE_ACCESS")
public class RemoteControlController {

    private static final Logger logger = LoggerFactory.getLogger(RemoteControlController.class);

    private final AgentCommandService agentCommandService;

    @Operation(summary = "Start remote control", description = "Enables remote input on the device")
    @PostMapping("/start/{udid}")
    public ResponseEntity<?> startRemoteControl(@PathVariable String udid) {
        agentCommandService.sendCommand(udid, CMD_REMOTE_CONTROL_START, Map.of());
        logger.info("Remote control started for device {}", udid);
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @Operation(summary = "Stop remote control", description = "Disables remote input on the device")
    @PostMapping("/stop/{udid}")
    public ResponseEntity<?> stopRemoteControl(@PathVariable String udid) {
        agentCommandService.sendCommand(udid, CMD_REMOTE_CONTROL_STOP, Map.of());
        logger.info("Remote control stopped for device {}", udid);
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @Operation(summary = "Send mouse event", description = "Sends a mouse event to the device")
    @PostMapping("/mouse/{udid}")
    public ResponseEntity<?> sendMouseEvent(
            @PathVariable String udid,
            @RequestBody Map<String, Object> body) {
        agentCommandService.sendCommand(udid, CMD_REMOTE_MOUSE, body);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Send keyboard event", description = "Sends a keyboard event to the device")
    @PostMapping("/keyboard/{udid}")
    public ResponseEntity<?> sendKeyboardEvent(
            @PathVariable String udid,
            @RequestBody Map<String, Object> body) {
        agentCommandService.sendCommand(udid, CMD_REMOTE_KEYBOARD, body);
        return ResponseEntity.ok().build();
    }
}
