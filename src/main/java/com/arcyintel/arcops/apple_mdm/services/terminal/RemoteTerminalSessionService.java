package com.arcyintel.arcops.apple_mdm.services.terminal;

import com.arcyintel.arcops.apple_mdm.models.session.RemoteTerminalSession;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages remote terminal sessions in Redis.
 */
@Service
@RequiredArgsConstructor
public class RemoteTerminalSessionService {

    private static final Logger logger = LoggerFactory.getLogger(RemoteTerminalSessionService.class);
    private static final String KEY_PREFIX = "terminal:session:";
    private static final String DEVICE_SESSION_PREFIX = "terminal:device:";
    private static final long INITIAL_TTL_MINUTES = 5;
    private static final long MAX_TTL_MINUTES = 60;

    private final RedisTemplate<String, Object> redisTemplate;

    public RemoteTerminalSession createSession(String deviceUdid) {
        String sessionId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        RemoteTerminalSession session = new RemoteTerminalSession(
                sessionId, deviceUdid, RemoteTerminalSession.STATE_PENDING, now, now
        );
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, session, INITIAL_TTL_MINUTES, TimeUnit.MINUTES);
        // Reverse mapping: device → sessionId (for cross-instance MQTT routing)
        redisTemplate.opsForValue().set(DEVICE_SESSION_PREFIX + deviceUdid, sessionId, INITIAL_TTL_MINUTES, TimeUnit.MINUTES);
        logger.info("Terminal session created — sessionId={}, device={}", sessionId, deviceUdid);
        return session;
    }

    public RemoteTerminalSession getSession(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw instanceof RemoteTerminalSession s) {
            return s;
        }
        return null;
    }

    public void updateState(String sessionId, String newState) {
        RemoteTerminalSession session = getSession(sessionId);
        if (session == null) {
            logger.warn("Cannot update state — terminal session not found: {}", sessionId);
            return;
        }
        RemoteTerminalSession updated = session.withState(newState);
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, updated, MAX_TTL_MINUTES, TimeUnit.MINUTES);
        logger.info("Terminal session state updated — sessionId={}, state={}", sessionId, newState);
    }

    /**
     * Lookup sessionId by device UDID (cross-instance MQTT routing).
     */
    public String findSessionByDevice(String deviceUdid) {
        Object val = redisTemplate.opsForValue().get(DEVICE_SESSION_PREFIX + deviceUdid);
        return val instanceof String s ? s : null;
    }

    public void endSession(String sessionId) {
        RemoteTerminalSession session = getSession(sessionId);
        String key = KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        if (session != null) {
            redisTemplate.delete(DEVICE_SESSION_PREFIX + session.deviceUdid());
        }
        logger.info("Terminal session ended — sessionId={}", sessionId);
    }
}
