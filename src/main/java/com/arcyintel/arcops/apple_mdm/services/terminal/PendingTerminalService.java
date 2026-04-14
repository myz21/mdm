package com.arcyintel.arcops.apple_mdm.services.terminal;

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
 * Stores a pending terminal session for an offline device being woken up.
 * When the device reconnects (detected by AgentPresenceService), the pending
 * session is retrieved and the start_terminal command is sent.
 */
@Service
@RequiredArgsConstructor
public class PendingTerminalService {

    private static final Logger logger = LoggerFactory.getLogger(PendingTerminalService.class);
    private static final String KEY_PREFIX = "apple:terminal:pending:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void setPending(String udid, String sessionId) {
        try {
            String key = KEY_PREFIX + udid;
            Map<String, String> data = Map.of("sessionId", sessionId);
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), TTL);
            logger.info("Pending terminal set for device {} — sessionId={}", udid, sessionId);
        } catch (Exception e) {
            logger.error("Failed to set pending terminal for {}: {}", udid, e.getMessage());
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
            logger.error("Failed to drain pending terminal for {}: {}", udid, e.getMessage());
        }
        return Optional.empty();
    }
}
