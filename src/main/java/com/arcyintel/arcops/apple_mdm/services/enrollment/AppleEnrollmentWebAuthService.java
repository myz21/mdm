package com.arcyintel.arcops.apple_mdm.services.enrollment;

import java.util.List;
import java.util.UUID;

public interface AppleEnrollmentWebAuthService {

    /**
     * Authenticates the user by username/password against AppleIdentity
     * and creates a temporary enrollment token in Redis.
     */
    String authenticateAndCreateEnrollmentToken(String username, String password);

    /**
     * Generates the enrollment profile with the given enrollment token embedded as org_magic.
     */
    String generateEnrollProfileWithToken(String enrollmentToken) throws Exception;

    /**
     * Resolves the enrollment token to an identity ID and removes the token from Redis.
     */
    UUID resolveEnrollmentToken(String enrollmentToken);

    /**
     * Returns whether any identity providers (Google/Azure) are configured.
     * Used to decide if OTP login option should be shown.
     */
    boolean hasIdentityProviders();

    /**
     * Returns the list of connected provider type names (e.g. ["GOOGLE_WORKSPACE", "AZURE_ENTRA"]).
     */
    List<String> getConnectedProviderTypes();

    /**
     * Sends a 6-digit OTP code to the given email if it matches an active identity.
     * OTP is stored in Redis with a 5-minute TTL.
     *
     * @return true if OTP was sent, false if no matching identity found
     */
    boolean sendOtp(String email);

    /**
     * Verifies the OTP for the given email and creates an enrollment token if valid.
     *
     * @return enrollment token if OTP is valid, null otherwise
     */
    String verifyOtpAndCreateEnrollmentToken(String email, String otp);

    /**
     * Creates an enrollment token for a given identity ID.
     * Used by Account-Driven Enrollment to embed orgMagic in the CheckInURL.
     */
    String createEnrollmentTokenForIdentity(UUID identityId);
}
