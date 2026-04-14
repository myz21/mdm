package com.arcyintel.arcops.apple_mdm.services.vnc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Redis Pub/Sub relay for VNC binary frames across apple_mdm instances.
 *
 * <p>When viewer and agent WebSockets land on different instances, this relay
 * forwards binary RFB frames via Redis using Base64 encoding.
 *
 * <p>Channel convention:
 * <ul>
 *   <li>{@code vnc:relay:{sessionId}:viewer} — carries agent→viewer frames</li>
 *   <li>{@code vnc:relay:{sessionId}:agent}  — carries viewer→agent frames</li>
 * </ul>
 *
 * Each side subscribes to its OWN role channel to receive frames from the other side.
 */
@Component
public class VncBinaryRelay {

    private static final Logger logger = LoggerFactory.getLogger(VncBinaryRelay.class);
    private static final String CHANNEL_PREFIX = "vnc:relay:";

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ConcurrentHashMap<String, MessageListener> activeListeners = new ConcurrentHashMap<>();

    public VncBinaryRelay(StringRedisTemplate redisTemplate,
                          RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
    }

    /**
     * Subscribe to receive binary frames targeted at the given role.
     * Viewer subscribes to {@code vnc:relay:{sessionId}:viewer} to receive agent→viewer frames.
     * Agent subscribes to {@code vnc:relay:{sessionId}:agent} to receive viewer→agent frames.
     */
    public void subscribe(String sessionId, String role, Consumer<ByteBuffer> handler) {
        String channel = CHANNEL_PREFIX + sessionId + ":" + role;
        String key = sessionId + ":" + role;

        MessageListener listener = (message, pattern) -> {
            try {
                byte[] decoded = Base64.getDecoder().decode(message.getBody());
                handler.accept(ByteBuffer.wrap(decoded));
            } catch (Exception e) {
                logger.error("VNC relay decode error — sessionId={}, role={}: {}",
                        sessionId, role, e.getMessage());
            }
        };

        activeListeners.put(key, listener);
        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
        logger.debug("VNC binary relay subscribed — sessionId={}, role={}", sessionId, role);
    }

    /**
     * Publish a binary frame to the target role's channel.
     * When viewer sends and agent is remote, publish to {@code vnc:relay:{sessionId}:agent}.
     * When agent sends and viewer is remote, publish to {@code vnc:relay:{sessionId}:viewer}.
     */
    public void publish(String sessionId, String targetRole, ByteBuffer data) {
        String channel = CHANNEL_PREFIX + sessionId + ":" + targetRole;
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        redisTemplate.convertAndSend(channel, encoded);
    }

    /**
     * Unsubscribe from a role's channel (called on WebSocket close).
     */
    public void unsubscribe(String sessionId, String role) {
        String key = sessionId + ":" + role;
        MessageListener listener = activeListeners.remove(key);
        if (listener != null) {
            String channel = CHANNEL_PREFIX + sessionId + ":" + role;
            listenerContainer.removeMessageListener(listener, new ChannelTopic(channel));
            logger.debug("VNC binary relay unsubscribed — sessionId={}, role={}", sessionId, role);
        }
    }
}
