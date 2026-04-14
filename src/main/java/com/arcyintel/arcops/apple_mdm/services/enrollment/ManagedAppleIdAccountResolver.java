package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;
import com.arcyintel.arcops.apple_mdm.repositories.AppleAccountRepository;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AccountResolutionContext;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AccountResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves accounts using Managed Apple ID.
 *
 * This resolver looks up accounts by their managedAppleId field.
 * Used for Account-Driven User Enrollment (BYOD) where users
 * sign in with their Managed Apple ID from Apple Business Manager.
 */
@Component
@RequiredArgsConstructor
public class ManagedAppleIdAccountResolver implements AccountResolver {

    private static final Logger logger = LoggerFactory.getLogger(ManagedAppleIdAccountResolver.class);
    private static final String RESOLVER_TYPE = "MANAGED_APPLE_ID";

    private final AppleAccountRepository accountRepository;

    @Override
    public String getType() {
        return RESOLVER_TYPE;
    }

    @Override
    public Optional<AppleAccount> resolve(String managedAppleId) {
        if (managedAppleId == null || managedAppleId.isBlank()) {
            logger.warn("Cannot resolve account: Managed Apple ID is null or blank");
            return Optional.empty();
        }

        logger.debug("Attempting to resolve account by Managed Apple ID: {}", managedAppleId);

        Optional<AppleAccount> account = accountRepository.findByManagedAppleId(managedAppleId);

        if (account.isPresent()) {
            logger.info("Account resolved for Managed Apple ID '{}': username='{}'",
                    managedAppleId, account.get().getUsername());
        } else {
            logger.warn("No account found for Managed Apple ID: {}", managedAppleId);
        }

        return account;
    }

    @Override
    public Optional<AppleAccount> resolve(String identifier, AccountResolutionContext context) {
        // First try the direct identifier
        Optional<AppleAccount> result = resolve(identifier);
        if (result.isPresent()) {
            return result;
        }

        // If context has managedAppleId, try that
        if (context != null && context.getManagedAppleId() != null) {
            result = resolve(context.getManagedAppleId());
            if (result.isPresent()) {
                return result;
            }
        }

        // If context has email, try to find by email as fallback
        if (context != null && context.getEmail() != null) {
            result = accountRepository.findByEmail(context.getEmail());
            if (result.isPresent()) {
                logger.info("Account resolved by email fallback: {}", context.getEmail());
                return result;
            }
        }

        // Auto-create if enabled
        if (context != null && context.isAutoCreate()) {
            return createOrUpdate(context);
        }

        return Optional.empty();
    }

    @Override
    public boolean supportsAutoCreation() {
        return true;
    }

    @Override
    public Optional<AppleAccount> createOrUpdate(AccountResolutionContext context) {
        if (context == null) {
            return Optional.empty();
        }

        String managedAppleId = context.getManagedAppleId() != null
                ? context.getManagedAppleId()
                : context.getIdentifier();

        if (managedAppleId == null || managedAppleId.isBlank()) {
            logger.warn("Cannot create account: no Managed Apple ID provided");
            return Optional.empty();
        }

        // Check if account already exists
        Optional<AppleAccount> existing = accountRepository.findByManagedAppleId(managedAppleId);
        if (existing.isPresent()) {
            // Update existing account if we have new information
            AppleAccount account = existing.get();
            boolean updated = false;

            if (context.getFullName() != null && !context.getFullName().equals(account.getFullName())) {
                account.setFullName(context.getFullName());
                updated = true;
            }
            if (context.getEmail() != null && !context.getEmail().equals(account.getEmail())) {
                account.setEmail(context.getEmail());
                updated = true;
            }

            if (updated) {
                accountRepository.save(account);
                logger.info("Updated existing account for Managed Apple ID: {}", managedAppleId);
            }

            return Optional.of(account);
        }

        // Create new account
        String username = context.getShortName() != null
                ? context.getShortName()
                : extractUsernameFromManagedAppleId(managedAppleId);

        AppleAccount newAccount = AppleAccount.builder()
                .managedAppleId(managedAppleId)
                .username(username)
                .email(context.getEmail())
                .fullName(context.getFullName())
                .status("ACTIVE")
                .build();

        accountRepository.save(newAccount);
        logger.info("Created new account for Managed Apple ID: {} (username: {})", managedAppleId, username);

        return Optional.of(newAccount);
    }

    /**
     * Extracts username from Managed Apple ID.
     * Managed Apple IDs are typically in format: username@appleid.company.com
     */
    private String extractUsernameFromManagedAppleId(String managedAppleId) {
        if (managedAppleId == null) {
            return "unknown";
        }
        int atIndex = managedAppleId.indexOf('@');
        if (atIndex > 0) {
            return managedAppleId.substring(0, atIndex);
        }
        return managedAppleId;
    }
}
