package com.arcyintel.arcops.apple_mdm.configs.audit;

import jakarta.annotation.Nonnull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Extracts the current username from JWT "username" claim for @CreatedBy / @LastModifiedBy.
 */
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    @Nonnull
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of("system");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("username");
            if (username != null && !username.isBlank()) {
                return Optional.of(username);
            }
        }

        String name = authentication.getName();
        if (name != null && !name.isBlank() && !name.equals("anonymousUser")) {
            return Optional.of(name);
        }

        return Optional.of("system");
    }
}
