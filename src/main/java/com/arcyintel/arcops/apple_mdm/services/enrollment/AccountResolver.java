package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;

import java.util.Optional;

/**
 * Strategy interface for resolving Apple accounts during enrollment.
 *
 * This allows different authentication methods to be used:
 * - Managed Apple ID (direct lookup)
 * - Federated authentication (SAML/OAuth with IdP)
 * - Username/password (web auth flow)
 */
public interface AccountResolver {

    /**
     * Returns the resolver type identifier.
     * Used for selecting the appropriate resolver based on enrollment context.
     */
    String getType();

    /**
     * Attempts to resolve an account using the provided identifier.
     *
     * @param identifier The identifier to use for resolution (e.g., Managed Apple ID, email, username)
     * @return The resolved AppleAccount if found, empty otherwise
     */
    Optional<AppleAccount> resolve(String identifier);

    /**
     * Attempts to resolve an account with additional context.
     *
     * @param identifier The primary identifier
     * @param context Additional context for resolution (e.g., IdP claims, enrollment metadata)
     * @return The resolved AppleAccount if found, empty otherwise
     */
    default Optional<AppleAccount> resolve(String identifier, AccountResolutionContext context) {
        return resolve(identifier);
    }

    /**
     * Whether this resolver can create accounts on-the-fly during enrollment.
     * Some resolvers (like federated auth) may auto-create accounts.
     */
    default boolean supportsAutoCreation() {
        return false;
    }

    /**
     * Creates or updates an account based on the provided context.
     * Only called if supportsAutoCreation() returns true.
     *
     * @param context The resolution context with user information
     * @return The created or updated account
     */
    default Optional<AppleAccount> createOrUpdate(AccountResolutionContext context) {
        return Optional.empty();
    }
}
