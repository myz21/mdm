package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.systemsetting.SystemSettingDto;
import com.arcyintel.arcops.apple_mdm.services.settings.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system-settings")
@RequiredArgsConstructor
@Tag(name = "System Settings", description = "CRUD operations for system-level configuration settings.")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    @Operation(summary = "List all settings", description = "Retrieves all system settings.")
    @GetMapping
    public ResponseEntity<List<SystemSettingDto>> listAll() {
        return ResponseEntity.ok(systemSettingService.listAll());
    }

    @Operation(summary = "Get setting by identifier", description = "Retrieves a single system setting by its operation identifier.")
    @GetMapping("/{operationIdentifier}")
    public ResponseEntity<SystemSettingDto> getByIdentifier(@PathVariable String operationIdentifier) {
        return ResponseEntity.ok(systemSettingService.getByIdentifier(operationIdentifier));
    }

    @Operation(summary = "Upsert setting", description = "Creates or updates a system setting identified by operationIdentifier.")
    @PutMapping("/{operationIdentifier}")
    public ResponseEntity<Void> upsert(
            @PathVariable String operationIdentifier,
            @RequestBody Map<String, Object> value) {
        systemSettingService.upsert(operationIdentifier, value);
        return ResponseEntity.ok().build();
    }
}
