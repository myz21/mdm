package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.device.GetAppleDeviceDetailDto;
import com.arcyintel.arcops.apple_mdm.services.device.AppleDeviceDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for Apple device detail operations.
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Tag(name = "Apple Devices", description = "Endpoints for retrieving Apple device details.")
public class AppleDeviceController {

    private final AppleDeviceDetailService appleDeviceDetailService;

    /**
     * Get detailed information for a specific device by ID.
     *
     * @param id the device UUID
     * @return detailed device information including properties, command history, and installed apps
     */
    @Operation(
            summary = "Get Device Detail",
            description = "Retrieves comprehensive device information including properties, command history, installed apps, and applied policy."
    )
    @GetMapping("/{id}/detail")
    public ResponseEntity<GetAppleDeviceDetailDto> getDeviceDetail(@PathVariable UUID id) {
        GetAppleDeviceDetailDto detail = appleDeviceDetailService.getDeviceDetail(id);
        return ResponseEntity.ok(detail);
    }

    /**
     * Get detailed information for a specific device by UDID.
     *
     * @param udid the device UDID
     * @return detailed device information including properties, command history, and installed apps
     */
    @Operation(
            summary = "Get Device Detail by UDID",
            description = "Retrieves comprehensive device information by UDID including properties, command history, installed apps, and applied policy."
    )
    @GetMapping("/udid/{udid}/detail")
    public ResponseEntity<GetAppleDeviceDetailDto> getDeviceDetailByUdid(@PathVariable String udid) {
        GetAppleDeviceDetailDto detail = appleDeviceDetailService.getDeviceDetailByUdid(udid);
        return ResponseEntity.ok(detail);
    }
}
