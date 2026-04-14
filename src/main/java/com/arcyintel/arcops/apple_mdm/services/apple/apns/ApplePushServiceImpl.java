package com.arcyintel.arcops.apple_mdm.services.apple.apns;

import com.eatthepath.pushy.apns.*;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.arcyintel.arcops.apple_mdm.services.apple.apns.ApplePushService;
import com.arcyintel.arcops.apple_mdm.services.apple.cert.ApplePushCredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Primary
public class ApplePushServiceImpl implements ApplePushService {
    private static final Logger logger = LoggerFactory.getLogger(ApplePushServiceImpl.class);
    private static final String P12_FILE_PATH = "certs/apple/mdm_customer_push.p12";
    private final String p12Password;
    private static final int MAX_RETRIES = 5;
    private static final long BASE_BACKOFF_MS = 250L;
    private static final long MAX_BACKOFF_MS = 5000L;
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);

    private final ApplePushCredentialService applePushCredentialService;
    private volatile ApnsClient apnsClient;

    public ApplePushServiceImpl(
            ApplePushCredentialService applePushCredentialService,
            @org.springframework.beans.factory.annotation.Value("${mdm.cert.p12-password:}") String p12Password
    ) throws Exception {
        this.applePushCredentialService = applePushCredentialService;
        this.p12Password = p12Password;
        initializeApnsClient();
        logger.info("APNs client initialized successfully.");
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            logger.warn("The provided byte array is null. Returning null.");
            return null;
        }
        logger.debug("Starting conversion of byte array to hexadecimal string. Byte array length: {}", bytes.length);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        logger.debug("Successfully converted byte array to hexadecimal string: {}", sb);
        return sb.toString();
    }

    @Override
    public void initializeApnsClient() throws Exception {
        try {
            logger.debug("Starting APNs client initialization...");
            this.apnsClient = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setClientCredentials(new File(P12_FILE_PATH), p12Password)
                    .build();
            logger.info("APNs client successfully initialized with the provided certificate.");
        } catch (Exception e) {
            if (e instanceof java.io.FileNotFoundException) {
                logger.warn("MDM push certificate file '{}' not found at path '{}'. APNs client will not be initialized.", P12_FILE_PATH, new File(P12_FILE_PATH).getAbsolutePath());
                this.apnsClient = null;
            } else {
                logger.error("An unexpected error occurred during APNs client initialization: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    @Override
    public void sendMdmWakeUp(String token, String pushMagic) {
        try {
            ensureApnsClient();

            // Build payload and sanitize token
            String payload = "{\"mdm\":\"" + pushMagic + "\"}";
            byte[] tokenBytes = Base64.getDecoder().decode(token);
            token = TokenUtil.sanitizeTokenString(bytesToHex(tokenBytes));

            String topic = applePushCredentialService.getPushTopic();
            if (topic == null || topic.isBlank()) {
                logger.error("Push topic extraction failed. Push notification cannot proceed.");
                return;
            }

            SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
                    token, topic, payload, Instant.now().plusSeconds(500),
                    DeliveryPriority.IMMEDIATE, PushType.MDM
            );

            sendWithBackoff(notification, 0);

        } catch (Exception e) {
            logger.error("Unexpected exception while sending MDM push. Error: {}", e.getMessage(), e);
        }
    }

    private void ensureApnsClient() throws Exception {
        if (apnsClient != null) return;
        synchronized (this) {
            if (apnsClient == null) {
                initializeApnsClient();
            }
        }
    }

    private void sendWithBackoff(SimpleApnsPushNotification notification, int attempt) {
        try {
            PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
                    apnsClient.sendNotification(notification);

            future.whenComplete((response, cause) -> {
                if (response != null) {
                    if (response.isAccepted()) {
                        logger.info("MDM push accepted by APNs. token={}", notification.getToken());
                    } else {
                        Optional<String> reason = response.getRejectionReason();
                        logger.warn("MDM push rejected. reason={}, token={}", reason, notification.getToken());
                        response.getTokenInvalidationTimestamp().ifPresent(t ->
                                logger.warn("Device token invalid as of: {}", t));

                        if (shouldRetry(reason.get()) && attempt < MAX_RETRIES) {
                            long delay = computeBackoff(attempt);
                            logger.info("Retrying push in {} ms (attempt {}/{})", delay, attempt + 1, MAX_RETRIES);
                            scheduleRetry(notification, attempt + 1, delay);
                        }
                    }
                } else {
                    logger.error("MDM push failed to send. cause={}", cause == null ? "unknown" : cause.getMessage(), cause);
                    if (attempt < MAX_RETRIES) {
                        long delay = computeBackoff(attempt);
                        logger.info("Retrying push in {} ms (attempt {}/{})", delay, attempt + 1, MAX_RETRIES);
                        scheduleRetry(notification, attempt + 1, delay);
                    }
                }
            });
        } catch (Exception ex) {
            logger.error("Error scheduling APNs push. {}", ex.getMessage(), ex);
            if (attempt < MAX_RETRIES) {
                long delay = computeBackoff(attempt);
                logger.info("Retrying push in {} ms (attempt {}/{})", delay, attempt + 1, MAX_RETRIES);
                scheduleRetry(notification, attempt + 1, delay);
            }
        }
    }

    private boolean shouldRetry(String rejectionReason) {
        if (rejectionReason == null) return true;
        String r = rejectionReason.trim().toLowerCase();
        // Retry on transient/server-side reasons
        return r.contains("too many requests") ||
                r.contains("service unavailable") ||
                r.contains("internalservererror") ||
                r.contains("shutdown") ||
                r.contains("idle timeout");
    }

    private long computeBackoff(int attempt) {
        long backoff = (long) (BASE_BACKOFF_MS * Math.pow(2, attempt));
        // add jitter (10%)
        long jitter = Math.min(100L, (long) (backoff * 0.1));
        return Math.min(backoff + jitter, MAX_BACKOFF_MS);
    }

    private void scheduleRetry(SimpleApnsPushNotification notification, int nextAttempt, long delayMs) {
        retryExecutor.schedule(
                () -> sendWithBackoff(notification, nextAttempt),
                delayMs, TimeUnit.MILLISECONDS);
    }
}
