package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;
import com.arcyintel.arcops.apple_mdm.domains.AppleIdentity;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentProfileType;
import com.arcyintel.arcops.apple_mdm.domains.OAuth2ProviderConfig;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.AuthenticatedUser;
import com.arcyintel.arcops.apple_mdm.repositories.AppleIdentityRepository;
import com.arcyintel.arcops.apple_mdm.services.enrollment.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDrivenEnrollmentServiceImpl implements AccountDrivenEnrollmentService {

    private static final Logger logger = LoggerFactory.getLogger(AccountDrivenEnrollmentServiceImpl.class);

    private final List<AccountDrivenAuthStrategy> authStrategies;
    private final OAuth2ProviderConfigCacheService providerConfigCacheService;
    private final AccountResolverRegistry accountResolverRegistry;
    private final EnrollmentProfileGenerator enrollmentProfileGenerator;
    private final AppleIdentityRepository appleIdentityRepository;
    private final AppleEnrollmentWebAuthService webAuthService;

    @Override
    public ResponseEntity<?> processEnrollment(String authorizationHeader, String enrollmentType, String domain) {

        // 1. Look up OAuth2 config for the domain (from Redis cache / back_core REST API)
        OAuth2ProviderConfig providerConfig = null;
        if (domain != null && !domain.isBlank()) {
            providerConfig = providerConfigCacheService.getByDomain(domain);
        }

        // 2. No Authorization header → return auth challenge
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return buildChallenge(providerConfig);
        }

        // 3. Find the strategy that supports this Authorization header type
        AccountDrivenAuthStrategy strategy = authStrategies.stream()
                .filter(s -> s.supports(authorizationHeader))
                .findFirst()
                .orElse(null);

        if (strategy == null) {
            logger.warn("Account-driven enrollment: unsupported Authorization scheme");
            return buildChallenge(providerConfig);
        }

        // 4. Authenticate
        AuthenticatedUser user = strategy.authenticate(authorizationHeader, providerConfig);
        if (user == null) {
            logger.warn("Account-driven enrollment: authentication failed for type={}, domain={}",
                    enrollmentType, domain);
            return buildChallenge(providerConfig);
        }

        // 5. Resolve or create AppleAccount
        resolveAccount(user);

        // 6. Generate enrollment profile
        String managedAppleId = user.getManagedAppleId() != null ? user.getManagedAppleId() : user.getEmail();
        if (managedAppleId == null || managedAppleId.isBlank()) {
            managedAppleId = user.getUsername();
        }

        try {
            EnrollmentProfileType profileType = "adde".equalsIgnoreCase(enrollmentType)
                    ? EnrollmentProfileType.ACCOUNT_DRIVEN_DEVICE
                    : EnrollmentProfileType.USER_ENROLLMENT;

            logger.info("Account-driven enrollment: generating {} profile for user '{}', domain='{}'",
                    profileType, user.getUsername(), domain);

            // Create an enrollment token for the checkin (orgMagic in CheckInURL)
            // so that authenticate() can resolve the identity and link the account
            String orgMagic = null;
            if (user.getExternalId() != null && "WEB_AUTH".equals(user.getIdentitySource())) {
                try {
                    UUID identityId = UUID.fromString(user.getExternalId());
                    orgMagic = webAuthService.createEnrollmentTokenForIdentity(identityId);
                } catch (Exception e) {
                    logger.debug("Could not create orgMagic for checkin: {}", e.getMessage());
                }
            }

            String mobileconfig = enrollmentProfileGenerator.generateProfile(profileType, managedAppleId, orgMagic);
            byte[] fileContent = mobileconfig.getBytes(StandardCharsets.UTF_8);

            String filename = profileType == EnrollmentProfileType.ACCOUNT_DRIVEN_DEVICE
                    ? "adde-enrollment.mobileconfig"
                    : "user-enrollment.mobileconfig";

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
            headers.add(HttpHeaders.CONTENT_TYPE, "application/x-apple-aspen-config");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(new ByteArrayResource(fileContent));

        } catch (Exception e) {
            logger.error("Account-driven enrollment: failed to generate profile for user '{}'",
                    user.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<?> buildChallenge(OAuth2ProviderConfig providerConfig) {
        // Apple Account-Driven Enrollment requires Bearer auth (web sign-in flow).
        // HTTP Basic is NOT supported by Apple for this enrollment type.
        return authStrategies.stream()
                .filter(s -> s instanceof OAuth2AuthStrategy)
                .findFirst()
                .map(s -> s.buildChallengeResponse(providerConfig))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private void resolveAccount(AuthenticatedUser user) {
        AccountResolutionContext.IdentitySource identitySource =
                "FEDERATED_IDP".equals(user.getIdentitySource())
                        ? AccountResolutionContext.IdentitySource.FEDERATED_IDP
                        : AccountResolutionContext.IdentitySource.WEB_AUTH;

        AccountResolutionContext context = AccountResolutionContext.builder()
                .identifier(user.getManagedAppleId())
                .managedAppleId(user.getManagedAppleId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .shortName(user.getUsername())
                .identitySource(identitySource)
                .autoCreate(true)
                .build();

        Optional<AppleAccount> accountOpt = accountResolverRegistry.resolve(context);
        if (accountOpt.isEmpty()) {
            accountOpt = accountResolverRegistry.createOrUpdate(context);
        }

        // Link AppleIdentity to account if possible
        accountOpt.ifPresent(account -> linkIdentityToAccount(user, account));
    }

    private void linkIdentityToAccount(AuthenticatedUser user, AppleAccount account) {
        if (account.getIdentity() != null || user.getExternalId() == null) return;

        Optional<AppleIdentity> identityOpt = Optional.empty();

        if ("WEB_AUTH".equals(user.getIdentitySource())) {
            try {
                identityOpt = appleIdentityRepository.findById(UUID.fromString(user.getExternalId()));
            } catch (Exception e) {
                logger.debug("Could not parse identity UUID: {}", user.getExternalId());
            }
        } else if ("FEDERATED_IDP".equals(user.getIdentitySource())) {
            identityOpt = appleIdentityRepository.findByExternalId(user.getExternalId());
            if (identityOpt.isEmpty() && user.getEmail() != null) {
                identityOpt = appleIdentityRepository.findByEmail(user.getEmail());
            }
        }

        identityOpt.ifPresent(account::setIdentity);
    }
}
