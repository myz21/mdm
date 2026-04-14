package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.services.settings.SystemSettingService;
import com.arcyintel.arcops.commons.web.RawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Apple Account-Driven Enrollment service discovery endpoint.
 *
 * When a user enters their Managed Apple ID in device Settings, the device
 * extracts the domain and queries:
 *   GET https://{domain}/.well-known/com.apple.remotemanagement
 *
 * This endpoint returns the MDM server URLs for BYOD (User Enrollment) and
 * ADDE (Account-Driven Device Enrollment).
 *
 * <h3>Reverse Proxy / Gateway Configuration Required:</h3>
 * Since this Spring Boot app runs under context-path {@code /api/apple},
 * the reverse proxy or API gateway MUST route:
 * <pre>
 *   /.well-known/com.apple.remotemanagement -> /api/apple/.well-known/com.apple.remotemanagement
 * </pre>
 *
 * <h3>Apple Account-Driven Enrollment Flow:</h3>
 * <ol>
 *   <li>User enters Managed Apple ID (e.g., user@company.com) in device Settings</li>
 *   <li>Device queries https://company.com/.well-known/com.apple.remotemanagement</li>
 *   <li>This endpoint returns BaseURL for BYOD and/or ADDE</li>
 *   <li>Device authenticates user via Apple Identity Service</li>
 *   <li>Device requests enrollment profile from BaseURL with authentication token</li>
 *   <li>MDM server returns the enrollment profile</li>
 *   <li>Device installs profile and begins MDM enrollment (Authenticate -> TokenUpdate -> Commands)</li>
 * </ol>
 */
@RestController
@RawResponse
@RequiredArgsConstructor
@Tag(name = "Apple Service Discovery",
        description = "Account-Driven Enrollment service discovery endpoint (.well-known/com.apple.remotemanagement)")
public class ServiceDiscoveryController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryController.class);

    private final SystemSettingService systemSettingService;

    @Value("${host}")
    private String apiHost;

    /**
     * Apple Remote Management service discovery endpoint.
     *
     * Returns MDM server information for Account-Driven Enrollment.
     * Supports both BYOD (mdm-byod) and ADDE (mdm-adde) enrollment types.
     *
     * @return JSON response with Servers array containing BaseURL per enrollment version
     */
    @Operation(
            summary = "Apple Remote Management Service Discovery",
            description = "Returns MDM server information for Account-Driven Enrollment discovery. " +
                    "Apple devices query this endpoint to discover the MDM server when a user enters " +
                    "their Managed Apple ID. Must be accessible at " +
                    "https://{domain}/.well-known/com.apple.remotemanagement via reverse proxy."
    )
    @GetMapping(value = "/.well-known/com.apple.remotemanagement", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> serviceDiscovery(
            @RequestParam(value = "user-identifier", required = false) String userIdentifier,
            @RequestParam(value = "model-family", required = false) String modelFamily) {

        logger.info("Service discovery request received (user-identifier={}, model-family={})",
                userIdentifier, modelFamily);

        String domainParam = "";
        if (userIdentifier != null && userIdentifier.contains("@")) {
            String domain = userIdentifier.substring(userIdentifier.indexOf("@") + 1);
            domainParam = "?domain=" + domain;
        }

        String byodBaseUrl = apiHost + "/mdm/account-enrollment/byod" + domainParam;
        String addeBaseUrl = apiHost + "/mdm/account-enrollment/adde" + domainParam;

        // Determine enrollment type from system settings based on model-family
        String enrollmentType = null;
        if (modelFamily != null) {
            Map<String, Object> config = systemSettingService.getValue("account_driven_decision");
            enrollmentType = config.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(modelFamily))
                    .map(e -> (String) e.getValue())
                    .findFirst().orElse(null);
        }

        List<Map<String, Object>> servers = new ArrayList<>();
        if ("byod".equalsIgnoreCase(enrollmentType)) {
            servers.add(Map.of("Version", "mdm-byod", "BaseURL", byodBaseUrl));
        } else if ("adde".equalsIgnoreCase(enrollmentType)) {
            servers.add(Map.of("Version", "mdm-adde", "BaseURL", addeBaseUrl));
        } else {
            servers.add(Map.of("Version", "mdm-byod", "BaseURL", byodBaseUrl));
            servers.add(Map.of("Version", "mdm-adde", "BaseURL", addeBaseUrl));
        }

        Map<String, Object> response = Map.of("Servers", servers);

        return ResponseEntity.ok(response);
    }
}
