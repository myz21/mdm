package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.AppGroup;
import com.arcyintel.arcops.apple_mdm.domains.AppGroupItem;
import com.arcyintel.arcops.apple_mdm.services.app.AppGroupService;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import org.springframework.data.web.PagedModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/app-groups")
@RequiredArgsConstructor
@Tag(name = "Apple App Groups", description = "Create and manage application catalogs for VPP and enterprise app groups.")
public class AppGroupController {

    private final AppGroupService appGroupService;

    @Operation(
            summary = "Create App Group",
            description = "Creates a new application group with optional items.",
            requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = AppGroup.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Created",
                            content = @Content(schema = @Schema(implementation = AppGroup.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<AppGroup> create(@org.springframework.web.bind.annotation.RequestBody AppGroup request) {
        return ResponseEntity.ok(appGroupService.create(request));
    }

    @Operation(
            summary = "Get App Group",
            description = "Returns a single app group by id.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Found",
                            content = @Content(schema = @Schema(implementation = AppGroup.class))),
                    @ApiResponse(responseCode = "404", description = "Not found", content = @Content)
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<AppGroup> getById(
            @Parameter(description = "AppGroup id") @PathVariable UUID id) {
        return appGroupService.getById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new EntityNotFoundException("AppGroup", id.toString()));
    }

    @Operation(
            summary = "List App Groups (Simple)",
            description = "Returns paginated app groups.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = Page.class)))
            }
    )
    @GetMapping
    public ResponseEntity<Page<AppGroup>> list(
            @Parameter(description = "Page index (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(appGroupService.list(page, size));
    }

    @Operation(
            summary = "List App Groups",
            description = "Returns app groups with dynamic filtering and field selection.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
            }
    )
    @PostMapping("/list")
    public ResponseEntity<PagedModel<Map<String, Object>>> listAppGroups(
            @org.springframework.web.bind.annotation.RequestBody DynamicListRequestDto request) {
        return ResponseEntity.ok(appGroupService.listAppGroups(request));
    }

    @Operation(
            summary = "Update App Group",
            description = "Updates name/description/metadata/items of an app group.",
            requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = AppGroup.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Updated",
                            content = @Content(schema = @Schema(implementation = AppGroup.class))),
                    @ApiResponse(responseCode = "404", description = "Not found", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<AppGroup> update(
            @Parameter(description = "AppGroup id") @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestBody AppGroup request) {
        return ResponseEntity.ok(appGroupService.update(id, request));
    }

    @Operation(
            summary = "Delete App Group",
            description = "Deletes an app group by id.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deleted"),
                    @ApiResponse(responseCode = "404", description = "Not found", content = @Content)
            }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "AppGroup id") @PathVariable UUID id) {
        appGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Add Item to App Group",
            description = "Appends a single item to the group.",
            requestBody = @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AppGroupItem.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item added",
                            content = @Content(schema = @Schema(implementation = AppGroup.class))),
                    @ApiResponse(responseCode = "404", description = "Group not found", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
    @PostMapping("/{id}/items")
    public ResponseEntity<AppGroup> addItem(
            @Parameter(description = "AppGroup id") @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestBody AppGroupItem item) {
        return ResponseEntity.ok(appGroupService.addItem(id, item));
    }

    @Operation(
            summary = "Replace Items",
            description = "Replaces the group's items with the provided list.",
            requestBody = @RequestBody(required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AppGroupItem.class)))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Items replaced",
                            content = @Content(schema = @Schema(implementation = AppGroup.class))),
                    @ApiResponse(responseCode = "404", description = "Group not found", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
    @PutMapping("/{id}/items")
    public ResponseEntity<AppGroup> replaceItems(
            @Parameter(description = "AppGroup id") @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestBody List<AppGroupItem> items) {
        return ResponseEntity.ok(appGroupService.replaceItems(id, items));
    }

    @Operation(
            summary = "Remove Item by Index",
            description = "Removes an item from the group by its index (0-based).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item removed",
                            content = @Content(schema = @Schema(implementation = AppGroup.class))),
                    @ApiResponse(responseCode = "404", description = "Group not found", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Invalid index", content = @Content)
            }
    )
    @DeleteMapping("/{id}/items/{index}")
    public ResponseEntity<AppGroup> removeItemByIndex(
            @Parameter(description = "AppGroup id") @PathVariable UUID id,
            @Parameter(description = "Item index (0-based)") @PathVariable int index) {
        return ResponseEntity.ok(appGroupService.removeItemByIndex(id, index));
    }
}
