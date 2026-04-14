package com.arcyintel.arcops.apple_mdm.services.screenshare;

import com.arcyintel.arcops.apple_mdm.models.session.ScreenShareSession;

import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.apple_mdm.services.websocket.WebSocketRedisRelay;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.*;

/**
 * WebSocket handler that bridges browser signaling messages to device MQTT commands
 * and routes device MQTT responses back to the browser WebSocket.
 *
 * <p>Browser connects to: /screen-share/ws?session={sessionId}
 */
@Component
@RequiredArgsConstructor
public class ScreenShareSignalingHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ScreenShareSignalingHandler.class);

    private final ScreenShareSessionService sessionService;
    private final AgentCommandService agentCommandService;
    private final ObjectMapper objectMapper;
    private final WebSocketRedisRelay webSocketRedisRelay;

    /** Send timeout per message (5 seconds) */
    private static final int SEND_TIME_LIMIT_MS = 5000;
    /** Max outbound buffer size per session (64 KB) */
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    /** sessionId → thread-safe WebSocketSession */
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** sessionId → deviceUdid (cached for fast lookup) */
    private final ConcurrentHashMap<String, String> sessionDeviceMap = new ConcurrentHashMap<>();

    /** Per-session bidirectional signaling buffer */
    private final ConcurrentHashMap<String, SignalingBuffer> signalingBuffers = new ConcurrentHashMap<>();

    /**
     * Holds buffered signaling messages for both directions until each side is ready.
     */
    static class SignalingBuffer {
        /** Browser → Device: offer/ICE commands buffered until agent sends screen_share_ready */
        final Queue<Runnable> toDevice = new ConcurrentLinkedQueue<>();
        /** Device → Browser: answer/ICE/ready messages buffered until WebSocket connects */
        final Queue<String> toBrowser = new ConcurrentLinkedQueue<>();
        /** Set to true when the agent sends screen_share_ready */
        volatile boolean deviceReady = false;
        /** Set to true when the browser WebSocket connects */
        volatile boolean browserConnected = false;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        String sessionId = extractSessionId(wsSession);
        if (sessionId == null) {
            wsSession.close(CloseStatus.BAD_DATA.withReason("Missing session parameter"));
            return;
        }

        ScreenShareSession session = sessionService.getSession(sessionId);
        if (session == null) {
            wsSession.close(CloseStatus.BAD_DATA.withReason("Invalid or expired session"));
            return;
        }

        // Wrap with ConcurrentWebSocketSessionDecorator for thread-safe sendMessage
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                wsSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

        sessionDeviceMap.put(sessionId, session.deviceUdid());

        // Register WebSocket and flush any device→browser messages that arrived before WS connected
        sessions.compute(sessionId, (key, existing) -> {
            SignalingBuffer buffer = signalingBuffers.computeIfAbsent(sessionId, k -> new SignalingBuffer());
            buffer.browserConnected = true;

            // Drain toBrowser queue under compute lock to prevent ordering races with handleDeviceResponse
            String buffered;
            while ((buffered = buffer.toBrowser.poll()) != null) {
                try {
                    safeSession.sendMessage(new TextMessage(buffered));
                    logger.info("Flushed buffered device→browser message — sessionId={}", sessionId);
                } catch (IOException e) {
                    logger.error("Failed to flush buffered message: {}", e.getMessage());
                }
            }

            return safeSession;
        });

        // Check if device already signaled ready (race: MQTT response may arrive before WS connects)
        // This uses persistent Redis hash state, not ephemeral Pub/Sub, so it's reliable.
        ScreenShareSession currentState = sessionService.getSession(sessionId);
        if (currentState != null && ScreenShareSession.STATE_DEVICE_READY.equals(currentState.state())) {
            SignalingBuffer buf = signalingBuffers.computeIfAbsent(sessionId, k -> new SignalingBuffer());
            if (!buf.deviceReady) {
                buf.deviceReady = true;
                Map<String, Object> readyMsg = Map.of("type", "screen_share_ready", "sessionId", sessionId);
                safeSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(readyMsg)));
                logger.info("Synthesized screen_share_ready from Redis state — sessionId={}", sessionId);

                Runnable cmd;
                while ((cmd = buf.toDevice.poll()) != null) {
                    cmd.run();
                    logger.info("Flushed buffered browser→device command after state check — sessionId={}", sessionId);
                }
            }
        }

        // Subscribe to Redis relay channel for cross-instance message delivery
        webSocketRedisRelay.subscribeSession(sessionId, message -> {
            try {
                // Check if this is a screen_share_ready message — flush buffered offers
                if (message.contains("screen_share_ready")) {
                    SignalingBuffer buf = signalingBuffers.get(sessionId);
                    if (buf != null && !buf.deviceReady) {
                        buf.deviceReady = true;
                        Runnable cmd;
                        while ((cmd = buf.toDevice.poll()) != null) {
                            cmd.run();
                            logger.info("Flushed buffered browser→device command via Redis relay — sessionId={}", sessionId);
                        }
                    }
                }

                WebSocketSession ws = sessions.get(sessionId);
                if (ws != null && ws.isOpen()) {
                    ws.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                logger.error("Failed to relay screen share message from Redis — sessionId={}: {}", sessionId, e.getMessage());
            }
        });

        logger.info("WebSocket connected — sessionId={}, device={}", sessionId, session.deviceUdid());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String sessionId = extractSessionId(wsSession);
        if (sessionId == null) return;

        String deviceUdid = sessionDeviceMap.get(sessionId);
        if (deviceUdid == null) {
            // Cross-instance: WebSocket on this instance but session created on another
            ScreenShareSession session = sessionService.getSession(sessionId);
            if (session != null) {
                deviceUdid = session.deviceUdid();
                sessionDeviceMap.put(sessionId, deviceUdid);
                logger.info("Resolved device from Redis for session: {}", sessionId);
            }
        }
        if (deviceUdid == null) {
            logger.warn("No device mapping for session: {}", sessionId);
            return;
        }
        final String resolvedDevice = deviceUdid;

        Map<String, Object> msg = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        String type = (String) msg.get("type");

        if (type == null) {
            logger.warn("WebSocket message without type — sessionId={}", sessionId);
            return;
        }

        SignalingBuffer buffer = signalingBuffers.computeIfAbsent(sessionId, k -> new SignalingBuffer());

        switch (type) {
            case "offer" -> {
                String sdp = (String) msg.get("sdp");
                Runnable sendOffer = () -> agentCommandService.sendCommand(resolvedDevice, CMD_WEBRTC_OFFER,
                        Map.of("sessionId", sessionId, "sdp", sdp));
                if (buffer.deviceReady) {
                    sendOffer.run();
                    logger.info("Forwarded WebRTC offer to device — sessionId={}", sessionId);
                } else {
                    buffer.toDevice.add(sendOffer);
                    logger.info("Buffered WebRTC offer (device not ready) — sessionId={}", sessionId);
                }
            }
            case "ice" -> {
                Object candidate = msg.get("candidate");
                Runnable sendIce = () -> agentCommandService.sendCommand(resolvedDevice, CMD_WEBRTC_ICE,
                        Map.of("sessionId", sessionId, "candidate", candidate));
                if (buffer.deviceReady) {
                    sendIce.run();
                    logger.debug("Forwarded ICE candidate to device — sessionId={}", sessionId);
                } else {
                    buffer.toDevice.add(sendIce);
                    logger.debug("Buffered ICE candidate (device not ready) — sessionId={}", sessionId);
                }
            }
            case "stop" -> {
                agentCommandService.sendCommand(resolvedDevice, CMD_STOP_SCREEN_SHARE,
                        Map.of("sessionId", sessionId));
                sessions.remove(sessionId);
                sessionDeviceMap.remove(sessionId);
                signalingBuffers.remove(sessionId);
                webSocketRedisRelay.unsubscribeSession(sessionId);
                sessionService.endSession(sessionId);
                logger.info("Stop screen share sent to device — sessionId={}, maps cleaned up", sessionId);
            }
            default -> logger.warn("Unknown WebSocket message type: {} — sessionId={}", type, sessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        String sessionId = extractSessionId(wsSession);
        if (sessionId != null) {
            sessions.remove(sessionId);
            signalingBuffers.remove(sessionId);
            String deviceUdid = sessionDeviceMap.remove(sessionId);

            // Notify the device agent to stop — the browser may have closed without
            // sending a "stop" message (e.g. tab closed, network loss, timeout).
            if (deviceUdid != null) {
                try {
                    agentCommandService.sendCommand(deviceUdid, CMD_STOP_SCREEN_SHARE,
                            Map.of("sessionId", sessionId));
                    logger.info("Sent stop command to device on WS close — sessionId={}, device={}", sessionId, deviceUdid);
                } catch (Exception e) {
                    logger.warn("Failed to send stop on WS close — sessionId={}: {}", sessionId, e.getMessage());
                }
            }

            webSocketRedisRelay.unsubscribeSession(sessionId);
            sessionService.endSession(sessionId);
            logger.info("WebSocket closed — sessionId={}, device={}, status={}",
                    sessionId, deviceUdid, status);
        }
    }

    /**
     * Called by MqttMessageRouter when a screen-share related response arrives from a device.
     */
    public void handleDeviceResponse(String deviceUdid, Map<String, Object> response) {
        try {
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                logger.warn("Device response missing data map — device={}", deviceUdid);
                return;
            }

            String type = (String) data.get("type");
            String sessionId = (String) data.get("sessionId");

            if (sessionId == null) {
                // Try local in-memory map first
                sessionId = sessionDeviceMap.entrySet().stream()
                        .filter(e -> e.getValue().equals(deviceUdid))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
            }

            if (sessionId == null) {
                // Cross-instance fallback: lookup from Redis
                sessionId = sessionService.findSessionByDevice(deviceUdid);
                if (sessionId != null) {
                    sessionDeviceMap.put(sessionId, deviceUdid);
                    logger.info("Resolved session from Redis — sessionId={}, device={}", sessionId, deviceUdid);
                }
            }

            if (sessionId == null) {
                logger.warn("Cannot route device response — no active session for device={}", deviceUdid);
                return;
            }

            SignalingBuffer buffer = signalingBuffers.computeIfAbsent(sessionId, k -> new SignalingBuffer());

            // When device signals ready, flush all buffered browser→device messages
            if ("screen_share_ready".equals(type)) {
                sessionService.updateState(sessionId, ScreenShareSession.STATE_DEVICE_READY);
                buffer.deviceReady = true;

                Runnable cmd;
                while ((cmd = buffer.toDevice.poll()) != null) {
                    cmd.run();
                    logger.info("Flushed buffered browser→device command — sessionId={}", sessionId);
                }
            }

            String json = objectMapper.writeValueAsString(data);

            WebSocketSession wsSession = sessions.get(sessionId);
            if (wsSession != null && wsSession.isOpen() && buffer.browserConnected) {
                wsSession.sendMessage(new TextMessage(json));
                logger.info("Forwarded device response to browser — sessionId={}, type={}", sessionId, type);
            } else if (buffer.browserConnected) {
                // Browser was connected but WS is gone — shouldn't happen normally
                buffer.toBrowser.add(json);
                logger.info("Buffered device→browser message — sessionId={}, type={}", sessionId, type);
            } else {
                // WebSocket not yet connected on this instance — buffer locally AND relay via Redis.
                // Buffer: in case the WS connects to THIS instance later (same-instance race).
                // Redis: in case the WS is on another instance (cross-instance case).
                buffer.toBrowser.add(json);
                webSocketRedisRelay.publishToSession(sessionId, json);
                logger.info("Buffered + relayed device response — sessionId={}, type={}", sessionId, type);
            }

        } catch (IOException e) {
            logger.error("Failed to forward device response to browser: {}", e.getMessage());
        }
    }

    private String extractSessionId(WebSocketSession wsSession) {
        URI uri = wsSession.getUri();
        if (uri == null) return null;
        return UriComponentsBuilder.fromUri(uri).build()
                .getQueryParams()
                .getFirst("session");
    }
}
