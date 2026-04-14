package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.AppCatalogAssignment;
import com.arcyintel.arcops.apple_mdm.services.app.AppCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/app-catalogs")
@RequiredArgsConstructor
@Tag(name = "App Catalog", description = "App catalog assignment management")
public class AppCatalogController {

    private final AppCatalogService appCatalogService;

    @Operation(summary = "Assign AppGroup to Account or AccountGroup")
    @PostMapping("/assign")
    public ResponseEntity<AppCatalogAssignment> assign(@RequestBody Map<String, String> body) {
        UUID appGroupId = UUID.fromString(body.get("appGroupId"));
        String targetType = body.get("targetType");  // ACCOUNT | ACCOUNT_GROUP
        UUID targetId = UUID.fromString(body.get("targetId"));

        AppCatalogAssignment assignment = appCatalogService.assign(appGroupId, targetType, targetId);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @Operation(summary = "Remove catalog assignment")
    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> removeAssignment(@PathVariable UUID id) {
        appCatalogService.removeAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get assignments for an AppGroup")
    @GetMapping("/app-group/{id}/assignments")
    public ResponseEntity<List<AppCatalogAssignment>> getAssignmentsForAppGroup(@PathVariable UUID id) {
        return ResponseEntity.ok(appCatalogService.getAssignmentsForAppGroup(id));
    }

    @Operation(summary = "Get assignments by target (account or account group)")
    @GetMapping("/assignments")
    public ResponseEntity<List<AppCatalogAssignment>> getAssignmentsByTarget(
            @RequestParam String targetType,
            @RequestParam UUID targetId) {
        return ResponseEntity.ok(appCatalogService.getAssignmentsByTarget(targetType, targetId));
    }

    @Operation(summary = "Get catalogs for an account")
    @GetMapping("/account/{id}")
    public ResponseEntity<List<Map<String, Object>>> getCatalogsForAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(appCatalogService.getCatalogsForAccount(id, List.of()));
    }
}
