package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.models.api.enrollment.GetEnrollmentStatusDto;

import java.util.List;

public interface EnrollmentStatusService {

    /**
     * Gets the current enrollment status with all certificate/token information.
     */
    GetEnrollmentStatusDto getEnrollmentStatus();

    /**
     * Updates the current step of the enrollment wizard.
     */
    GetEnrollmentStatusDto updateCurrentStep(int step);

    /**
     * Marks a step as completed.
     */
    GetEnrollmentStatusDto completeStep(int step);

    /**
     * Marks the entire enrollment as completed.
     */
    GetEnrollmentStatusDto markEnrollmentCompleted();

    /**
     * Refreshes all certificate and token information from disk files.
     * Call this after uploading new certificates/tokens.
     */
    GetEnrollmentStatusDto refreshCertificateInfo();

    /**
     * Reloads all cached credentials in services (APNS client, private keys, etc.)
     * Call this after renewing certificates to apply changes without restart.
     */
    void hotReloadCredentials() throws Exception;

    /**
     * Removes specified steps from the completedSteps list and resets enrollmentCompleted if needed.
     * Used when user explicitly re-initiates a step (e.g., regenerating CSR or re-downloading DEP cert).
     */
    GetEnrollmentStatusDto resetSteps(List<Integer> stepsToReset);

    /**
     * Marks that the vendor certificate has been renewed.
     * This sets pushCertRenewedAfterVendor to false, triggering a warning for the user.
     */
    void markVendorCertRenewed();

    /**
     * Marks that the push certificate has been renewed after a vendor cert renewal.
     * This clears the vendor renewal warning.
     */
    void markPushCertRenewed();
}
