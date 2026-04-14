package com.arcyintel.arcops.apple_mdm.services.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages pending notifications for offline devices using Redis.
 * Notifications are queued when a device is offline and flushed when it reconnects.
 */
@Service
@RequiredArgsConstructor
public class PendingNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PendingNotificationService.class);
    private static final String KEY_PREFIX = "agent:pending_notifications:";
    private static final long MAX_PENDING = 50;
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Queues a notification for later delivery when the device reconnects.
     */
    public void queueNotification(String udid, Map<String, Object> notification) {
        try {
            String key = KEY_PREFIX + udid;
            String json = objectMapper.writeValueAsString(notification);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_PENDING, -1);
            redisTemplate.expire(key, TTL);
            logger.info("Queued pending notification for offline device {}", udid);
        } catch (Exception e) {
            logger.error("Failed to queue notification for device {}: {}", udid, e.getMessage());
        }
    }

    /**
     * Drains all pending notifications for a device. Returns the list and removes them from Redis.
     */
    public List<Map<String, Object>> drainPendingNotifications(String udid) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String key = KEY_PREFIX + udid;
            List<String> items = redisTemplate.opsForList().range(key, 0, -1);
            if (items != null && !items.isEmpty()) {
                for (String json : items) {
                    result.add(objectMapper.readValue(json, new TypeReference<>() {}));
                }
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            logger.error("Failed to drain pending notifications for device {}: {}", udid, e.getMessage());
        }
        return result;
    }
}
