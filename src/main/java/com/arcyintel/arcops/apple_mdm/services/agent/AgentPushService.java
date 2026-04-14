package com.arcyintel.arcops.apple_mdm.services.agent;

import com.eatthepath.pushy.apns.*;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends APNs push notifications to the iOS / macOS agent apps.
 * Uses an APNs Auth Key (.p8) which is separate from the MDM push certificate.
 * If the .p8 key file is not present, the service runs in disabled mode.
 *
 * A single .p8 Auth Key works for all apps under the same Apple Developer team,
 * but each app has a different bundle ID used as the APNs topic.
 */
@Service
public class AgentPushService {

    private static final Logger logger = LoggerFactory.getLogger(AgentPushService.class);
    private static final String IOS_BUNDLE_ID = "com.arcyintel.arcops";
    private static final String MACOS_BUNDLE_ID = "arcyintel.macos-agent";

    private final ObjectMapper objectMapper;

    public AgentPushService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Value("${apns.agent.key-path:certs/apple/AuthKey_U323WD6MV8.p8}")
    private String keyPath;

    @Value("${apns.agent.key-id:}")
    private String keyId;

    @Value("${apns.agent.team-id:}")
    private String teamId;

    @Value("${apns.agent.production:false}")
    private boolean production;

    private volatile ApnsClient apnsClient;
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        try {
            File keyFile = new File(keyPath);
            if (!keyFile.exists()) {
                logger.warn("APNs Auth Key not found at '{}'. Agent push will be unavailable.", keyPath);
                return;
            }
            if (keyId.isBlank() || teamId.isBlank()) {
                logger.warn("APNs agent key-id or team-id not configured. Agent push will be unavailable.");
                return;
            }
            String apnsHost = production
                    ? ApnsClientBuilder.PRODUCTION_APNS_HOST
                    : ApnsClientBuilder.DEVELOPMENT_APNS_HOST;
            this.apnsClient = new ApnsClientBuilder()
                    .setApnsServer(apnsHost)
                    .setSigningKey(ApnsSigningKey.loadFromPkcs8File(keyFile, teamId, keyId))
                    .build();
            this.enabled = true;
            logger.info("Agent APNs client initialized (keyId={}, teamId={}, server={}).", keyId, teamId, apnsHost);
        } catch (Exception e) {
            logger.error("Failed to initialize agent APNs client: {}", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Resolves the APNs bundle ID (topic) based on the agent platform.
     * Falls back to iOS bundle ID if platform is unknown.
     */
    private String resolveBundleId(String agentPlatform) {
        if ("macOS".equalsIgnoreCase(agentPlatform) || "macos".equals(agentPlatform)) {
            return MACOS_BUNDLE_ID;
        }
        return IOS_BUNDLE_ID;
    }

    /**
     * Sends a visible alert push notification via APNs.
     * Unlike silent push, this is displayed regardless of app state (even force-quit).
     *
     * @param agentPushToken APNs device token hex string
     * @param agentPlatform  "iOS" or "macOS" — used to determine the correct bundle ID
     * @param title          notification title
     * @param body           notification body
     * @param customData     additional payload fields
     */
    public void sendAlertNotification(String agentPushToken, String agentPlatform, String title, String body, Map<String, Object> customData) {
        if (!enabled || apnsClient == null) {
            logger.warn("Agent APNs client not available. Cannot send alert notification.");
            return;
        }
        if (agentPushToken == null || agentPushToken.isBlank()) {
            logger.warn("No agent push token provided. Cannot send alert notification.");
            return;
        }

        try {
            Map<String, Object> aps = new LinkedHashMap<>();
            aps.put("alert", Map.of("title", title, "body", body != null ? body : ""));
            aps.put("sound", "default");

            Map<String, Object> fullPayload = new LinkedHashMap<>();
            fullPayload.put("aps", aps);
            if (customData != null) {
                fullPayload.putAll(customData);
            }

            String payload = objectMapper.writeValueAsString(fullPayload);
            String token = TokenUtil.sanitizeTokenString(agentPushToken);
            String bundleId = resolveBundleId(agentPlatform);

            SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
                    token, bundleId, payload,
                    Instant.now().plusSeconds(3600),
                    DeliveryPriority.IMMEDIATE,
                    PushType.ALERT
            );

            apnsClient.sendNotification(notification).whenComplete((response, cause) -> {
                if (response != null && response.isAccepted()) {
                    logger.info("Alert push accepted for token={}..., bundle={}, title={}", token.substring(0, Math.min(8, token.length())), bundleId, title);
                } else {
                    String reason = response != null ? response.getRejectionReason().orElse("unknown") : (cause != null ? cause.getMessage() : "unknown");
                    logger.warn("Alert push failed (bundle={}): {}", bundleId, reason);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to build alert push payload: {}", e.getMessage());
        }
    }

    /**
     * Backward-compatible overload — defaults to iOS bundle ID.
     */
    public void sendAlertNotification(String agentPushToken, String title, String body, Map<String, Object> customData) {
        sendAlertNotification(agentPushToken, "iOS", title, body, customData);
    }

    /**
     * Sends a silent push notification (content-available:1) to wake the agent app in the background.
     *
     * @param agentPushToken APNs device token hex string
     * @param agentPlatform  "iOS" or "macOS"
     */
    public void sendSilentWakeUp(String agentPushToken, String agentPlatform) {
        if (!enabled || apnsClient == null) {
            logger.warn("Agent APNs client not available. Cannot send silent wake-up.");
            return;
        }
        if (agentPushToken == null || agentPushToken.isBlank()) {
            logger.warn("No agent push token provided. Cannot send silent wake-up.");
            return;
        }

        String payload = "{\"aps\":{\"content-available\":1}}";
        String token = TokenUtil.sanitizeTokenString(agentPushToken);
        String bundleId = resolveBundleId(agentPlatform);

        SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
                token, bundleId, payload,
                Instant.now().plusSeconds(3600),
                DeliveryPriority.CONSERVE_POWER,
                PushType.BACKGROUND
        );

        apnsClient.sendNotification(notification).whenComplete((response, cause) -> {
            if (response != null && response.isAccepted()) {
                logger.info("Silent wake-up push accepted for token={}..., bundle={}", token.substring(0, Math.min(8, token.length())), bundleId);
            } else {
                String reason = response != null ? response.getRejectionReason().orElse("unknown") : (cause != null ? cause.getMessage() : "unknown");
                logger.warn("Silent wake-up push failed (bundle={}): {}", bundleId, reason);
            }
        });
    }

    /**
     * Backward-compatible overload — defaults to iOS bundle ID.
     */
    public void sendSilentWakeUp(String agentPushToken) {
        sendSilentWakeUp(agentPushToken, "iOS");
    }
}
