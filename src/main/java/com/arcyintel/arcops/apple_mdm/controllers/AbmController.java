package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.api.device.AbmDeviceSummaryDto;
import com.arcyintel.arcops.apple_mdm.models.cert.abm.*;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset.AssetAssignRequest;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset.Assignment;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.user.VppUser;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleDepService;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleVppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/abm")
@RequiredArgsConstructor
@Tag(name = "Apple Business Manager", description = "DEP device management, enrollment profile operations and VPP asset management.")
public class AbmController {

    private final AppleDepService appleDepService;
    private final AppleVppService appleVppService;

    // --- DEP (Device Enrollment Program) Operations ---

    @Operation(summary = "Fetch Devices", description = "Fetches the list of devices assigned to this MDM server from Apple Business Manager and syncs to database.")
    @GetMapping("/devices")
    public ResponseEntity<List<Device>> fetchDevices() throws Exception {
        List<Device> devices = appleDepService.fetchDevices();
        return ResponseEntity.ok(devices);
    }

    @Operation(summary = "Device Summary", description = "Returns device counts by profile status and the 10 most recently added devices from local database.")
    @GetMapping("/devices/summary")
    public ResponseEntity<AbmDeviceSummaryDto> getDeviceSummary() {
        return ResponseEntity.ok(appleDepService.getDeviceSummary());
    }

    @Operation(summary = "Disown Devices", description = "Notify Apple that these devices are no longer owned by this organization.")
    @PostMapping("/devices/disown")
    public ResponseEntity<DeviceStatusResponse> disownDevices(@RequestBody List<String> serialNumbers) throws Exception {
        DeviceStatusResponse resp = appleDepService.disownDevices(serialNumbers);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Create Enrollment Profile", description = "Creates an MDM enrollment profile on Apple Business Manager and saves to database.")
    @PostMapping("/profiles")
    public ResponseEntity<ProfileResponse> createProfile(@RequestBody Profile profileRequest) throws Exception {
        ProfileResponse response = appleDepService.createAndSaveProfile(profileRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get Profile", description = "Retrieves details of an existing MDM profile by its UUID.")
    @GetMapping("/profiles/{profileUuid}")
    public ResponseEntity<Profile> getProfile(@PathVariable String profileUuid) throws Exception {
        Profile profile = appleDepService.getProfile(profileUuid);
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "List All Profiles", description = "Returns all MDM enrollment profiles stored in the system.")
    @GetMapping("/profiles")
    public ResponseEntity<?> listProfiles() {
        return ResponseEntity.ok(appleDepService.listProfiles());
    }

    @Operation(summary = "Delete Profile", description = "Deletes an MDM profile from the local database. Only allowed if no devices are assigned to the profile. Apple does not support profile deletion from their servers.")
    @DeleteMapping("/profiles/{profileUuid}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String profileUuid) throws Exception {
        appleDepService.deleteProfile(profileUuid);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove Profile from Devices", description = "Removes the MDM profile from the specified devices. Optionally reassigns a new profile.")
    @DeleteMapping("/profiles/{profileUuid}/devices")
    public ResponseEntity<ClearProfileResponse> removeProfileFromDevices(
            @PathVariable String profileUuid,
            @RequestBody ClearProfileRequest req) throws Exception {
        req.setProfileUuid(profileUuid);
        ClearProfileResponse resp = appleDepService.removeProfileFromDevices(req);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Assign Profile to Devices", description = "Assigns the MDM enrollment profile to all unassigned devices.")
    @PostMapping("/profiles/{profileUuid}/assign")
    public ResponseEntity<ProfileResponse> assignProfileToDevices(@PathVariable String profileUuid) throws Exception {
        ProfileResponse response = appleDepService.assignProfileToDevices(profileUuid);
        return ResponseEntity.ok(response);
    }

    // --- VPP (Volume Purchase Program) Operations ---

    @Operation(summary = "Get VPP Assets", description = "Fetches all VPP assets (apps and books) from Apple VPP.")
    @GetMapping("/vpp/assets")
    public ResponseEntity<List<ItunesAppMeta>> getVppAssets() throws Exception {
        List<ItunesAppMeta> assets = appleVppService.fetchVppAssets();
        return ResponseEntity.ok(assets);
    }

    @Operation(summary = "Assign VPP Assets to Devices", description = "Assigns VPP assets (apps/books) to specified devices.")
    @PostMapping("/vpp/assets/assign")
    public ResponseEntity<String> assignAssetsToDevices(@RequestBody List<AssetAssignRequest> requests) throws Exception {
        appleVppService.assignAssetsToDevices(requests);
        return ResponseEntity.ok("Assets assigned successfully.");
    }

    @Operation(summary = "Disassociate VPP Assets from Devices", description = "Removes VPP assets from specified devices.")
    @PostMapping("/vpp/assets/disassociate")
    public ResponseEntity<String> disassociateAssetsFromDevices(@RequestBody List<AssetAssignRequest> requests) throws Exception {
        appleVppService.disassociateAssetsFromDevices(requests);
        return ResponseEntity.ok("Assets disassociated successfully.");
    }

    @Operation(summary = "Get VPP Assignments", description = "Fetches all assigned assets to devices from Apple VPP.")
    @GetMapping("/vpp/assets/assignments")
    public ResponseEntity<List<Assignment>> getVppAssignments() throws Exception {
        List<Assignment> assignments = appleVppService.fetchAllAssignments();
        return ResponseEntity.ok(assignments);
    }

    @Operation(summary = "Revoke all assets from a device", description = "Revokes all VPP assets assigned to a device by its serial number.")
    @PostMapping("/vpp/assets/revoke")
    public ResponseEntity<String> revokeAssetsFromDevice(@RequestParam String serialNumber) throws Exception {
        appleVppService.revokeAssetsFromDevice(serialNumber);
        return ResponseEntity.ok("Assets revoked successfully for device: " + serialNumber);
    }

    @Operation(summary = "Get All VPP Users", description = "Fetches all VPP users from Apple VPP.")
    @GetMapping("/vpp/users")
    public ResponseEntity<List<VppUser>> getAllVppUsers() throws Exception {
        List<VppUser> users = appleVppService.fetchAllVppUsers();
        return ResponseEntity.ok(users);
    }

}
