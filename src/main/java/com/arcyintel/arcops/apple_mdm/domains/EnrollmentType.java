package com.arcyintel.arcops.apple_mdm.domains;

/**
 * Represents the type of device enrollment in Apple MDM.
 *
 * Different enrollment types have different capabilities and privacy implications.
 */
public enum EnrollmentType {
    /**
     * Device Enrollment Program (DEP) / Automated Device Enrollment (ADE).
     * Device was purchased through Apple or added via Apple Configurator.
     * Supervised, full MDM control, assigned via ABM/ASM.
     */
    DEP,

    /**
     * Account-Driven User Enrollment (BYOD).
     * User enrolled using their Managed Apple ID.
     * Limited MDM control, privacy-focused, separate managed partition.
     * Uses EnrollmentID instead of UDID for privacy.
     */
    USER_ENROLLMENT,

    /**
     * Profile-based enrollment.
     * User manually downloaded and installed the enrollment profile.
     * Device-level MDM control but not supervised.
     */
    PROFILE,

    /**
     * Unknown or legacy enrollment type.
     * Used for devices enrolled before tracking was added.
     */
    UNKNOWN
}
