package com.arcyintel.arcops.apple_mdm.services.enrollment;

import org.springframework.http.ResponseEntity;

/**
 * Handles the Account-Driven Enrollment authentication flow for BYOD and ADDE.
 *
 * When a device POSTs without credentials, returns an auth challenge (401).
 * When a device POSTs with credentials, validates, resolves the account,
 * and returns the enrollment profile.
 */
public interface AccountDrivenEnrollmentService {

    /**
     * Process an account-driven enrollment request.
     *
     * @param authorizationHeader The Authorization header (null on first request)
     * @param enrollmentType      "byod" or "adde"
     * @param domain              User's email domain (e.g., "arcyintel.com") for OAuth2 config lookup
     * @return Auth challenge (401) or enrollment profile (200)
     */
    ResponseEntity<?> processEnrollment(String authorizationHeader, String enrollmentType, String domain);
}
