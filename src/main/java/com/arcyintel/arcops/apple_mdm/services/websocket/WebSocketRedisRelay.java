package com.arcyintel.arcops.apple_mdm.services.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Redis Pub/Sub relay for WebSocket messages across apple_mdm instances.
 *
 * When a device MQTT response arrives at Instance A but the browser WebSocket
 * lives on Instance B, this relay publishes the message to a Redis channel.
 * Instance B's subscription receives it and forwards to the browser.
 */
@Component
public class WebSocketRedisRelay {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketRedisRelay.class);
    private static final String CHANNEL_PREFIX = "ws:session:";

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    /** Track active subscriptions so we can unsubscribe on session close */
    private final ConcurrentHashMap<String, MessageListener> activeListeners = new ConcurrentHashMap<>();

    public WebSocketRedisRelay(StringRedisTemplate redisTemplate,
                               RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
    }

    /**
     * Subscribe to a session's Redis channel. Messages published by other instances
     * will be delivered to the provided handler.
     */
    public void subscribeSession(String sessionId, Consumer<String> messageHandler) {
        MessageListener listener = (message, pattern) -> {
            try {
                messageHandler.accept(new String(message.getBody()));
            } catch (Exception e) {
                logger.error("Failed to handle relayed message for session {}: {}", sessionId, e.getMessage());
            }
        };

        activeListeners.put(sessionId, listener);
        listenerContainer.addMessageListener(listener, new ChannelTopic(CHANNEL_PREFIX + sessionId));
        logger.debug("Subscribed to Redis relay channel — sessionId={}", sessionId);
    }

    /**
     * Unsubscribe from a session's Redis channel (called on WebSocket close).
     */
    public void unsubscribeSession(String sessionId) {
        MessageListener listener = activeListeners.remove(sessionId);
        if (listener != null) {
            listenerContainer.removeMessageListener(listener, new ChannelTopic(CHANNEL_PREFIX + sessionId));
            logger.debug("Unsubscribed from Redis relay channel — sessionId={}", sessionId);
        }
    }

    /**
     * Publish a message to a session's Redis channel.
     * Called when the local instance receives a device MQTT response but
     * does not hold the browser WebSocket for that session.
     */
    public void publishToSession(String sessionId, String message) {
        redisTemplate.convertAndSend(CHANNEL_PREFIX + sessionId, message);
        logger.debug("Published relay message — sessionId={}", sessionId);
    }
}
