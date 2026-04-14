package com.arcyintel.arcops.apple_mdm.services.enrollment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Context information for account resolution during enrollment.
 *
 * Contains user information from various sources:
 * - Apple Managed Apple ID
 * - IdP claims (SAML/OAuth)
 * - Enrollment metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResolutionContext {

    /**
     * Primary identifier (Managed Apple ID, email, etc.)
     */
    private String identifier;

    /**
     * User's email address
     */
    private String email;

    /**
     * User's full name
     */
    private String fullName;

    /**
     * User's short name / username
     */
    private String shortName;

    /**
     * Managed Apple ID (if provided by Apple)
     */
    private String managedAppleId;

    /**
     * EnrollmentUserID from Apple (for User Enrollment)
     */
    private String enrollmentUserId;

    /**
     * The resolver type that should be used
     */
    private String resolverType;

    /**
     * Source of the identity information
     */
    private IdentitySource identitySource;

    /**
     * Additional claims or metadata (from IdP, enrollment profile, etc.)
     */
    @Builder.Default
    private Map<String, Object> claims = new HashMap<>();

    /**
     * Whether to create the account if not found
     */
    @Builder.Default
    private boolean autoCreate = false;

    /**
     * Identity source enumeration
     */
    public enum IdentitySource {
        MANAGED_APPLE_ID,    // From Apple Business/School Manager
        FEDERATED_IDP,       // From SAML/OAuth IdP (Azure AD, Google, Okta)
        WEB_AUTH,            // From web authentication form
        ENROLLMENT_TOKEN,    // From DEP enrollment token
        UNKNOWN
    }

    /**
     * Add a claim to the context
     */
    public void addClaim(String key, Object value) {
        if (claims == null) {
            claims = new HashMap<>();
        }
        claims.put(key, value);
    }

    /**
     * Get a claim value
     */
    @SuppressWarnings("unchecked")
    public <T> T getClaim(String key, Class<T> type) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}
