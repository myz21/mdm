package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.DeviceAuthHistory;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceAuthHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Tag(name = "Device Auth History", description = "Device authentication history")
public class DeviceAuthHistoryController {

    private final DeviceAuthHistoryService deviceAuthHistoryService;

    @Operation(summary = "Get device auth history by UDID")
    @GetMapping("/{udid}/auth-history")
    public ResponseEntity<Page<DeviceAuthHistory>> getAuthHistory(
            @PathVariable String udid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<DeviceAuthHistory> history = deviceAuthHistoryService.getAuthHistoryByUdid(udid, page, size);
        return ResponseEntity.ok(history);
    }
}
