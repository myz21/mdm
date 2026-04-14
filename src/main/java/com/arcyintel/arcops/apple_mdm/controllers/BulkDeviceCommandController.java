package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.BulkCommand;
import com.arcyintel.arcops.apple_mdm.models.api.device.BulkCommandRequest;
import com.arcyintel.arcops.apple_mdm.models.api.device.BulkCommandResponse;
import com.arcyintel.arcops.apple_mdm.models.api.device.BulkInstallAppRequest;
import com.arcyintel.arcops.apple_mdm.repositories.BulkCommandRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.BulkCommandTaggingService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.DisenrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/bulk-commands")
@RequiredArgsConstructor
@Tag(name = "Bulk Device Commands", description = "Endpoints for sending commands to multiple Apple devices at once.")
public class BulkDeviceCommandController {

    private static final Logger logger = LoggerFactory.getLogger(BulkDeviceCommandController.class);

    private final AppleCommandSenderService appleCommandSenderService;
    private final BulkCommandRepository bulkCommandRepository;
    private final BulkCommandTaggingService bulkCommandTaggingService;
    private final DisenrollService disenrollService;

    private static final Set<String> SUPPORTED_COMMANDS = Set.of(
            "device-information", "lock", "restart-device", "shutdown", "sync-apps"
    );

    @Operation(summary = "Send bulk command", description = "Sends an MDM command to multiple devices.")
    @PostMapping("/commands/{commandType}")
    public ResponseEntity<BulkCommandResponse> bulkCommand(
            @PathVariable String commandType,
            @Valid @RequestBody BulkCommandRequest request
    ) {
        if (!SUPPORTED_COMMANDS.contains(commandType)) {
            throw new IllegalArgumentException("Unsupported command type: " + commandType);
        }

        BulkCommand bulk = BulkCommand.builder()
                .commandType(commandType)
                .totalDevices(request.getUdids().size())
                .build();
        bulkCommandRepository.save(bulk);

        int total = request.getUdids().size();
        int success = 0;
        int failed = 0;
        List<BulkCommandResponse.FailureDetail> failures = new ArrayList<>();
        List<String> succeededUdids = new ArrayList<>();

        for (String udid : request.getUdids()) {
            try {
                switch (commandType) {
                    case "device-information" -> appleCommandSenderService.queryDeviceInformation(udid, false);
                    case "lock" -> appleCommandSenderService.lockDevice(udid, null, null);
                    case "restart-device" -> appleCommandSenderService.restartDevice(udid, null);
                    case "shutdown" -> appleCommandSenderService.shutDownDevice(udid);
                    case "sync-apps" -> appleCommandSenderService.syncAppInventory(udid);
                }
                success++;
                succeededUdids.add(udid);
            } catch (Exception e) {
                failed++;
                failures.add(BulkCommandResponse.FailureDetail.builder()
                        .udid(udid).reason(e.getMessage()).build());
                logger.warn("Bulk command '{}' failed for UDID {}: {}", commandType, udid, e.getMessage());
            }
        }

        // Tag commands asynchronously — apple_command records are saved async by pushCommand
        // Run tagging in background thread after async saves complete
        final UUID bulkId = bulk.getId();
        final List<String> tagUdids = List.copyOf(succeededUdids);
        CompletableFuture.runAsync(() -> bulkCommandTaggingService.scheduleTagging(bulkId, tagUdids));

        logger.info("Bulk command '{}': id={}, total={}, success={}, failed={}", commandType, bulk.getId(), total, success, failed);
        return ResponseEntity.ok(BulkCommandResponse.builder()
                .total(total).success(success).failed(failed).failures(failures)
                .bulkCommandId(bulk.getId())
                .build());
    }

    @Operation(summary = "Bulk install app")
    @PostMapping("/commands/install-app")
    public ResponseEntity<BulkCommandResponse> bulkInstallApp(
            @Valid @RequestBody BulkInstallAppRequest request
    ) {
        if (request.getTrackId() == null && (request.getIdentifier() == null || request.getIdentifier().isBlank())) {
            throw new IllegalArgumentException("Either trackId or identifier must be provided");
        }

        BulkCommand bulk = BulkCommand.builder()
                .commandType("install-app")
                .totalDevices(request.getUdids().size())
                .payload(Map.of(
                        "trackId", request.getTrackId() != null ? request.getTrackId() : "",
                        "identifier", request.getIdentifier() != null ? request.getIdentifier() : ""
                ))
                .build();
        bulkCommandRepository.save(bulk);

        int total = request.getUdids().size();
        int success = 0;
        int failed = 0;
        List<BulkCommandResponse.FailureDetail> failures = new ArrayList<>();
        List<String> succeededUdids = new ArrayList<>();

        for (String udid : request.getUdids()) {
            try {
                if (request.getTrackId() != null) {
                    appleCommandSenderService.installApp(udid, request.getTrackId(), false, false, null);
                } else {
                    appleCommandSenderService.installApp(udid, request.getIdentifier(), false, false, null);
                }
                success++;
                succeededUdids.add(udid);
            } catch (Exception e) {
                failed++;
                failures.add(BulkCommandResponse.FailureDetail.builder()
                        .udid(udid).reason(e.getMessage()).build());
                logger.warn("Bulk install-app failed for UDID {}: {}", udid, e.getMessage());
            }
        }

        // Run tagging in background thread after async saves complete
        final UUID bulkId = bulk.getId();
        final List<String> tagUdids = List.copyOf(succeededUdids);
        CompletableFuture.runAsync(() -> bulkCommandTaggingService.scheduleTagging(bulkId, tagUdids));

        logger.info("Bulk install-app: id={}, total={}, success={}, failed={}", bulk.getId(), total, success, failed);
        return ResponseEntity.ok(BulkCommandResponse.builder()
                .total(total).success(success).failed(failed).failures(failures)
                .bulkCommandId(bulk.getId())
                .build());
    }

    @Operation(summary = "Bulk disenroll devices (soft-delete, keeps history)")
    @PostMapping("/disenroll")
    public ResponseEntity<Map<String, Object>> bulkDisenroll(@Valid @RequestBody BulkCommandRequest request) {
        DisenrollService.DisenrollResult result = disenrollService.bulkDisenroll(request.getUdids());
        return ResponseEntity.ok(Map.of(
                "total", result.total(),
                "success", result.success(),
                "failed", result.failed()
        ));
    }
}
