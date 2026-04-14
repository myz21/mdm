package com.arcyintel.arcops.apple_mdm.services.enrollment;

public interface AppleEnrollmentService {

    String generateEnrollProfile() throws Exception;

    /**
     * Generates enrollment profile with OrganizationMagic embedded in the MDM payload.
     * Used when a web-auth enrollment token cookie is present.
     */
    String generateEnrollProfile(String organizationMagic) throws Exception;
}