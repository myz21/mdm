package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentProfileType;

/**
 * Service interface for generating MDM enrollment profiles.
 *
 * Supports different enrollment types:
 * - Standard device enrollment
 * - Account-Driven User Enrollment (BYOD)
 * - Account-Driven Device Enrollment (ADDE)
 */
public interface EnrollmentProfileGenerator {

    /**
     * Generates a standard device enrollment profile.
     */
    String generateDeviceEnrollmentProfile() throws Exception;

    /**
     * Generates a User Enrollment profile for BYOD.
     *
     * @param managedAppleId The Managed Apple ID for the user
     * @return The generated mobileconfig XML
     */
    String generateUserEnrollmentProfile(String managedAppleId) throws Exception;

    /**
     * Generates an Account-Driven Device Enrollment profile.
     *
     * @param managedAppleId The Managed Apple ID for the user
     * @return The generated mobileconfig XML
     */
    String generateAccountDrivenDeviceEnrollmentProfile(String managedAppleId) throws Exception;

    /**
     * Generates an enrollment profile based on the specified type.
     *
     * @param type The enrollment profile type
     * @param managedAppleId The Managed Apple ID (required for USER_ENROLLMENT and ACCOUNT_DRIVEN_DEVICE)
     * @return The generated mobileconfig XML
     */
    String generateProfile(EnrollmentProfileType type, String managedAppleId) throws Exception;

    /**
     * Generates an enrollment profile with an orgMagic token embedded in CheckInURL.
     * Used for Account-Driven Enrollment so the device checkin can resolve the identity.
     */
    String generateProfile(EnrollmentProfileType type, String managedAppleId, String orgMagic) throws Exception;
}
