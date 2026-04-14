package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.web.RawResponse;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog.AuditAction;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog.AuditTargetType;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleAbmTokenService;
import com.arcyintel.arcops.apple_mdm.services.apple.cert.AppleCertificationService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentAuditService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cert")
@Tag(name = "Apple Certificate Management", description = "Apple Push, DEP and VPP certificate and token operations.")
public class CertificationController {

    private static final Logger logger = LoggerFactory.getLogger(CertificationController.class);

    private final AppleCertificationService appleCertificationService;
    private final AppleAbmTokenService appleAbmTokenService;
    private final EnrollmentStatusService enrollmentStatusService;
    private final EnrollmentAuditService enrollmentAuditService;

    @Operation(summary = "Generate Certificate Plist", description = "Returns the Base64 encoded plist file used for certificate identifier generation. Company name and email are used in the CSR subject.")
    @GetMapping("/generate-identifier-plist")
    public ResponseEntity<ByteArrayResource> generateCertificatePlist(
            @Parameter(description = "Company/Organization name for CSR subject")
            @RequestParam(required = false) String companyName,
            @Parameter(description = "Contact email for CSR subject")
            @RequestParam(required = false) String email,
            HttpServletRequest request) throws Exception {
        try {
            String plistBase64 = appleCertificationService.generatePlist(companyName, email);
            byte[] fileContent = plistBase64.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=plist.b64");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

            enrollmentAuditService.logSuccess(
                    AuditAction.GENERATE,
                    AuditTargetType.APNS_CERT,
                    "CSR plist generated for " + companyName,
                    "{\"companyName\":\"" + companyName + "\",\"email\":\"" + email + "\"}",
                    request
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (Exception ex) {
            enrollmentAuditService.logFailure(
                    AuditAction.GENERATE,
                    AuditTargetType.APNS_CERT,
                    "Failed to generate CSR plist",
                    ex.getMessage(),
                    request
            );
            throw ex;
        }
    }

    @Operation(summary = "Upload Apple Push Certificate", description = "Uploads the Apple push certificate to the server and refreshes cached credentials.")
    @RawResponse
    @PostMapping(value = "/upload-apple-cert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadAppleCertificate(
            @Parameter(description = "Apple push certificate file (.pem)", required = true)
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            appleCertificationService.uploadCertificate(file);

            // Mark push cert as renewed (clears vendor renewal warning if any)
            enrollmentStatusService.markPushCertRenewed();

            // Refresh certificate info and hot-reload credentials
            enrollmentStatusService.refreshCertificateInfo();
            try {
                enrollmentStatusService.hotReloadCredentials();
                logger.info("Credentials hot-reloaded after certificate upload.");
            } catch (Exception e) {
                logger.warn("Hot-reload failed after certificate upload: {}", e.getMessage());
            }

            enrollmentAuditService.logSuccess(
                    AuditAction.UPLOAD,
                    AuditTargetType.APNS_CERT,
                    "Apple push certificate uploaded: " + file.getOriginalFilename(),
                    "{\"fileName\":\"" + file.getOriginalFilename() + "\",\"size\":" + file.getSize() + "}",
                    request
            );

            return ResponseEntity.ok("Apple push certificate uploaded successfully.");
        } catch (IOException e) {
            enrollmentAuditService.logFailure(
                    AuditAction.UPLOAD,
                    AuditTargetType.APNS_CERT,
                    "Failed to upload Apple push certificate",
                    e.getMessage(),
                    request
            );
            throw new BusinessException("CERT_ERROR", "Failed to upload Apple push certificate: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "Upload DEP Server Token", description = "Uploads the DEP Server Token (.p7m file) from Apple Business Manager.")
    @RawResponse
    @PostMapping(value = "/upload-server-token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadServerToken(
            @Parameter(description = "DEP Server Token file (.p7m)", required = true)
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        try {
            appleAbmTokenService.uploadServerToken(file);
            // Refresh token info in enrollment status
            enrollmentStatusService.refreshCertificateInfo();

            enrollmentAuditService.logSuccess(
                    AuditAction.UPLOAD,
                    AuditTargetType.DEP_TOKEN,
                    "DEP server token uploaded: " + file.getOriginalFilename(),
                    "{\"fileName\":\"" + file.getOriginalFilename() + "\",\"size\":" + file.getSize() + "}",
                    request
            );

            return ResponseEntity.ok("Server token uploaded and processed successfully.");
        } catch (Exception e) {
            enrollmentAuditService.logFailure(
                    AuditAction.UPLOAD,
                    AuditTargetType.DEP_TOKEN,
                    "Failed to upload DEP server token",
                    e.getMessage(),
                    request
            );
            throw e;
        }
    }

    @Operation(summary = "Upload VPP Token", description = "Uploads the VPP Token file (.vppToken) from Apple Business Manager.")
    @RawResponse
    @PostMapping(value = "/upload-vpp-token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadVppToken(
            @Parameter(description = "VPP Token file (.vppToken)", required = true)
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        try {
            appleAbmTokenService.uploadVppToken(file);
            // Refresh token info in enrollment status
            enrollmentStatusService.refreshCertificateInfo();

            enrollmentAuditService.logSuccess(
                    AuditAction.UPLOAD,
                    AuditTargetType.VPP_TOKEN,
                    "VPP token uploaded: " + file.getOriginalFilename(),
                    "{\"fileName\":\"" + file.getOriginalFilename() + "\",\"size\":" + file.getSize() + "}",
                    request
            );

            return ResponseEntity.ok("VPP token uploaded successfully.");
        } catch (Exception e) {
            enrollmentAuditService.logFailure(
                    AuditAction.UPLOAD,
                    AuditTargetType.VPP_TOKEN,
                    "Failed to upload VPP token",
                    e.getMessage(),
                    request
            );
            throw e;
        }
    }

    @Operation(summary = "Download DEP Certificate", description = "Generates and downloads a DEP certificate in PEM format.")
    @GetMapping("/download-dep-cert")
    public ResponseEntity<byte[]> downloadDepCertificate(HttpServletRequest request) throws Exception {
        try {
            String pem = appleAbmTokenService.generateDepCertificateAndSave();
            byte[] pemBytes = pem.getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename("dep.pem")
                    .build());
            headers.setContentLength(pemBytes.length);

            enrollmentAuditService.logSuccess(
                    AuditAction.GENERATE,
                    AuditTargetType.DEP_CERT,
                    "DEP certificate generated successfully",
                    null,
                    request
            );

            return new ResponseEntity<>(pemBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            enrollmentAuditService.logFailure(
                    AuditAction.GENERATE,
                    AuditTargetType.DEP_CERT,
                    "Failed to generate DEP certificate",
                    e.getMessage(),
                    request
            );
            throw e;
        }
    }

    @Operation(summary = "Renew Vendor Certificate", description = "Protected endpoint for renewing the vendor certificate (mdm.pem). Requires secret password.", hidden = true)
    @RawResponse
    @PostMapping(value = "/renew-vendor-cert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> renewVendorCertificate(
            @Parameter(description = "New vendor certificate file (.pem or .cer)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Secret password for authorization", required = true)
            @RequestParam("secret") String secret,
            HttpServletRequest request) throws Exception {
        try {
            appleCertificationService.renewVendorCertificate(file, secret);

            // Mark vendor cert as renewed - this will trigger a warning for users to renew push cert
            enrollmentStatusService.markVendorCertRenewed();

            // Refresh certificate info after renewal
            enrollmentStatusService.refreshCertificateInfo();

            enrollmentAuditService.logSuccess(
                    AuditAction.RENEW,
                    AuditTargetType.VENDOR_CERT,
                    "Vendor certificate renewed: " + file.getOriginalFilename(),
                    "{\"fileName\":\"" + file.getOriginalFilename() + "\",\"size\":" + file.getSize() + "}",
                    request
            );

            return ResponseEntity.ok("Vendor certificate renewed successfully. Users will need to renew their push certificates.");
        } catch (SecurityException e) {
            logger.warn("Unauthorized vendor certificate renewal attempt.");
            enrollmentAuditService.logFailure(
                    AuditAction.RENEW,
                    AuditTargetType.VENDOR_CERT,
                    "Unauthorized vendor certificate renewal attempt",
                    e.getMessage(),
                    request
            );
            throw e;
        } catch (IllegalArgumentException e) {
            enrollmentAuditService.logFailure(
                    AuditAction.RENEW,
                    AuditTargetType.VENDOR_CERT,
                    "Invalid vendor certificate renewal request",
                    e.getMessage(),
                    request
            );
            throw new BusinessException("VALIDATION_ERROR", "Invalid request: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to renew vendor certificate: {}", e.getMessage(), e);
            enrollmentAuditService.logFailure(
                    AuditAction.RENEW,
                    AuditTargetType.VENDOR_CERT,
                    "Failed to renew vendor certificate",
                    e.getMessage(),
                    request
            );
            throw e;
        }
    }
}