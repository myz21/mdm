package com.arcyintel.arcops.apple_mdm.services.screenshare;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Stores a pending screen-share session for an offline device being woken up.
 * When the device reconnects (detected by AgentPresenceService), the pending
 * session is retrieved and the start_screen_share command is sent.
 */
@Service
@RequiredArgsConstructor
public class PendingScreenShareService {

    private static final Logger logger = LoggerFactory.getLogger(PendingScreenShareService.class);
    private static final String KEY_PREFIX = "screenshare:pending:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void setPending(String udid, String sessionId, String captureType) {
        try {
            String key = KEY_PREFIX + udid;
            Map<String, String> data = Map.of("sessionId", sessionId, "captureType", captureType);
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), TTL);
            logger.info("Pending screen share set for device {} — sessionId={}", udid, sessionId);
        } catch (Exception e) {
            logger.error("Failed to set pending screen share for {}: {}", udid, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, String>> drainPending(String udid) {
        try {
            String key = KEY_PREFIX + udid;
            String json = stringRedisTemplate.opsForValue().getAndDelete(key);
            if (json != null) {
                Map<String, String> data = objectMapper.readValue(json, Map.class);
                return Optional.of(data);
            }
        } catch (Exception e) {
            logger.error("Failed to drain pending screen share for {}: {}", udid, e.getMessage());
        }
        return Optional.empty();
    }
}
