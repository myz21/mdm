package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentProfileType;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.SendEnrollmentEmailDto;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleEnrollmentService;
import com.arcyintel.arcops.apple_mdm.services.email.EnrollmentEmailService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentProfileGenerator;
import com.arcyintel.arcops.commons.web.RawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controller responsible for serving the enrollment profile (mobileconfig) file to devices.
 */
@RestController
@RawResponse
@RequestMapping("/mdm/enrollment")
@RequiredArgsConstructor
@Tag(name = "Apple Enrollment Profile", description = "Endpoints for generating and downloading the MDM enrollment profile.")
public class MdmEnrollmentProfileController {

    private static final Logger logger = LoggerFactory.getLogger(MdmEnrollmentProfileController.class);

    private final AppleEnrollmentService appleEnrollmentService;
    private final EnrollmentProfileGenerator enrollmentProfileGenerator;
    private final EnrollmentEmailService enrollmentEmailService;

    @Value("${host}")
    private String apiHost;

    @Operation(
            summary = "Download MDM Enrollment Profile",
            description = "Generates and downloads the enrollment profile (enroll.mobileconfig) required for Apple device MDM enrollment."
    )
    @PostMapping()
    public ResponseEntity<ByteArrayResource> downloadEnrollProfile() {
        try {
            String mobileconfigContent = appleEnrollmentService.generateEnrollProfile();
            byte[] fileContent = mobileconfigContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=enroll.mobileconfig");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/x-apple-aspen-config");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(
            summary = "Download MDM Enrollment Profile (Legacy/DEP)",
            description = "Generates and downloads the enrollment profile via GET method for legacy compatibility."
    )
    @GetMapping()
    public ResponseEntity<ByteArrayResource> getEnrollProfile() {
        try {
            String mobileconfigContent = appleEnrollmentService.generateEnrollProfile();
            byte[] fileContent = mobileconfigContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=enroll.mobileconfig");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/x-apple-aspen-config");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generates and serves the User Enrollment profile for BYOD (Account-Driven User Enrollment).
     *
     * @param managedAppleId The Managed Apple ID for the user
     * @return ResponseEntity containing the generated mobileconfig as a downloadable file
     */
    @Operation(
            summary = "Download User Enrollment Profile (BYOD)",
            description = "Generates and downloads the User Enrollment profile for BYOD. " +
                    "This enrollment type provides privacy-focused management where personal data remains separate."
    )
    @PostMapping("/user")
    public ResponseEntity<ByteArrayResource> downloadUserEnrollmentProfile(
            @Parameter(description = "The Managed Apple ID for the user", required = true)
            @RequestParam String managedAppleId) {
        try {
            logger.info("Generating User Enrollment profile for: {}", managedAppleId);
            String mobileconfigContent = enrollmentProfileGenerator.generateUserEnrollmentProfile(managedAppleId);
            byte[] fileContent = mobileconfigContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=user-enrollment.mobileconfig");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/x-apple-aspen-config");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for User Enrollment: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            logger.error("Error generating User Enrollment profile", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generates and serves the Account-Driven Device Enrollment profile.
     *
     * @param managedAppleId The Managed Apple ID for the user
     * @return ResponseEntity containing the generated mobileconfig as a downloadable file
     */
    @Operation(
            summary = "Download Account-Driven Device Enrollment Profile",
            description = "Generates and downloads the Account-Driven Device Enrollment (ADDE) profile. " +
                    "This enrollment type provides full MDM control initiated via Managed Apple ID."
    )
    @PostMapping("/adde")
    public ResponseEntity<ByteArrayResource> downloadAccountDrivenDeviceEnrollmentProfile(
            @Parameter(description = "The Managed Apple ID for the user", required = true)
            @RequestParam String managedAppleId) {
        try {
            logger.info("Generating Account-Driven Device Enrollment profile for: {}", managedAppleId);
            String mobileconfigContent = enrollmentProfileGenerator.generateAccountDrivenDeviceEnrollmentProfile(managedAppleId);
            byte[] fileContent = mobileconfigContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=adde-enrollment.mobileconfig");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/x-apple-aspen-config");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for ADDE: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            logger.error("Error generating ADDE profile", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generic endpoint to generate enrollment profile based on type.
     *
     * @param type The enrollment profile type
     * @param managedAppleId The Managed Apple ID (required for USER_ENROLLMENT and ACCOUNT_DRIVEN_DEVICE)
     * @return ResponseEntity containing the generated mobileconfig as a downloadable file
     */
    @Operation(
            summary = "Download Enrollment Profile by Type",
            description = "Generates and downloads the enrollment profile based on the specified type. " +
                    "For USER_ENROLLMENT and ACCOUNT_DRIVEN_DEVICE types, managedAppleId is required."
    )
    @PostMapping("/generate")
    public ResponseEntity<ByteArrayResource> downloadEnrollmentProfileByType(
            @Parameter(description = "The enrollment profile type", required = true)
            @RequestParam EnrollmentProfileType type,
            @Parameter(description = "The Managed Apple ID (required for USER_ENROLLMENT and ACCOUNT_DRIVEN_DEVICE)")
            @RequestParam(required = false) String managedAppleId) {
        try {
            logger.info("Generating {} enrollment profile", type);
            String mobileconfigContent = enrollmentProfileGenerator.generateProfile(type, managedAppleId);
            byte[] fileContent = mobileconfigContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            String filename = switch (type) {
                case DEVICE -> "enroll.mobileconfig";
                case USER_ENROLLMENT -> "user-enrollment.mobileconfig";
                case ACCOUNT_DRIVEN_DEVICE -> "adde-enrollment.mobileconfig";
            };

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
            headers.add(HttpHeaders.CONTENT_TYPE, "application/x-apple-aspen-config");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            logger.error("Error generating enrollment profile", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Returns the enrollment URL for QR code generation.
     *
     * @return Map containing the enrollment URL
     */
    @Operation(
            summary = "Get Enrollment URL",
            description = "Returns the enrollment URL that can be used to generate a QR code for device enrollment."
    )
    @GetMapping("/url")
    public ResponseEntity<Map<String, String>> getEnrollmentUrl() {
        String enrollmentUrl = apiHost + "/mdm/enrollment";
        return ResponseEntity.ok(Map.of(
                "url", enrollmentUrl,
                "description", "Scan this QR code with your device camera to start enrollment"
        ));
    }

    /**
     * Sends an enrollment invitation email to the specified user.
     *
     * @param request The email request containing recipient and optional message
     * @return Success response
     */
    @Operation(
            summary = "Send Enrollment Email",
            description = "Sends an enrollment invitation email to the specified user with instructions on how to enroll their device."
    )
    @PostMapping("/send-email")
    public ResponseEntity<Map<String, String>> sendEnrollmentEmail(
            @Valid @RequestBody SendEnrollmentEmailDto request) {
        try {
            logger.info("Sending enrollment email to: {}", request.getEmail());
            enrollmentEmailService.sendEnrollmentEmail(
                    request.getEmail(),
                    request.getUserName(),
                    request.getCustomMessage()
            );
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Enrollment email sent successfully to " + request.getEmail()
            ));
        } catch (Exception ex) {
            logger.error("Failed to send enrollment email to: {}", request.getEmail(), ex);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to send enrollment email: " + ex.getMessage()
            ));
        }
    }
}