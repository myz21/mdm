package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.configs.mqtt.MqttProperties;
import com.arcyintel.arcops.apple_mdm.domains.*;
import com.arcyintel.arcops.apple_mdm.event.publisher.AccountEventPublisher;
import com.arcyintel.arcops.apple_mdm.models.api.agent.AgentAuthRequest;
import com.arcyintel.arcops.apple_mdm.models.api.agent.AgentAuthResponse;
import com.arcyintel.arcops.apple_mdm.repositories.AppleAccountRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleIdentityRepository;
import com.arcyintel.arcops.apple_mdm.repositories.DeviceAuthHistoryRepository;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentAuthService;
import com.arcyintel.arcops.commons.constants.apple.Os;
import com.arcyintel.arcops.commons.events.account.AccountCreatedEvent;
import com.arcyintel.arcops.commons.events.mail.SendEmailEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.arcyintel.arcops.commons.constants.events.MailGatewayEvents.MAIL_GATEWAY_EXCHANGE;
import static com.arcyintel.arcops.commons.constants.events.MailGatewayEvents.MAIL_SEND_ROUTING_KEY;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AgentAuthServiceImpl implements AgentAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AgentAuthServiceImpl.class);
    private static final String AGENT_TOKEN_PREFIX = "agent:session:";
    private static final String OTP_PREFIX = "agent:otp:";
    private static final long TOKEN_TTL_HOURS = 24;
    private static final long OTP_TTL_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppleIdentityRepository appleIdentityRepository;
    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleAccountRepository appleAccountRepository;
    private final DeviceAuthHistoryRepository deviceAuthHistoryRepository;
    private final AccountEventPublisher accountEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MqttProperties mqttProperties;
    private final RabbitTemplate rabbitTemplate;
    private final String emailFrom;
    private final String organizationName;

    public AgentAuthServiceImpl(
            AppleIdentityRepository appleIdentityRepository,
            AppleDeviceRepository appleDeviceRepository,
            AppleAccountRepository appleAccountRepository,
            DeviceAuthHistoryRepository deviceAuthHistoryRepository,
            AccountEventPublisher accountEventPublisher,
            PasswordEncoder passwordEncoder,
            RedisTemplate<String, Object> redisTemplate,
            MqttProperties mqttProperties,
            RabbitTemplate rabbitTemplate,
            @Value("${mdm.email.from:noreply@arcops.io}") String emailFrom,
            @Value("${mdm.organization-name:ArcOps}") String organizationName) {
        this.appleIdentityRepository = appleIdentityRepository;
        this.appleDeviceRepository = appleDeviceRepository;
        this.appleAccountRepository = appleAccountRepository;
        this.deviceAuthHistoryRepository = deviceAuthHistoryRepository;
        this.accountEventPublisher = accountEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.mqttProperties = mqttProperties;
        this.rabbitTemplate = rabbitTemplate;
        this.emailFrom = emailFrom;
        this.organizationName = organizationName;
    }

    @Override
    public AgentAuthResponse authenticate(AgentAuthRequest request, String clientIp) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required.");
        }

        String identifier = request.getUsername().trim();
        logger.info("Agent auth attempt for: {}", identifier);

        AppleIdentity identity;
        try {
            identity = findActiveIdentity(identifier);
        } catch (ResponseStatusException e) {
            // Determine specific failure reason
            String failureReason = e.getStatusCode() == HttpStatus.FORBIDDEN
                    ? "Account inactive" : "User not found";
            recordAuthHistory(null, "unknown", null, "AGENT", "SIGN_IN_FAILED",
                    clientIp, request.getAgentVersion(), failureReason);
            throw e;
        }

        if (identity.getPasswordHash() == null || identity.getPasswordHash().isBlank()) {
            logger.warn("Agent auth failed - no password set: {}", identifier);
            recordAuthHistory(null, "unknown", identity, "AGENT", "SIGN_IN_FAILED",
                    clientIp, request.getAgentVersion(), "Password not configured");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }

        if (!passwordEncoder.matches(request.getPassword(), identity.getPasswordHash())) {
            logger.warn("Agent auth failed - invalid password: {}", identifier);
            recordAuthHistory(null, "unknown", identity, "AGENT", "SIGN_IN_FAILED",
                    clientIp, request.getAgentVersion(), "Invalid password");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }

        return buildAuthResponse(identity, request.getDeviceSerialNumber(),
                request.getAgentVersion(), clientIp, "AGENT");
    }

    @Override
    public boolean sendOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }

        String trimmedEmail = email.trim().toLowerCase();
        Optional<AppleIdentity> identityOpt = appleIdentityRepository.findByEmail(trimmedEmail);
        if (identityOpt.isEmpty()) {
            logger.warn("OTP request for unknown email: {}", trimmedEmail);
            return false;
        }

        AppleIdentity identity = identityOpt.get();
        if (!"ACTIVE".equals(identity.getStatus())) {
            logger.warn("OTP request for inactive identity: {}", trimmedEmail);
            return false;
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String redisKey = OTP_PREFIX + trimmedEmail;
        redisTemplate.opsForValue().set(redisKey, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);

        try {
            sendOtpEmail(trimmedEmail, otp, identity.getFullName());
        } catch (Exception e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
        logger.info("OTP sent to: {}", trimmedEmail);
        return true;
    }

    @Override
    public AgentAuthResponse authenticateWithOtp(String email, String otp,
                                                  String deviceSerialNumber, String agentVersion,
                                                  String clientIp) {
        if (email == null || otp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and OTP are required.");
        }

        String trimmedEmail = email.trim().toLowerCase();
        String redisKey = OTP_PREFIX + trimmedEmail;
        Object storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null) {
            logger.warn("OTP verification failed for: {}", trimmedEmail);
            recordAuthHistory(null, "unknown", null, "AGENT_OTP", "SIGN_IN_FAILED",
                    clientIp, agentVersion, "OTP expired or not requested");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code.");
        }

        if (!otp.equals(storedOtp.toString())) {
            logger.warn("OTP verification failed for: {}", trimmedEmail);
            recordAuthHistory(null, "unknown", null, "AGENT_OTP", "SIGN_IN_FAILED",
                    clientIp, agentVersion, "Invalid OTP code");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code.");
        }

        redisTemplate.delete(redisKey);

        AppleIdentity identity = findActiveIdentity(trimmedEmail);

        return buildAuthResponse(identity, deviceSerialNumber, agentVersion, clientIp, "AGENT_OTP");
    }

    private AppleIdentity findActiveIdentity(String identifier) {
        Optional<AppleIdentity> identityOpt = appleIdentityRepository.findByUsername(identifier);
        if (identityOpt.isEmpty()) {
            identityOpt = appleIdentityRepository.findByEmail(identifier);
        }

        if (identityOpt.isEmpty()) {
            logger.warn("Agent auth failed - identity not found: {}", identifier);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        }

        AppleIdentity identity = identityOpt.get();
        if (!"ACTIVE".equals(identity.getStatus())) {
            logger.warn("Agent auth failed - identity not active: {}", identifier);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your account has been disabled. Contact your administrator.");
        }

        return identity;
    }

    private AgentAuthResponse buildAuthResponse(AppleIdentity identity, String serialNumber,
                                                 String agentVersion, String clientIp, String authSource) {
        logger.info("Agent auth successful for: {} (identity={}, source={})",
                identity.getEmail(), identity.getId(), authSource);

        AppleDevice resolvedDevice = null;
        String deviceUdid = null;
        if (serialNumber != null && !serialNumber.isBlank()) {
            Optional<AppleDevice> deviceOpt = appleDeviceRepository.findBySerialNumber(serialNumber.trim());
            if (deviceOpt.isPresent()) {
                resolvedDevice = deviceOpt.get();
                deviceUdid = resolvedDevice.getUdid();
                logger.info("Resolved device UDID by serial number: {} -> {}", serialNumber, deviceUdid);
            } else {
                logger.warn("No device found for serial number: {} - agent will use vendorId", serialNumber);
            }
        } else {
            logger.info("No device serial number provided - agent will use vendorId");
        }

        if (resolvedDevice != null) {
            bindDeviceToAccount(identity, resolvedDevice);
        }

        String deviceIdentifier = deviceUdid != null ? deviceUdid : (serialNumber != null ? serialNumber : "unknown");
        recordAuthHistory(resolvedDevice, deviceIdentifier, identity, authSource, "SIGN_IN",
                clientIp, agentVersion, null);

        // Store identityId:deviceIdentifier so logout can resolve the device
        String token = UUID.randomUUID().toString();
        String redisKey = AGENT_TOKEN_PREFIX + token;
        String redisValue = identity.getId().toString() + ":" + deviceIdentifier;
        redisTemplate.opsForValue().set(redisKey, redisValue, TOKEN_TTL_HOURS, TimeUnit.HOURS);

        return AgentAuthResponse.builder()
                .token(token)
                .deviceUdid(deviceUdid)
                .user(AgentAuthResponse.AgentUserDto.builder()
                        .id(identity.getId().toString())
                        .username(identity.getUsername())
                        .email(identity.getEmail())
                        .fullName(identity.getFullName())
                        .build())
                .mqtt(AgentAuthResponse.AgentMqttConfig.builder()
                        .host(mqttProperties.getAgentHost())
                        .port(mqttProperties.getAgentPort())
                        .username(mqttProperties.getUsername())
                        .password(mqttProperties.getPassword())
                        .clientId("agent-" + identity.getId().toString().substring(0, 8) + "-" + token.substring(0, 8))
                        .websocket(mqttProperties.isAgentWebSocket())
                        .path(mqttProperties.getAgentPath())
                        .ssl(mqttProperties.isAgentSsl())
                        .topicPrefix("arcops/" + mqttProperties.getPlatform() + "/devices/" + deviceIdentifier)
                        .build())
                .build();
    }

    private void sendOtpEmail(String email, String otp, String fullName) {
        try {
            String name = (fullName != null && !fullName.isBlank()) ? fullName : email;
            String html = """
                    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px 24px;">
                        <h2 style="margin: 0 0 8px; font-size: 20px; color: #1a1a1a;">Sign-In Verification</h2>
                        <p style="margin: 0 0 24px; color: #666; font-size: 14px;">Hi %s,</p>
                        <p style="margin: 0 0 16px; color: #333; font-size: 14px;">Your verification code for <strong>%s</strong> agent sign-in:</p>
                        <div style="background: #f5f5f5; border-radius: 12px; padding: 20px; text-align: center; margin: 0 0 24px;">
                            <span style="font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #1a1a1a;">%s</span>
                        </div>
                        <p style="margin: 0; color: #999; font-size: 12px;">This code expires in 5 minutes. If you didn't request this, ignore this email.</p>
                    </div>
                    """.formatted(name, organizationName, otp);

            SendEmailEvent event = SendEmailEvent.builder()
                    .to(email)
                    .subject("ArcOps Sign-In Verification Code")
                    .htmlBody(html)
                    .build();

            rabbitTemplate.convertAndSend(MAIL_GATEWAY_EXCHANGE, MAIL_SEND_ROUTING_KEY, event);
        } catch (Exception e) {
            logger.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send verification email.");
        }
    }

    @Override
    public void logout(String token, String clientIp) {
        String redisKey = AGENT_TOKEN_PREFIX + token;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);

        if (redisValue == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        // Parse identityId:deviceIdentifier from Redis value
        String valueStr = redisValue.toString();
        String identityIdStr;
        String deviceIdentifier;
        int colonIdx = valueStr.indexOf(':');
        if (colonIdx > 0) {
            identityIdStr = valueStr.substring(0, colonIdx);
            deviceIdentifier = valueStr.substring(colonIdx + 1);
        } else {
            // Backward compat: old tokens only stored identityId
            identityIdStr = valueStr;
            deviceIdentifier = "unknown";
        }

        UUID identityId = UUID.fromString(identityIdStr);
        redisTemplate.delete(redisKey);

        // Resolve device for the auth history record
        AppleDevice device = appleDeviceRepository.findByUdid(deviceIdentifier)
                .or(() -> appleDeviceRepository.findBySerialNumber(deviceIdentifier))
                .orElse(null);

        // Record sign-out auth history
        appleIdentityRepository.findById(identityId).ifPresent(identity ->
                recordAuthHistory(device, deviceIdentifier, identity, "AGENT", "SIGN_OUT",
                        clientIp, null, null));

        logger.info("Agent logout successful for identity={}, device={}", identityId, deviceIdentifier);
    }

    @Override
    public boolean validateToken(String token) {
        String redisKey = AGENT_TOKEN_PREFIX + token;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);
        if (redisValue != null) {
            // Sliding expiration: renew TTL on each successful validation
            redisTemplate.expire(redisKey, TOKEN_TTL_HOURS, TimeUnit.HOURS);
            return true;
        }
        return false;
    }

    private void bindDeviceToAccount(AppleIdentity identity, AppleDevice device) {
        try {
            Optional<AppleAccount> existingAccount = appleAccountRepository.findByIdentityId(identity.getId());

            AppleAccount account;
            if (existingAccount.isPresent()) {
                account = existingAccount.get();
            } else {
                account = AppleAccount.builder()
                        .username(identity.getUsername())
                        .email(identity.getEmail())
                        .fullName(identity.getFullName())
                        .identity(identity)
                        .build();
                account = appleAccountRepository.save(account);
                logger.info("New AppleAccount created (id={}) via agent login for identity '{}'.",
                        account.getId(), identity.getUsername());
            }

            if (account.getDevices().add(device)) {
                appleAccountRepository.save(account);
                logger.info("Device '{}' bound to account '{}' via agent login.", device.getUdid(), identity.getUsername());

                // Determine platform
                String platform = Os.IOS;
                if (device.getDeviceProperties() != null && device.getDeviceProperties().getModelName() != null) {
                    if (device.getDeviceProperties().getModelName().startsWith("Mac")) {
                        platform = Os.MACOS;
                    }
                }

                accountEventPublisher.publishAccountCreatedEvent(
                        AccountCreatedEvent.builder()
                                .accountId(account.getId())
                                .identityId(identity.getId())
                                .deviceId(device.getId())
                                .platform(platform)
                                .build()
                );
            }
        } catch (Exception e) {
            logger.error("Device-account binding failed for device '{}': {}", device.getUdid(), e.getMessage(), e);
        }
    }

    private void recordAuthHistory(AppleDevice device, String deviceIdentifier,
                                   AppleIdentity identity, String authSource, String eventType,
                                   String ipAddress, String agentVersion, String failureReason) {
        try {
            DeviceAuthHistory history = DeviceAuthHistory.builder()
                    .device(device)
                    .deviceIdentifier(deviceIdentifier)
                    .identity(identity)
                    .username(identity != null ? identity.getUsername() : null)
                    .authSource(authSource)
                    .eventType(eventType)
                    .ipAddress(ipAddress)
                    .agentVersion(agentVersion)
                    .failureReason(failureReason)
                    .build();
            deviceAuthHistoryRepository.save(history);
        } catch (Exception e) {
            logger.error("Failed to record auth history: {}", e.getMessage(), e);
        }
    }
}
