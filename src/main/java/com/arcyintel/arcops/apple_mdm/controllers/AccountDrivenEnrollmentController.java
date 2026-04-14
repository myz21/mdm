package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.services.enrollment.AccountDrivenEnrollmentService;
import com.arcyintel.arcops.commons.web.RawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;

/**
 * Apple Account-Driven Enrollment endpoint.
 *
 * Handles the authentication flow for BYOD (User Enrollment) and ADDE
 * (Account-Driven Device Enrollment). The device discovers this endpoint
 * via .well-known/com.apple.remotemanagement.
 *
 * Flow:
 * 1. Device POSTs without credentials → 401 + auth challenge
 * 2. Device POSTs with credentials    → 200 + enrollment profile
 */
@RestController
@RawResponse
@RequestMapping("/mdm/account-enrollment")
@RequiredArgsConstructor
@Tag(name = "Apple Account-Driven Enrollment",
        description = "Handles Account-Driven Enrollment authentication (Simple/OAuth2) for BYOD and ADDE.")
public class AccountDrivenEnrollmentController {

    private static final Logger logger = LoggerFactory.getLogger(AccountDrivenEnrollmentController.class);

    private final AccountDrivenEnrollmentService enrollmentService;

    @Operation(
            summary = "BYOD Account-Driven Enrollment",
            description = "Handles Account-Driven User Enrollment (BYOD). " +
                    "Returns 401 auth challenge on first request, enrollment profile on authenticated request."
    )
    @PostMapping("/byod")
    public ResponseEntity<?> byodEnrollment(
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Parameter(description = "User's email domain for OAuth2 provider lookup")
            @RequestParam(value = "domain", required = false) String domain,
            HttpServletRequest request) {
        logRequestDetails("BYOD", request, authorizationHeader, domain);
        ResponseEntity<?> response = enrollmentService.processEnrollment(authorizationHeader, "byod", domain);
        logger.info("BYOD enrollment response: status={}, headers={}", response.getStatusCode(), response.getHeaders());
        return response;
    }

    @Operation(
            summary = "ADDE Account-Driven Enrollment",
            description = "Handles Account-Driven Device Enrollment (ADDE). " +
                    "Returns 401 auth challenge on first request, enrollment profile on authenticated request."
    )
    @PostMapping("/adde")
    public ResponseEntity<?> addeEnrollment(
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Parameter(description = "User's email domain for OAuth2 provider lookup")
            @RequestParam(value = "domain", required = false) String domain,
            HttpServletRequest request) {
        logRequestDetails("ADDE", request, authorizationHeader, domain);
        ResponseEntity<?> response = enrollmentService.processEnrollment(authorizationHeader, "adde", domain);
        logger.info("ADDE enrollment response: status={}, headers={}", response.getStatusCode(), response.getHeaders());
        return response;
    }

    private void logRequestDetails(String type, HttpServletRequest request, String authHeader, String domain) {
        logger.info("=== {} Account-Driven Enrollment Request ===", type);
        logger.info("  Remote: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        logger.info("  Method: {} {}", request.getMethod(), request.getRequestURL());
        logger.info("  Query: {}", request.getQueryString());
        logger.info("  Domain param: {}", domain);
        logger.info("  Authorization: {}", authHeader != null ? authHeader.substring(0, Math.min(authHeader.length(), 30)) + "..." : "null");
        logger.info("  Content-Type: {}", request.getContentType());
        logger.info("  Content-Length: {}", request.getContentLength());
        logger.info("  User-Agent: {}", request.getHeader("User-Agent"));

        StringBuilder headers = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!"authorization".equalsIgnoreCase(name)) {
                headers.append("\n    ").append(name).append(": ").append(request.getHeader(name));
            }
        }
        logger.info("  All headers:{}", headers);
    }
}
