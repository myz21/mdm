package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.agent.AgentAuthRequest;
import com.arcyintel.arcops.commons.web.RawResponse;
import com.arcyintel.arcops.apple_mdm.models.api.agent.AgentAuthResponse;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentAuthService;
import com.arcyintel.arcops.apple_mdm.utils.HttpRequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication endpoint for the ArcOps iOS/macOS agent app.
 * Validates credentials against AppleIdentity and returns a session token.
 */
@RestController
@RawResponse
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "Agent Auth", description = "Agent app authentication")
public class AgentAuthController {

    private final AgentAuthService agentAuthService;

    @Operation(summary = "Authenticate agent app")
    @PostMapping("/auth")
    public ResponseEntity<?> authenticate(@RequestBody AgentAuthRequest request,
                                          HttpServletRequest httpRequest) {
        AgentAuthResponse response = agentAuthService.authenticate(request, HttpRequestUtils.extractClientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Send OTP code to email for agent sign-in")
    @PostMapping("/auth/otp/send")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        boolean sent = agentAuthService.sendOtp(email);
        if (!sent) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No active account found for this email."));
        }
        return ResponseEntity.ok(Map.of("message", "Verification code sent."));
    }

    @Operation(summary = "Verify OTP and authenticate agent app")
    @PostMapping("/auth/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body,
                                       HttpServletRequest httpRequest) {
        AgentAuthResponse response = agentAuthService.authenticateWithOtp(
                body.get("email"),
                body.get("otp"),
                body.get("deviceSerialNumber"),
                body.get("agentVersion"),
                HttpRequestUtils.extractClientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout agent session")
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                    HttpServletRequest httpRequest) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        agentAuthService.logout(token, HttpRequestUtils.extractClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @Operation(summary = "Validate agent session token")
    @GetMapping("/auth/validate")
    public ResponseEntity<?> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!agentAuthService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(Map.of("valid", true));
    }
}
