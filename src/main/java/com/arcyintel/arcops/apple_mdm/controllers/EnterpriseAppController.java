package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.AppAnalyzeResponse;
import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.ConfirmUploadRequest;
import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.GetEnterpriseAppDto;
import com.arcyintel.arcops.apple_mdm.services.app.EnterpriseAppService;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.arcyintel.arcops.commons.license.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/enterprise-apps")
@RequiredArgsConstructor
@Tag(name = "Enterprise Apps", description = "Upload and manage enterprise applications (.ipa / .pkg).")
@RequiresFeature("ENTERPRISE_APPS")
public class EnterpriseAppController {

    private final EnterpriseAppService enterpriseAppService;

    // ── Legacy single-step upload (backward compatibility) ────────────────

    @Operation(summary = "Upload Enterprise App (single-step)",
            description = "Uploads an .ipa or .pkg file, parses metadata automatically. Kept for backward compatibility.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "App uploaded and parsed successfully",
                    content = @Content(schema = @Schema(implementation = GetEnterpriseAppDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file type (only .ipa and .pkg supported)", content = @Content),
            @ApiResponse(responseCode = "409", description = "App with same bundleId and version already exists", content = @Content)
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GetEnterpriseAppDto> upload(
            @Parameter(description = "Enterprise app file (.ipa or .pkg)", required = true)
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(enterpriseAppService.upload(file));
    }

    // ── Two-phase upload (primary flow for UI) ──────────────────────────────

    @Operation(summary = "Upload and analyze app (Phase 1)",
            description = "Uploads an .ipa or .pkg file to temp storage and parses its metadata. Returns tempId and parsed metadata for user review.")
    @PostMapping(value = "/upload-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppAnalyzeResponse> uploadAndAnalyze(
            @Parameter(description = "Enterprise app file (.ipa or .pkg)", required = true)
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(enterpriseAppService.uploadAndAnalyze(file));
    }

    @Operation(summary = "Confirm app upload (Phase 2)",
            description = "Confirms the upload: moves file from temp to permanent storage, saves entity to DB with user-provided metadata.")
    @PostMapping("/confirm/{tempId}")
    public ResponseEntity<GetEnterpriseAppDto> confirmUpload(
            @PathVariable @Parameter(description = "Temp ID from Phase 1") String tempId,
            @RequestBody ConfirmUploadRequest request) {
        return ResponseEntity.ok(enterpriseAppService.confirmUpload(tempId, request));
    }

    @Operation(summary = "Cancel app upload",
            description = "Cancels a pending upload and deletes the temp file.")
    @DeleteMapping("/cancel/{tempId}")
    public ResponseEntity<Void> cancelUpload(
            @PathVariable @Parameter(description = "Temp ID from Phase 1") String tempId) {
        enterpriseAppService.cancelUpload(tempId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get Enterprise App by ID", description = "Retrieves an enterprise app by its internal UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "App retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GetEnterpriseAppDto.class))),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<GetEnterpriseAppDto> getById(
            @PathVariable @Parameter(description = "App UUID") String id) {
        return ResponseEntity.ok(enterpriseAppService.getById(id));
    }

    @Operation(summary = "Get Enterprise App by Bundle ID", description = "Retrieves an enterprise app by its Bundle ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "App retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GetEnterpriseAppDto.class))),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @GetMapping("/bundle/{bundleId}")
    public ResponseEntity<GetEnterpriseAppDto> getByBundleId(
            @PathVariable @Parameter(description = "Bundle ID (e.g., com.example.app)") String bundleId) {
        return ResponseEntity.ok(enterpriseAppService.getByBundleId(bundleId));
    }

    @Operation(
            summary = "List Enterprise Apps",
            description = """
                    Retrieves enterprise apps with dynamic filtering and field selection.

                    **Special filter: `platform`**
                    Use the `platform` filter to find apps that support a specific platform.
                    Values: ios, macos, iPhone, iPad, Mac, etc.

                    **Global search: `q`**
                    Searches across displayName, bundleId, and platform fields.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Apps retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
    })
    @PostMapping("/list")
    public ResponseEntity<PagedModel<Map<String, Object>>> list(
            @RequestBody DynamicListRequestDto request) {
        return ResponseEntity.ok(enterpriseAppService.list(request));
    }

    @Operation(summary = "Download Enterprise App", description = "Downloads the enterprise app binary file (.ipa or .pkg).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File download started"),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable @Parameter(description = "App UUID") UUID id) {
        Resource resource = enterpriseAppService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "Get Enterprise App Manifest",
            description = "Returns an Apple manifest plist for enterprise app installation via MDM. " +
                    "Used by InstallApplication command with ManifestURL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Manifest plist returned",
                    content = @Content(mediaType = "application/xml")),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @GetMapping(value = "/{id}/manifest", produces = "application/xml")
    public ResponseEntity<String> getManifest(
            @PathVariable @Parameter(description = "App UUID") UUID id) {
        String manifest = enterpriseAppService.getManifest(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(manifest);
    }

    @Operation(summary = "Delete Enterprise App", description = "Deletes the enterprise app and its binary file from storage.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "App deleted successfully"),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable @Parameter(description = "App UUID") UUID id) {
        enterpriseAppService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
