package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.DisenrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller responsible for sending MDM commands to Apple devices.
 * Returns Map responses compatible with ApiResponse wrapper.
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Tag(name = "Apple Device Commands", description = "Endpoints for sending commands to enrolled Apple devices.")
public class DeviceCommandController {

    private final AppleCommandSenderService appleCommandSenderService;
    private final DisenrollService disenrollService;

    @Operation(summary = "Send RemoveProfile Command")
    @PostMapping("/{udid}/commands/remove-profile")
    public ResponseEntity<Map<String, Object>> removeProfile(
            @PathVariable String udid,
            @RequestParam String identifier) throws Exception {
        appleCommandSenderService.removeProfile(udid, identifier);
        return ResponseEntity.ok(commandResponse("RemoveProfile", udid));
    }

    @Operation(summary = "Send InstallApp Command")
    @PostMapping("/{udid}/commands/install-app")
    public ResponseEntity<Map<String, Object>> installApp(@PathVariable String udid,
                             @RequestParam(required = false) Integer trackId,
                             @RequestParam(required = false) String identifier) throws Exception {
        if (trackId != null) {
            appleCommandSenderService.installApp(udid, trackId, false, false, null);
        } else if (identifier != null && !identifier.isBlank()) {
            appleCommandSenderService.installApp(udid, identifier, false, false, null);
        } else {
            throw new IllegalArgumentException("Either trackId or identifier must be provided");
        }
        return ResponseEntity.ok(commandResponse("InstallApp", udid));
    }

    @Operation(summary = "Send RemoveApplication Command")
    @PostMapping("/{udid}/commands/remove-app")
    public ResponseEntity<Map<String, Object>> removeApp(
            @PathVariable String udid,
            @RequestParam String identifier) throws Exception {
        appleCommandSenderService.removeApp(udid, identifier);
        return ResponseEntity.ok(commandResponse("RemoveApplication", udid));
    }

    @Operation(summary = "Send DeviceInformation Command")
    @PostMapping("/{udid}/commands/device-information")
    public ResponseEntity<Map<String, Object>> queryDeviceInformation(
            @PathVariable String udid,
            @RequestParam(defaultValue = "false") boolean isSystem) throws Exception {
        appleCommandSenderService.queryDeviceInformation(udid, isSystem);
        return ResponseEntity.ok(commandResponse("DeviceInformation", udid));
    }

    @Operation(summary = "Send RestartDevice Command")
    @PostMapping("/{udid}/commands/restart-device")
    public ResponseEntity<Map<String, Object>> restartDevice(
            @PathVariable String udid,
            @RequestParam(required = false) Boolean notifyUser) throws Exception {
        appleCommandSenderService.restartDevice(udid, notifyUser);
        return ResponseEntity.ok(commandResponse("RestartDevice", udid));
    }

    @Operation(summary = "Send DeviceLock Command")
    @PostMapping("/{udid}/commands/lock")
    public ResponseEntity<Map<String, Object>> lockDevice(@PathVariable String udid,
                             @RequestParam(required = false) String message,
                             @RequestParam(required = false) String phoneNumber) throws Exception {
        appleCommandSenderService.lockDevice(udid, message, phoneNumber);
        return ResponseEntity.ok(commandResponse("DeviceLock", udid));
    }

    @Operation(summary = "Send ShutDownDevice Command")
    @PostMapping("/{udid}/commands/shutdown")
    public ResponseEntity<Map<String, Object>> shutDownDevice(@PathVariable String udid) throws Exception {
        appleCommandSenderService.shutDownDevice(udid);
        return ResponseEntity.ok(commandResponse("ShutDownDevice", udid));
    }

    @Operation(summary = "Send EraseDevice Command")
    @PostMapping("/{udid}/commands/erase")
    public ResponseEntity<Map<String, Object>> eraseDevice(@PathVariable String udid,
                              @RequestParam(required = false) String pin,
                              @RequestParam(defaultValue = "true") boolean preserveDataPlan) throws Exception {
        appleCommandSenderService.eraseDevice(udid, pin, preserveDataPlan);
        return ResponseEntity.ok(commandResponse("EraseDevice", udid));
    }

    @Operation(summary = "Sync App Inventory")
    @PostMapping("/{udid}/commands/sync-apps")
    public ResponseEntity<Map<String, Object>> syncAppInventory(@PathVariable String udid) throws Exception {
        appleCommandSenderService.syncAppInventory(udid);
        return ResponseEntity.ok(commandResponse("SyncApps", udid));
    }

    @Operation(summary = "Send DeviceConfigured Command")
    @PostMapping("/{udid}/commands/device-configured")
    public ResponseEntity<Map<String, Object>> deviceConfigured(@PathVariable String udid) throws Exception {
        appleCommandSenderService.deviceConfigured(udid);
        return ResponseEntity.ok(commandResponse("DeviceConfigured", udid));
    }

    @Operation(summary = "Security Info")
    @PostMapping("/{udid}/commands/security-info")
    public ResponseEntity<Map<String, Object>> securityInfo(@PathVariable String udid) throws Exception {
        appleCommandSenderService.securityInfo(udid);
        return ResponseEntity.ok(commandResponse("SecurityInfo", udid));
    }

    @Operation(summary = "Send ClearPasscode Command")
    @PostMapping("/{udid}/commands/clear-passcode")
    public ResponseEntity<Map<String, Object>> clearPasscode(@PathVariable String udid) throws Exception {
        appleCommandSenderService.clearPasscode(udid);
        return ResponseEntity.ok(commandResponse("ClearPasscode", udid));
    }

    @Operation(summary = "Send ClearRestrictionsPassword Command")
    @PostMapping("/{udid}/commands/clear-restrictions-password")
    public ResponseEntity<Map<String, Object>> clearRestrictionsPassword(@PathVariable String udid) throws Exception {
        appleCommandSenderService.clearRestrictionsPassword(udid);
        return ResponseEntity.ok(commandResponse("ClearRestrictionsPassword", udid));
    }

    @Operation(summary = "Enable Lost Mode")
    @PostMapping("/{udid}/commands/enable-lost-mode")
    public ResponseEntity<Map<String, Object>> enableLostMode(@PathVariable String udid,
                                 @RequestParam String message,
                                 @RequestParam(required = false) String phoneNumber,
                                 @RequestParam(required = false) String footnote) throws Exception {
        appleCommandSenderService.enableLostMode(udid, message, phoneNumber, footnote);
        return ResponseEntity.ok(commandResponse("EnableLostMode", udid));
    }

    @Operation(summary = "Request Device Location")
    @PostMapping("/{udid}/commands/location")
    public ResponseEntity<Map<String, Object>> requestDeviceLocation(@PathVariable String udid) throws Exception {
        appleCommandSenderService.requestDeviceLocation(udid);
        return ResponseEntity.ok(commandResponse("DeviceLocation", udid));
    }

    @Operation(summary = "Request User List")
    @PostMapping("/{udid}/commands/user-list")
    public ResponseEntity<Map<String, Object>> requestUserList(@PathVariable String udid) throws Exception {
        appleCommandSenderService.requestUserList(udid);
        return ResponseEntity.ok(commandResponse("UserList", udid));
    }

    @Operation(summary = "Log Out User")
    @PostMapping("/{udid}/commands/logout-user")
    public ResponseEntity<Map<String, Object>> logOutUser(@PathVariable String udid) throws Exception {
        appleCommandSenderService.logOutUser(udid);
        return ResponseEntity.ok(commandResponse("LogOutUser", udid));
    }

    @Operation(summary = "Delete User")
    @PostMapping("/{udid}/commands/delete-user")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable String udid,
            @RequestParam String userName,
            @RequestParam(defaultValue = "false") boolean forceDeletion) throws Exception {
        appleCommandSenderService.deleteUser(udid, userName, forceDeletion);
        return ResponseEntity.ok(commandResponse("DeleteUser", udid));
    }

    @Operation(summary = "Play Lost Mode Sound")
    @PostMapping("/{udid}/commands/play-lost-mode-sound")
    public ResponseEntity<Map<String, Object>> playLostModeSound(@PathVariable String udid) throws Exception {
        appleCommandSenderService.playLostModeSound(udid);
        return ResponseEntity.ok(commandResponse("PlayLostModeSound", udid));
    }

    @Operation(summary = "Disable Lost Mode")
    @PostMapping("/{udid}/commands/disable-lost-mode")
    public ResponseEntity<Map<String, Object>> disableLostMode(@PathVariable String udid) throws Exception {
        appleCommandSenderService.disableLostMode(udid);
        return ResponseEntity.ok(commandResponse("DisableLostMode", udid));
    }

    @Operation(summary = "Rename Device")
    @PostMapping("/{udid}/commands/rename")
    public ResponseEntity<Map<String, Object>> renameDevice(@PathVariable String udid, @RequestParam String newName) throws Exception {
        appleCommandSenderService.renameDevice(udid, newName);
        return ResponseEntity.ok(commandResponse("Rename", udid));
    }

    @Operation(summary = "Toggle Bluetooth")
    @PostMapping("/{udid}/commands/bluetooth")
    public ResponseEntity<Map<String, Object>> setBluetooth(@PathVariable String udid, @RequestParam boolean enabled) throws Exception {
        appleCommandSenderService.setBluetooth(udid, enabled);
        return ResponseEntity.ok(commandResponse("Bluetooth", udid));
    }

    @Operation(summary = "Toggle Data Roaming")
    @PostMapping("/{udid}/commands/data-roaming")
    public ResponseEntity<Map<String, Object>> setDataRoaming(@PathVariable String udid, @RequestParam boolean enabled) throws Exception {
        appleCommandSenderService.setDataRoaming(udid, enabled);
        return ResponseEntity.ok(commandResponse("DataRoaming", udid));
    }

    @Operation(summary = "Toggle Personal Hotspot")
    @PostMapping("/{udid}/commands/personal-hotspot")
    public ResponseEntity<Map<String, Object>> setPersonalHotspot(@PathVariable String udid, @RequestParam boolean enabled) throws Exception {
        appleCommandSenderService.setPersonalHotspot(udid, enabled);
        return ResponseEntity.ok(commandResponse("PersonalHotspot", udid));
    }

    @Operation(summary = "Set Device Wallpaper")
    @PostMapping(value = "/{udid}/commands/wallpaper", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> setWallpaper(
            @PathVariable String udid,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "3") Integer where) throws Exception {
        appleCommandSenderService.setWallpaper(udid, file.getBytes(), where);
        return ResponseEntity.ok(commandResponse("Wallpaper", udid));
    }

    @Operation(summary = "Set Device TimeZone")
    @PostMapping("/{udid}/commands/time-zone")
    public ResponseEntity<Map<String, Object>> setTimeZone(
            @PathVariable String udid,
            @RequestParam String timeZone) throws Exception {
        appleCommandSenderService.setTimeZone(udid, timeZone);
        return ResponseEntity.ok(commandResponse("TimeZone", udid));
    }

    @Operation(summary = "Set Device Hostname")
    @PostMapping("/{udid}/commands/hostname")
    public ResponseEntity<Map<String, Object>> setHostname(
            @PathVariable String udid,
            @RequestParam String hostname) throws Exception {
        appleCommandSenderService.setHostname(udid, hostname);
        return ResponseEntity.ok(commandResponse("Hostname", udid));
    }

    @Operation(summary = "Toggle Voice Roaming")
    @PostMapping("/{udid}/commands/voice-roaming")
    public ResponseEntity<Map<String, Object>> setVoiceRoaming(
            @PathVariable String udid,
            @RequestParam boolean enabled) throws Exception {
        appleCommandSenderService.setVoiceRoaming(udid, enabled);
        return ResponseEntity.ok(commandResponse("VoiceRoaming", udid));
    }

    @Operation(summary = "Toggle Diagnostic Submission")
    @PostMapping("/{udid}/commands/diagnostic-submission")
    public ResponseEntity<Map<String, Object>> setDiagnosticSubmission(
            @PathVariable String udid,
            @RequestParam boolean enabled) throws Exception {
        appleCommandSenderService.setDiagnosticSubmission(udid, enabled);
        return ResponseEntity.ok(commandResponse("DiagnosticSubmission", udid));
    }

    @Operation(summary = "Toggle App Analytics")
    @PostMapping("/{udid}/commands/app-analytics")
    public ResponseEntity<Map<String, Object>> setAppAnalytics(
            @PathVariable String udid,
            @RequestParam boolean enabled) throws Exception {
        appleCommandSenderService.setAppAnalytics(udid, enabled);
        return ResponseEntity.ok(commandResponse("AppAnalytics", udid));
    }

    @Operation(summary = "Request Certificate List")
    @PostMapping("/{udid}/commands/certificates")
    public ResponseEntity<Map<String, Object>> requestCertificateList(@PathVariable String udid) throws Exception {
        appleCommandSenderService.requestCertificateList(udid);
        return ResponseEntity.ok(commandResponse("CertificateList", udid));
    }

    @Operation(summary = "Disenroll device (soft-delete, keeps history)")
    @PostMapping("/{udid}/disenroll")
    public ResponseEntity<Void> disenrollDevice(@PathVariable String udid) {
        disenrollService.disenroll(udid);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> commandResponse(String commandType, String udid) {
        return Map.of(
                "status", "queued",
                "commandType", commandType,
                "udid", udid
        );
    }
}
