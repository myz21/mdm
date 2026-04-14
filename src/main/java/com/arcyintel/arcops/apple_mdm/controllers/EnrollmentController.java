package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.enrollment.EnrollmentAuditLogDto;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.GetEnrollmentStatusDto;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentAuditService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/enrollment")
@Tag(name = "Enrollment Status", description = "MDM enrollment progress tracking and certificate status monitoring.")
public class EnrollmentController {

    private final EnrollmentStatusService enrollmentStatusService;
    private final EnrollmentAuditService enrollmentAuditService;

    @Operation(summary = "Get Enrollment Status", description = "Returns the current enrollment status including progress and all certificate/token information with expiry dates.")
    @GetMapping("/status")
    public ResponseEntity<GetEnrollmentStatusDto> getEnrollmentStatus() {
        GetEnrollmentStatusDto status = enrollmentStatusService.getEnrollmentStatus();
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Update Current Step", description = "Updates the current step of the enrollment wizard.")
    @PutMapping("/status/step/{step}")
    public ResponseEntity<GetEnrollmentStatusDto> updateCurrentStep(
            @Parameter(description = "Step number (1-6)")
            @PathVariable int step) {
        if (step < 1 || step > 6) {
            throw new IllegalArgumentException("Step must be between 1 and 6");
        }
        GetEnrollmentStatusDto status = enrollmentStatusService.updateCurrentStep(step);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Complete Step", description = "Marks a step as completed and advances to the next step.")
    @PostMapping("/status/step/{step}/complete")
    public ResponseEntity<GetEnrollmentStatusDto> completeStep(
            @Parameter(description = "Step number to mark as completed (1-6)")
            @PathVariable int step) {
        if (step < 1 || step > 6) {
            throw new IllegalArgumentException("Step must be between 1 and 6");
        }
        GetEnrollmentStatusDto status = enrollmentStatusService.completeStep(step);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Reset Steps", description = "Removes specified steps from completed list. Used when re-initiating a step group (APNS: 1-2, ABM: 3-6).")
    @PostMapping("/status/reset-steps")
    public ResponseEntity<GetEnrollmentStatusDto> resetSteps(
            @RequestBody java.util.List<Integer> stepsToReset) {
        if (stepsToReset == null || stepsToReset.isEmpty()) {
            throw new IllegalArgumentException("Steps to reset must not be empty");
        }
        boolean allValid = stepsToReset.stream().allMatch(s -> s >= 1 && s <= 6);
        if (!allValid) {
            throw new IllegalArgumentException("All steps must be between 1 and 6");
        }
        GetEnrollmentStatusDto status = enrollmentStatusService.resetSteps(stepsToReset);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Mark Enrollment Completed", description = "Marks the entire enrollment process as completed.")
    @PostMapping("/status/complete")
    public ResponseEntity<GetEnrollmentStatusDto> markEnrollmentCompleted() {
        GetEnrollmentStatusDto status = enrollmentStatusService.markEnrollmentCompleted();
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Refresh Certificate Info", description = "Refreshes all certificate and token information from disk. Call this after uploading new certificates.")
    @PostMapping("/status/refresh")
    public ResponseEntity<GetEnrollmentStatusDto> refreshCertificateInfo() {
        GetEnrollmentStatusDto status = enrollmentStatusService.refreshCertificateInfo();
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Hot Reload Credentials", description = "Reloads all cached credentials (APNS client, private keys) without service restart. Call this after renewing certificates.")
    @PostMapping("/hot-reload")
    public ResponseEntity<String> hotReloadCredentials() throws Exception {
        enrollmentStatusService.hotReloadCredentials();
        return ResponseEntity.ok("Credentials reloaded successfully.");
    }

    @Operation(summary = "Get Audit Logs", description = "Returns paginated audit logs for certificate and token operations.")
    @GetMapping("/logs")
    public ResponseEntity<Page<EnrollmentAuditLogDto>> getAuditLogs(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        Page<EnrollmentAuditLogDto> logs = enrollmentAuditService.getLogs(page, size);
        return ResponseEntity.ok(logs);
    }
}
