package com.arcyintel.arcops.apple_mdm.services.email;

/**
 * Service for sending enrollment emails to users.
 */
public interface EnrollmentEmailService {

    /**
     * Sends an enrollment invitation email to the user.
     *
     * @param toEmail The recipient's email address
     * @param userName Optional user name for personalization
     */
    void sendEnrollmentEmail(String toEmail, String userName);

    /**
     * Sends an enrollment invitation email with custom message.
     *
     * @param toEmail The recipient's email address
     * @param userName Optional user name for personalization
     * @param customMessage Optional custom message to include
     */
    void sendEnrollmentEmail(String toEmail, String userName, String customMessage);
}
