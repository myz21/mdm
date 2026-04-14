package com.arcyintel.arcops.apple_mdm.services.email;

import com.arcyintel.arcops.apple_mdm.services.email.EnrollmentEmailService;
import com.arcyintel.arcops.commons.events.mail.SendEmailEvent;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.arcyintel.arcops.commons.constants.events.MailGatewayEvents.MAIL_GATEWAY_EXCHANGE;
import static com.arcyintel.arcops.commons.constants.events.MailGatewayEvents.MAIL_SEND_ROUTING_KEY;

/**
 * Implementation of EnrollmentEmailService.
 * Sends MDM enrollment invitation emails to users via centralized mail gateway.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentEmailServiceImpl implements EnrollmentEmailService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentEmailServiceImpl.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${host}")
    private String apiHost;

    @Value("${mdm.organization.name:ArcOps}")
    private String organizationName;

    @Value("${mdm.email.enrollment-subject:Device Enrollment Required}")
    private String defaultSubject;

    @Override
    @Async
    public void sendEnrollmentEmail(String toEmail, String userName) {
        sendEnrollmentEmail(toEmail, userName, null);
    }

    @Override
    @Async
    public void sendEnrollmentEmail(String toEmail, String userName, String customMessage) {
        try {
            logger.info("Sending enrollment email to: {}", toEmail);

            String subject = defaultSubject + " - " + organizationName;
            String htmlContent = buildEmailContent(userName, customMessage);

            SendEmailEvent event = SendEmailEvent.builder()
                    .to(toEmail)
                    .subject(subject)
                    .htmlBody(htmlContent)
                    .build();

            rabbitTemplate.convertAndSend(MAIL_GATEWAY_EXCHANGE, MAIL_SEND_ROUTING_KEY, event);
            logger.info("Enrollment email queued for: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to queue enrollment email for: {}", toEmail, e);
            throw new BusinessException("EMAIL_ERROR", "Failed to send enrollment email", e);
        }
    }

    private String buildEmailContent(String userName, String customMessage) {
        String enrollmentUrl = apiHost + "/mdm/enrollment";
        String greeting = userName != null && !userName.isBlank()
                ? "Hello " + escapeHtml(userName) + ","
                : "Hello,";

        String customSection = customMessage != null && !customMessage.isBlank()
                ? "<p style=\"margin: 16px 0; color: #374151;\">" + escapeHtml(customMessage) + "</p>"
                : "";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f3f4f6;">
                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%%" style="background-color: #f3f4f6;">
                        <tr>
                            <td style="padding: 40px 20px;">
                                <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="600" style="margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
                                    <!-- Header -->
                                    <tr>
                                        <td style="background: linear-gradient(135deg, #6366f1 0%%, #8b5cf6 100%%); padding: 32px; text-align: center;">
                                            <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 600;">%s</h1>
                                            <p style="margin: 8px 0 0 0; color: rgba(255, 255, 255, 0.9); font-size: 14px;">Device Management</p>
                                        </td>
                                    </tr>

                                    <!-- Content -->
                                    <tr>
                                        <td style="padding: 32px;">
                                            <p style="margin: 0 0 16px 0; color: #1f2937; font-size: 16px;">%s</p>

                                            <p style="margin: 0 0 16px 0; color: #374151; font-size: 14px; line-height: 1.6;">
                                                Your organization requires you to enroll your Apple device in the Mobile Device Management (MDM) system.
                                                This allows IT to securely manage your device and provide access to company resources.
                                            </p>

                                            %s

                                            <!-- CTA Button -->
                                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin: 24px 0;">
                                                <tr>
                                                    <td style="border-radius: 8px; background: linear-gradient(135deg, #6366f1 0%%, #8b5cf6 100%%);">
                                                        <a href="%s" target="_blank" style="display: inline-block; padding: 14px 32px; color: #ffffff; text-decoration: none; font-size: 16px; font-weight: 600;">
                                                            Enroll My Device
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>

                                            <!-- Instructions -->
                                            <div style="background-color: #f9fafb; border-radius: 8px; padding: 20px; margin-top: 24px;">
                                                <h3 style="margin: 0 0 12px 0; color: #1f2937; font-size: 14px; font-weight: 600;">How to Enroll:</h3>
                                                <ol style="margin: 0; padding-left: 20px; color: #4b5563; font-size: 13px; line-height: 1.8;">
                                                    <li><strong>Open this email on your Apple device</strong> (iPhone, iPad, or Mac)</li>
                                                    <li><strong>Use Safari</strong> - Profile installation only works in Safari</li>
                                                    <li><strong>Tap "Enroll My Device"</strong> button above</li>
                                                    <li><strong>Allow the profile download</strong> when prompted</li>
                                                    <li><strong>Go to Settings</strong> → tap "Profile Downloaded" notification</li>
                                                    <li><strong>Install the profile</strong> and enter your passcode if asked</li>
                                                    <li><strong>Trust Remote Management</strong> to complete enrollment</li>
                                                </ol>
                                            </div>

                                            <!-- Warning -->
                                            <div style="background-color: #fef3c7; border-left: 4px solid #f59e0b; padding: 12px 16px; margin-top: 20px; border-radius: 0 8px 8px 0;">
                                                <p style="margin: 0; color: #92400e; font-size: 13px;">
                                                    <strong>Important:</strong> You must open this link in Safari. Other browsers will not work for MDM enrollment.
                                                </p>
                                            </div>
                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color: #f9fafb; padding: 24px 32px; border-top: 1px solid #e5e7eb;">
                                            <p style="margin: 0 0 8px 0; color: #6b7280; font-size: 12px; text-align: center;">
                                                If the button doesn't work, copy and paste this link into Safari:
                                            </p>
                                            <p style="margin: 0 0 16px 0; color: #6366f1; font-size: 12px; text-align: center; word-break: break-all;">
                                                <a href="%s" style="color: #6366f1;">%s</a>
                                            </p>
                                            <p style="margin: 0; color: #9ca3af; font-size: 11px; text-align: center;">
                                                This email was sent by %s IT Department.<br>
                                                If you didn't expect this email, please contact your IT administrator.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(
                organizationName,
                greeting,
                customSection,
                enrollmentUrl,
                enrollmentUrl,
                enrollmentUrl,
                organizationName
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
