package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleEnrollmentWebAuthService;
import com.arcyintel.arcops.commons.web.RawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Handles web-based enrollment authentication for both:
 * - DEP (configuration_web_url): Setup Assistant WebView, returns mobileconfig directly
 * - BYOD (Account-Driven Enrollment): ASWebAuthenticationSession, redirects with access-token
 *
 * Supports username/password login and email OTP login (for IdP-synced users without passwords).
 */
@RestController
@RawResponse
@RequestMapping("/mdm/enrollment")
@RequiredArgsConstructor
@Tag(name = "Apple Enrollment Web Auth", description = "Web authentication flow for DEP and Account-Driven Enrollment.")
public class MdmEnrollmentWebAuthController {

    private static final Logger logger = LoggerFactory.getLogger(MdmEnrollmentWebAuthController.class);
    private static final String LOGIN_TEMPLATE = "templates/enrollment-login.html";
    private static final String APPLE_CALLBACK_SCHEME = "apple-remotemanagement-user-login://authentication-results";

    private final AppleEnrollmentWebAuthService webAuthService;

    private String loadLoginTemplate() {
        try {
            return new ClassPathResource(LOGIN_TEMPLATE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load login template: {}", e.getMessage());
            return "<html><body><h1>Error</h1><p>Login page unavailable.</p></body></html>";
        }
    }

    /**
     * Serves the login page.
     * - DEP mode (no mode param): Setup Assistant WebView
     * - BYOD mode (?mode=byod): ASWebAuthenticationSession from Account-Driven Enrollment
     */
    @Operation(summary = "Show Login Page")
    @GetMapping(value = "/web-auth", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> showLoginPage(
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "user-identifier", required = false) String userIdentifier) {
        // Apple's ASWebAuthenticationSession adds ?user-identifier=<email> automatically.
        // If user-identifier is present, this is a BYOD enrollment (not DEP).
        if (userIdentifier != null && !userIdentifier.isBlank()) {
            mode = "byod";
            logger.info("=== Web Auth Login Page GET (BYOD via user-identifier={}) === ASWebAuthenticationSession opened!", userIdentifier);
        } else {
            logger.info("=== Web Auth Login Page GET (mode={}) === DEP/Setup Assistant web view", mode);
        }
        String html = injectProviderData(loadLoginTemplate());
        html = injectMode(html, mode);
        html = injectUserIdentifier(html, userIdentifier);
        return ResponseEntity.ok(html);
    }

    /**
     * Handles username/password login form submission.
     * In BYOD mode, redirects to Apple callback with access-token.
     * In DEP mode, returns the enrollment profile directly.
     */
    @Operation(summary = "Handle Web Auth Login")
    @PostMapping(value = "/web-auth", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> handleLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "user-identifier", required = false) String userIdentifier
    ) {
        logger.info("Web auth login attempt for username: {} (mode={}, user-identifier={})", username, mode, userIdentifier);

        if (isIdentityMismatch(userIdentifier, username)) {
            logger.warn("BYOD identity mismatch: user-identifier={}, submitted username={}", userIdentifier, username);
            String errorHtml = injectUserIdentifier(injectMode(injectProviderData(
                    loadLoginTemplate()
                            .replace("Authentication failed. Please check your credentials.",
                                    "You must sign in with the account you used to start enrollment.")
                            .replace("class=\"error-msg\"", "class=\"error-msg visible\"")
            ), mode), userIdentifier);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml);
        }

        String enrollmentToken = webAuthService.authenticateAndCreateEnrollmentToken(username, password);

        if (enrollmentToken == null) {
            logger.warn("Web auth login failed for username: {}", username);
            String errorHtml = injectUserIdentifier(injectMode(injectProviderData(
                    loadLoginTemplate().replace("class=\"error-msg\"", "class=\"error-msg visible\"")
            ), mode), userIdentifier);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml);
        }

        if ("byod".equalsIgnoreCase(mode)) {
            return redirectToAppleCallback(enrollmentToken, username);
        }
        return returnEnrollmentProfile(enrollmentToken, username);
    }

    /**
     * Sends an OTP code to the given email if it matches a known identity.
     * Used for IdP-synced users who don't have local passwords.
     */
    @Operation(summary = "Send OTP Code")
    @PostMapping(value = "/web-auth/otp/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> sendOtp(
            @RequestParam String email,
            @RequestParam(value = "user-identifier", required = false) String userIdentifier) {
        logger.info("OTP send request for email: {} (user-identifier={})", email, userIdentifier);

        if (isIdentityMismatch(userIdentifier, email)) {
            logger.warn("BYOD identity mismatch on OTP send: user-identifier={}, email={}", userIdentifier, email);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "error", "message", "You must use the account you started enrollment with."));
        }

        boolean sent = webAuthService.sendOtp(email);
        if (!sent) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "No matching account found."));
        }

        return ResponseEntity.ok(Map.of("status", "ok", "message", "Verification code sent."));
    }

    /**
     * Verifies OTP and completes enrollment.
     * In BYOD mode, redirects to Apple callback with access-token.
     * In DEP mode, returns the enrollment profile directly.
     */
    @Operation(summary = "Verify OTP and Enroll")
    @PostMapping(value = "/web-auth/otp/verify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> verifyOtp(
            @RequestParam String email,
            @RequestParam String otp,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "user-identifier", required = false) String userIdentifier
    ) {
        logger.info("OTP verify attempt for email: {} (mode={}, user-identifier={})", email, mode, userIdentifier);

        if (isIdentityMismatch(userIdentifier, email)) {
            logger.warn("BYOD identity mismatch on OTP verify: user-identifier={}, email={}", userIdentifier, email);
            String errorHtml = injectUserIdentifier(injectMode(injectProviderData(
                    loadLoginTemplate()
                            .replace("Authentication failed. Please check your credentials.",
                                    "You must sign in with the account you used to start enrollment.")
                            .replace("class=\"error-msg\"", "class=\"error-msg visible\"")
            ), mode), userIdentifier);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml);
        }

        String enrollmentToken = webAuthService.verifyOtpAndCreateEnrollmentToken(email, otp);

        if (enrollmentToken == null) {
            logger.warn("OTP verification failed for email: {}", email);
            String errorHtml = injectUserIdentifier(injectMode(injectProviderData(
                    loadLoginTemplate()
                            .replace("Authentication failed. Please check your credentials.",
                                    "Invalid or expired verification code.")
                            .replace("class=\"error-msg\"", "class=\"error-msg visible\"")
            ), mode), userIdentifier);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml);
        }

        if ("byod".equalsIgnoreCase(mode)) {
            return redirectToAppleCallback(enrollmentToken, "otp:" + email);
        }
        return returnEnrollmentProfile(enrollmentToken, "otp:" + email);
    }

    /**
     * BYOD mode: redirect to Apple's callback scheme with the enrollment token as access-token.
     * The device captures this redirect and uses the token for the enrollment POST.
     */
    private ResponseEntity<?> redirectToAppleCallback(String enrollmentToken, String logContext) {
        String callbackUrl = APPLE_CALLBACK_SCHEME + "?access-token=" + enrollmentToken;
        logger.info("BYOD auth successful for {}. Redirecting to Apple callback.", logContext);
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .location(URI.create(callbackUrl))
                .build();
    }

    private ResponseEntity<?> returnEnrollmentProfile(String enrollmentToken, String logContext) {
        try {
            String mobileconfig = webAuthService.generateEnrollProfileWithToken(enrollmentToken);
            logger.info("Web auth login successful for {}. Returning enrollment profile.", logContext);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-apple-aspen-config"))
                    .header("Content-Disposition", "attachment; filename=\"enroll.mobileconfig\"")
                    .body(mobileconfig.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Failed to generate enrollment profile for {}", logContext, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body><h1>Error</h1><p>Failed to generate enrollment profile.</p></body></html>");
        }
    }

    private String injectMode(String html, String mode) {
        String modeValue = (mode != null && !mode.isBlank()) ? mode : "";
        return html.replace("/*AUTH_MODE*/''", "/*AUTH_MODE*/'" + modeValue + "'");
    }

    private String injectUserIdentifier(String html, String userIdentifier) {
        String value = (userIdentifier != null && !userIdentifier.isBlank()) ? userIdentifier : "";
        return html.replace("/*USER_IDENTIFIER*/''", "/*USER_IDENTIFIER*/'" + value + "'");
    }

    private String extractUsername(String identifier) {
        if (identifier == null) return null;
        int atIdx = identifier.indexOf('@');
        return atIdx > 0 ? identifier.substring(0, atIdx).toLowerCase() : identifier.toLowerCase();
    }

    /**
     * In BYOD mode, validates that the submitted identifier's username part
     * matches the user-identifier from enrollment initiation.
     */
    private boolean isIdentityMismatch(String userIdentifier, String submittedIdentifier) {
        if (userIdentifier == null || userIdentifier.isBlank()) return false;
        if (submittedIdentifier == null || submittedIdentifier.isBlank()) return true;
        String expected = extractUsername(userIdentifier);
        String actual = extractUsername(submittedIdentifier);
        return !expected.equals(actual);
    }

    private String injectProviderData(String html) {
        List<String> types = webAuthService.getConnectedProviderTypes();
        boolean hasProviders = !types.isEmpty();
        html = html.replace("/*OTP_ENABLED*/false", "/*OTP_ENABLED*/" + hasProviders);

        String typesJson;
        if (types.isEmpty()) {
            typesJson = "[]";
        } else {
            typesJson = "[\"" + String.join("\",\"", types) + "\"]";
        }
        html = html.replace("/*PROVIDER_TYPES*/[]", "/*PROVIDER_TYPES*/" + typesJson);
        return html;
    }
}
