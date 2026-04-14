package com.arcyintel.arcops.apple_mdm.domains;

/**
 * Represents the type of enrollment profile to generate.
 *
 * Different profile types have different capabilities and are used in different scenarios.
 */
public enum EnrollmentProfileType {
    /**
     * Standard device enrollment profile.
     * Full MDM control, works with DEP or manual profile installation.
     * Device-level management with access to all device information.
     */
    DEVICE,

    /**
     * Account-Driven User Enrollment (BYOD).
     * Privacy-focused enrollment for personal devices.
     * Separate managed partition, limited MDM capabilities.
     * Uses EnrollmentID instead of UDID (privacy feature).
     * Requires ManagedAppleID key in profile.
     */
    USER_ENROLLMENT,

    /**
     * Account-Driven Device Enrollment (ADDE).
     * Company-owned devices enrolled via Managed Apple ID.
     * Full MDM control like DEP, but initiated by user with Managed Apple ID.
     * Useful for devices not purchased through Apple.
     */
    ACCOUNT_DRIVEN_DEVICE
}
