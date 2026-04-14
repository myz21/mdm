package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.configs.enrollment.AccountDrivenEnrollmentProperties;
import com.arcyintel.arcops.apple_mdm.domains.AppleIdentity;
import com.arcyintel.arcops.apple_mdm.domains.OAuth2ProviderConfig;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.AuthenticatedUser;
import com.arcyintel.arcops.apple_mdm.repositories.AppleIdentityRepository;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AccountDrivenAuthStrategy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SimpleAuthStrategy implements AccountDrivenAuthStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SimpleAuthStrategy.class);

    private final AccountDrivenEnrollmentProperties properties;
    private final AppleIdentityRepository appleIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean supports(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.startsWith("Basic ");
    }

    @Override
    public ResponseEntity<?> buildChallengeResponse(OAuth2ProviderConfig config) {
        String realm = properties.getSimple().getRealm();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + realm + "\"")
                .build();
    }

    @Override
    public AuthenticatedUser authenticate(String authorizationHeader, OAuth2ProviderConfig config) {
        if (!supports(authorizationHeader)) {
            return null;
        }

        String base64Credentials = authorizationHeader.substring("Basic ".length());
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Simple auth: failed to decode Basic credentials");
            return null;
        }

        int colonIndex = decoded.indexOf(':');
        if (colonIndex < 0) {
            logger.warn("Simple auth: malformed credentials");
            return null;
        }

        String username = decoded.substring(0, colonIndex);
        String password = decoded.substring(colonIndex + 1);

        Optional<AppleIdentity> identityOpt = appleIdentityRepository.findByUsername(username);
        if (identityOpt.isEmpty()) {
            // Fallback: try by email
            identityOpt = appleIdentityRepository.findByEmail(username);
        }

        if (identityOpt.isEmpty()) {
            logger.warn("Simple auth: identity not found for: {}", username);
            return null;
        }

        AppleIdentity identity = identityOpt.get();

        if (!"ACTIVE".equals(identity.getStatus())) {
            logger.warn("Simple auth: identity not active: {}", username);
            return null;
        }

        if (identity.getPasswordHash() == null || identity.getPasswordHash().isBlank()) {
            logger.warn("Simple auth: no password set for: {}", username);
            return null;
        }

        if (!passwordEncoder.matches(password, identity.getPasswordHash())) {
            logger.warn("Simple auth: invalid password for: {}", username);
            return null;
        }

        logger.info("Simple auth: user '{}' authenticated successfully", username);

        return AuthenticatedUser.builder()
                .username(identity.getUsername())
                .email(identity.getEmail())
                .fullName(identity.getFullName())
                .externalId(identity.getId().toString())
                .managedAppleId(identity.getEmail())
                .identitySource("WEB_AUTH")
                .build();
    }
}
