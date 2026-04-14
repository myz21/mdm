package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.AppleIdentity;
import com.arcyintel.arcops.apple_mdm.domains.OAuth2ProviderConfig;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.AuthenticatedUser;
import com.arcyintel.arcops.apple_mdm.repositories.AppleIdentityRepository;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AccountDrivenAuthStrategy;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleEnrollmentWebAuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Bearer token authentication for Account-Driven Enrollment.
 *
 * Apple supports two authentication methods:
 *   Simple:  WWW-Authenticate: Bearer method="apple-as-web", url="<auth_page>"
 *   OAuth2:  WWW-Authenticate: Bearer method="apple-oauth2", authorization-url="...", token-url="...", ...
 *
 * Currently uses "apple-as-web" (simple) — MDM server hosts its own login page.
 * The device opens ASWebAuthenticationSession to load the auth URL.
 *
 * Authentication: Validates Bearer tokens — first tries enrollment token (from web auth),
 * then falls back to JWT validation (for IdP tokens).
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthStrategy implements AccountDrivenAuthStrategy {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthStrategy.class);
    private static final String APPLE_AS_WEB_METHOD = "apple-as-web";

    @Value("${host}")
    private String apiHost;

    private final AppleEnrollmentWebAuthService webAuthService;
    private final AppleIdentityRepository appleIdentityRepository;

    private final ConcurrentHashMap<String, JwtDecoder> jwtDecoderCache = new ConcurrentHashMap<>();

    @Override
    public boolean supports(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.startsWith("Bearer ");
    }

    @Override
    public ResponseEntity<?> buildChallengeResponse(OAuth2ProviderConfig config) {
        // Apple docs: device appends ?user-identifier=<email> to this URL when opening web view.
        // Do NOT add ?mode=byod here — the device replaces query params with user-identifier.
        // BYOD mode is detected server-side by the presence of user-identifier param.
        String authUrl = apiHost + "/mdm/enrollment/web-auth";

        String wwwAuthenticate = String.format(
                "Bearer method=\"%s\", url=\"%s\"",
                APPLE_AS_WEB_METHOD, authUrl
        );

        logger.info("Account-driven enrollment: returning apple-as-web challenge, url={}", authUrl);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate)
                .build();
    }

    @Override
    public AuthenticatedUser authenticate(String authorizationHeader, OAuth2ProviderConfig config) {
        if (!supports(authorizationHeader)) {
            return null;
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        // 1. Try to resolve as enrollment token (from web auth login page)
        AuthenticatedUser user = resolveEnrollmentToken(token);
        if (user != null) {
            return user;
        }

        // 2. Fall back to JWT validation (for direct IdP tokens)
        if (config != null && config.getJwkSetUri() != null) {
            return validateJwt(token, config);
        }

        logger.warn("Bearer auth: token could not be resolved as enrollment token or JWT");
        return null;
    }

    private AuthenticatedUser resolveEnrollmentToken(String token) {
        UUID identityId = webAuthService.resolveEnrollmentToken(token);
        if (identityId == null) {
            return null;
        }

        Optional<AppleIdentity> identityOpt = appleIdentityRepository.findById(identityId);
        if (identityOpt.isEmpty()) {
            logger.warn("Bearer auth: enrollment token resolved to unknown identity: {}", identityId);
            return null;
        }

        AppleIdentity identity = identityOpt.get();
        logger.info("Bearer auth: enrollment token resolved for user '{}'", identity.getUsername());

        return AuthenticatedUser.builder()
                .username(identity.getUsername())
                .email(identity.getEmail())
                .fullName(identity.getFullName())
                .externalId(identity.getId().toString())
                .managedAppleId(identity.getEmail())
                .identitySource("WEB_AUTH")
                .build();
    }

    private AuthenticatedUser validateJwt(String token, OAuth2ProviderConfig config) {
        JwtDecoder decoder = getOrCreateDecoder(config.getJwkSetUri());
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            logger.warn("Bearer auth: JWT validation failed for domain '{}': {}",
                    config.getDomain(), e.getMessage());
            return null;
        }

        String email = getFirstClaim(jwt, "email", "preferred_username", "upn");
        String name = getFirstClaim(jwt, "name", "given_name");
        String sub = jwt.getClaimAsString("sub");

        if (email == null) {
            logger.warn("Bearer auth: JWT missing email claim for domain '{}'", config.getDomain());
            return null;
        }

        String username = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;

        logger.info("Bearer auth: JWT validated for user '{}' via {} (domain={})",
                email, config.getProviderType(), config.getDomain());

        return AuthenticatedUser.builder()
                .username(username)
                .email(email)
                .fullName(name)
                .externalId(sub)
                .managedAppleId(email)
                .identitySource("FEDERATED_IDP")
                .build();
    }

    public void invalidateCache(String jwkSetUri) {
        jwtDecoderCache.remove(jwkSetUri);
    }

    private JwtDecoder getOrCreateDecoder(String jwkSetUri) {
        return jwtDecoderCache.computeIfAbsent(jwkSetUri,
                uri -> NimbusJwtDecoder.withJwkSetUri(uri).build());
    }

    private String getFirstClaim(Jwt jwt, String... claimNames) {
        for (String name : claimNames) {
            String value = jwt.getClaimAsString(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
