package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.appresolve.AppResolveRequest;
import com.arcyintel.arcops.apple_mdm.models.api.appresolve.AppResolveResponseItem;
import com.arcyintel.arcops.apple_mdm.models.api.itunesapp.GetItunesAppDto;
import com.arcyintel.arcops.apple_mdm.services.app.AppResolveService;
import com.arcyintel.arcops.apple_mdm.services.app.ItunesAppService;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/apps")
@RequiredArgsConstructor
@Tag(name = "iTunes Apps", description = "Query iTunes/VPP application metadata synced from Apple Business Manager.")
public class ItunesAppController {

    private final ItunesAppService itunesAppService;
    private final AppResolveService appResolveService;

    @Operation(summary = "Get App by ID", description = "Retrieves an iTunes app by its internal UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "App retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GetItunesAppDto.class))),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<GetItunesAppDto> getApp(
            @PathVariable @Parameter(description = "App UUID") String id) {
        return ResponseEntity.ok(itunesAppService.getApp(id));
    }

    @Operation(summary = "Get App by Track ID", description = "Retrieves an iTunes app by its Apple Track ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "App retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GetItunesAppDto.class))),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @GetMapping("/track/{trackId}")
    public ResponseEntity<GetItunesAppDto> getAppByTrackId(
            @PathVariable @Parameter(description = "Apple Track ID") Long trackId) {
        return ResponseEntity.ok(itunesAppService.getAppByTrackId(trackId));
    }

    @Operation(summary = "Get App by Bundle ID", description = "Retrieves an iTunes app by its Bundle ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "App retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GetItunesAppDto.class))),
            @ApiResponse(responseCode = "404", description = "App not found", content = @Content)
    })
    @GetMapping("/bundle/{bundleId}")
    public ResponseEntity<GetItunesAppDto> getAppByBundleId(
            @PathVariable @Parameter(description = "Bundle ID (e.g., com.apple.Pages)") String bundleId) {
        return ResponseEntity.ok(itunesAppService.getAppByBundleId(bundleId));
    }

    @Operation(
            summary = "List Apps",
            description = """
                    Retrieves iTunes apps with dynamic filtering and field selection.

                    **Special filter: `platform`**
                    Use the `platform` filter to find apps that support a specific platform.
                    Supported values: iPhone, iPad, MacOS, AppleTV, AppleWatch, etc.

                    Example: `{"filters": {"platform": "MacOS"}}` returns all macOS compatible apps.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Apps retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
    })
    @PostMapping("/list")
    public ResponseEntity<PagedModel<Map<String, Object>>> listApps(
            @RequestBody DynamicListRequestDto request) {
        return ResponseEntity.ok(itunesAppService.listApps(request));
    }

    @Operation(summary = "Get Distinct Genres", description = "Returns distinct primary genre names for filtering.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Genres retrieved successfully")
    })
    @GetMapping("/genres")
    public ResponseEntity<List<String>> getGenres() {
        return ResponseEntity.ok(itunesAppService.getDistinctGenres());
    }

    @Operation(summary = "Get Genre Counts", description = "Returns genre names with app counts, ordered by count descending.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Genre counts retrieved successfully")
    })
    @GetMapping("/genre-counts")
    public ResponseEntity<Map<String, Long>> getGenreCounts() {
        return ResponseEntity.ok(itunesAppService.getGenreCounts());
    }

    @Operation(summary = "Batch Resolve App Metadata",
            description = "Resolves app names and icon URLs for a batch of bundle IDs. " +
                    "Accepts VPP (iTunes) and Enterprise apps in a single request.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Apps resolved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content)
    })
    @PostMapping("/resolve")
    public ResponseEntity<List<AppResolveResponseItem>> resolveApps(
            @RequestBody AppResolveRequest request) {
        return ResponseEntity.ok(appResolveService.resolveApps(request.getApps()));
    }
}
