package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.clients.BackCoreClient;
import com.arcyintel.arcops.apple_mdm.domains.AppleIdentity;
import com.arcyintel.arcops.apple_mdm.repositories.AppleIdentityRepository;
import com.arcyintel.arcops.apple_mdm.services.email.EnrollmentEmailService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleEnrollmentService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleEnrollmentWebAuthService;
import com.arcyintel.arcops.commons.events.mail.SendEmailEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.arcyintel.arcops.commons.constants.events.MailGatewayEvents.MAIL_GATEWAY_EXCHANGE;
import static com.arcyintel.arcops.commons.constants.events.MailGatewayEvents.MAIL_SEND_ROUTING_KEY;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AppleEnrollmentWebAuthServiceImpl implements AppleEnrollmentWebAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AppleEnrollmentWebAuthServiceImpl.class);
    private static final String ENROLLMENT_TOKEN_PREFIX = "enrollment:web-auth:";
    private static final String OTP_PREFIX = "enrollment:otp:";
    private static final long TOKEN_TTL_MINUTES = 10;
    private static final long OTP_TTL_MINUTES = 5;

    private final AppleIdentityRepository appleIdentityRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppleEnrollmentService enrollmentService;
    private final PasswordEncoder passwordEncoder;
    private final BackCoreClient backCoreClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${mdm.organization.name:ArcOps}")
    private String organizationName;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String authenticateAndCreateEnrollmentToken(String username, String password) {
        Optional<AppleIdentity> identityOpt = appleIdentityRepository.findByUsername(username);

        if (identityOpt.isEmpty()) {
            identityOpt = appleIdentityRepository.findByEmail(username);
        }

        if (identityOpt.isEmpty()) {
            logger.warn("Authentication failed - identity not found: {}", username);
            return null;
        }

        AppleIdentity identity = identityOpt.get();

        if (!"ACTIVE".equals(identity.getStatus())) {
            logger.warn("Authentication failed - identity is not active: {}", username);
            return null;
        }

        if (identity.getPasswordHash() == null || identity.getPasswordHash().isBlank()) {
            logger.warn("Authentication failed - identity has no password set: {}", username);
            return null;
        }

        if (!passwordEncoder.matches(password, identity.getPasswordHash())) {
            logger.warn("Authentication failed - invalid password for: {}", username);
            return null;
        }

        return createEnrollmentToken(identity);
    }

    @Override
    public String generateEnrollProfileWithToken(String enrollmentToken) throws Exception {
        return enrollmentService.generateEnrollProfile(enrollmentToken);
    }

    @Override
    public UUID resolveEnrollmentToken(String enrollmentToken) {
        if (enrollmentToken == null || enrollmentToken.isBlank()) {
            return null;
        }

        String redisKey = ENROLLMENT_TOKEN_PREFIX + enrollmentToken;
        Object identityIdObj = redisTemplate.opsForValue().get(redisKey);

        if (identityIdObj == null) {
            logger.warn("Enrollment token not found or expired: {}", enrollmentToken);
            return null;
        }

        redisTemplate.delete(redisKey);

        try {
            UUID identityId = UUID.fromString(identityIdObj.toString());
            logger.info("Enrollment token resolved to identity: {}", identityId);
            return identityId;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid identity ID in enrollment token: {}", identityIdObj);
            return null;
        }
    }

    @Override
    public boolean hasIdentityProviders() {
        try {
            return !backCoreClient.getAllProviderConfigs().isEmpty();
        } catch (Exception e) {
            logger.warn("Failed to check identity providers: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> getConnectedProviderTypes() {
        try {
            return backCoreClient.getAllProviderConfigs().stream()
                    .map(c -> c.getProviderType().name())
                    .toList();
        } catch (Exception e) {
            logger.warn("Failed to fetch provider types: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean sendOtp(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();

        // Find identity by email
        Optional<AppleIdentity> identityOpt = appleIdentityRepository.findByEmail(normalizedEmail);
        if (identityOpt.isEmpty()) {
            identityOpt = appleIdentityRepository.findByUsername(normalizedEmail);
        }

        if (identityOpt.isEmpty()) {
            logger.warn("OTP send failed - no identity found for email: {}", normalizedEmail);
            return false;
        }

        AppleIdentity identity = identityOpt.get();
        if (!"ACTIVE".equals(identity.getStatus())) {
            logger.warn("OTP send failed - identity is not active: {}", normalizedEmail);
            return false;
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        String redisKey = OTP_PREFIX + normalizedEmail;
        redisTemplate.opsForValue().set(redisKey, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);

        // Send email
        try {
            sendOtpEmail(normalizedEmail, otp, identity.getFullName());
            logger.info("OTP sent to: {}", normalizedEmail);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send OTP email to: {}", normalizedEmail, e);
            redisTemplate.delete(redisKey);
            return false;
        }
    }

    @Override
    public String verifyOtpAndCreateEnrollmentToken(String email, String otp) {
        if (email == null || otp == null || email.isBlank() || otp.isBlank()) {
            return null;
        }

        String normalizedEmail = email.trim().toLowerCase();
        String redisKey = OTP_PREFIX + normalizedEmail;
        Object storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null) {
            logger.warn("OTP expired or not found for: {}", normalizedEmail);
            return null;
        }

        if (!otp.trim().equals(storedOtp.toString())) {
            logger.warn("OTP mismatch for: {}", normalizedEmail);
            return null;
        }

        // OTP valid — delete it
        redisTemplate.delete(redisKey);

        // Find identity
        Optional<AppleIdentity> identityOpt = appleIdentityRepository.findByEmail(normalizedEmail);
        if (identityOpt.isEmpty()) {
            identityOpt = appleIdentityRepository.findByUsername(normalizedEmail);
        }

        if (identityOpt.isEmpty() || !"ACTIVE".equals(identityOpt.get().getStatus())) {
            return null;
        }

        return createEnrollmentToken(identityOpt.get());
    }

    @Override
    public String createEnrollmentTokenForIdentity(UUID identityId) {
        String token = UUID.randomUUID().toString();
        String redisKey = ENROLLMENT_TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(redisKey, identityId.toString(), TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        logger.info("Enrollment token created for identity ID: {}", identityId);
        return token;
    }

    private String createEnrollmentToken(AppleIdentity identity) {
        String token = UUID.randomUUID().toString();
        String redisKey = ENROLLMENT_TOKEN_PREFIX + token;

        redisTemplate.opsForValue().set(redisKey, identity.getId().toString(), TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        logger.info("Enrollment token created for identity: {} (token: {})", identity.getUsername(), token);

        return token;
    }

    private void sendOtpEmail(String toEmail, String otp, String userName) {
        String greeting = (userName != null && !userName.isBlank())
                ? "Hello " + userName + ","
                : "Hello,";

        String html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Helvetica Neue',sans-serif;background:#f5f5f7;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
                    <tr><td align="center">
                        <table width="420" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);">
                            <tr><td style="background:linear-gradient(135deg,#0071e3,#00c7be);padding:28px;text-align:center;">
                                <h1 style="margin:0;color:#fff;font-size:20px;font-weight:600;">%s</h1>
                                <p style="margin:6px 0 0;color:rgba(255,255,255,.85);font-size:13px;">Device Enrollment</p>
                            </td></tr>
                            <tr><td style="padding:32px;">
                                <p style="margin:0 0 16px;color:#1d1d1f;font-size:15px;">%s</p>
                                <p style="margin:0 0 24px;color:#86868b;font-size:14px;line-height:1.5;">
                                    Use the following verification code to complete your device enrollment.
                                    This code expires in 5 minutes.
                                </p>
                                <div style="background:#f5f5f7;border-radius:12px;padding:20px;text-align:center;margin:0 0 24px;">
                                    <span style="font-size:32px;font-weight:700;letter-spacing:8px;color:#1d1d1f;">%s</span>
                                </div>
                                <p style="margin:0;color:#86868b;font-size:12px;line-height:1.4;">
                                    If you didn't request this code, you can safely ignore this email.
                                </p>
                            </td></tr>
                        </table>
                    </td></tr>
                </table>
            </body>
            </html>
            """.formatted(organizationName, greeting, otp);

        SendEmailEvent event = SendEmailEvent.builder()
                .to(toEmail)
                .subject("Device Enrollment Verification Code — " + organizationName)
                .htmlBody(html)
                .build();

        rabbitTemplate.convertAndSend(MAIL_GATEWAY_EXCHANGE, MAIL_SEND_ROUTING_KEY, event);
    }
}
