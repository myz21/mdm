package com.arcyintel.arcops.apple_mdm.services.terminal;

import com.arcyintel.arcops.apple_mdm.models.session.RemoteTerminalSession;

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
 * WebSocket handler that bridges browser terminal I/O to device MQTT commands
 * and routes device terminal output back to the browser WebSocket.
 *
 * <p>Browser connects to: /remote-terminal/ws?session={sessionId}
 */
@Component
@RequiredArgsConstructor
public class RemoteTerminalHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RemoteTerminalHandler.class);

    private final RemoteTerminalSessionService sessionService;
    private final AgentCommandService agentCommandService;
    private final ObjectMapper objectMapper;
    private final WebSocketRedisRelay webSocketRedisRelay;

    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int BUFFER_SIZE_LIMIT = 128 * 1024;

    /** sessionId -> thread-safe WebSocketSession */
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** sessionId -> deviceUdid (cached for fast lookup) */
    private final ConcurrentHashMap<String, String> sessionDeviceMap = new ConcurrentHashMap<>();

    /** Buffered messages for sessions whose WebSocket hasn't connected yet */
    private final ConcurrentHashMap<String, Queue<String>> pendingMessages = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        String sessionId = extractSessionId(wsSession);
        if (sessionId == null) {
            wsSession.close(CloseStatus.BAD_DATA.withReason("Missing session parameter"));
            return;
        }

        RemoteTerminalSession session = sessionService.getSession(sessionId);
        if (session == null) {
            wsSession.close(CloseStatus.BAD_DATA.withReason("Invalid or expired session"));
            return;
        }

        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                wsSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

        sessions.compute(sessionId, (key, existing) -> {
            sessionDeviceMap.put(sessionId, session.deviceUdid());

            Queue<String> pending = pendingMessages.remove(sessionId);
            if (pending != null) {
                String buffered;
                while ((buffered = pending.poll()) != null) {
                    try {
                        safeSession.sendMessage(new TextMessage(buffered));
                        logger.info("Flushed buffered terminal message — sessionId={}", sessionId);
                    } catch (IOException e) {
                        logger.error("Failed to flush buffered terminal message: {}", e.getMessage());
                    }
                }
            }

            return safeSession;
        });

        // Check if device already started terminal (race: MQTT response may arrive before WS connects)
        RemoteTerminalSession currentState = sessionService.getSession(sessionId);
        if (currentState != null && RemoteTerminalSession.STATE_ACTIVE.equals(currentState.state())) {
            Map<String, Object> startedMsg = Map.of("type", "terminal_started", "sessionId", sessionId);
            safeSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(startedMsg)));
            logger.info("Synthesized terminal_started from Redis state — sessionId={}", sessionId);
        }

        // Subscribe to Redis relay channel for cross-instance message delivery
        webSocketRedisRelay.subscribeSession(sessionId, message -> {
            try {
                WebSocketSession ws = sessions.get(sessionId);
                if (ws != null && ws.isOpen()) {
                    ws.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                logger.error("Failed to relay terminal message from Redis — sessionId={}: {}", sessionId, e.getMessage());
            }
        });

        logger.info("Terminal WebSocket connected — sessionId={}, device={}", sessionId, session.deviceUdid());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String sessionId = extractSessionId(wsSession);
        if (sessionId == null) return;

        String deviceUdid = sessionDeviceMap.get(sessionId);
        if (deviceUdid == null) {
            // Cross-instance: WebSocket on this instance but session created on another
            RemoteTerminalSession session = sessionService.getSession(sessionId);
            if (session != null) {
                deviceUdid = session.deviceUdid();
                sessionDeviceMap.put(sessionId, deviceUdid);
                logger.info("Resolved device from Redis for terminal session: {}", sessionId);
            }
        }
        if (deviceUdid == null) {
            logger.warn("No device mapping for terminal session: {}", sessionId);
            return;
        }

        Map<String, Object> msg = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        String type = (String) msg.get("type");

        if (type == null) {
            logger.warn("Terminal WebSocket message without type — sessionId={}", sessionId);
            return;
        }

        switch (type) {
            case "input" -> {
                String input = (String) msg.get("input");
                agentCommandService.sendCommand(deviceUdid, CMD_TERMINAL_INPUT,
                        Map.of("sessionId", sessionId, "input", input));
            }
            case "signal" -> {
                Object signal = msg.get("signal");
                agentCommandService.sendCommand(deviceUdid, CMD_TERMINAL_SIGNAL,
                        Map.of("sessionId", sessionId, "signal", signal));
            }
            case "resize" -> {
                Object cols = msg.get("cols");
                Object rows = msg.get("rows");
                agentCommandService.sendCommand(deviceUdid, CMD_TERMINAL_RESIZE,
                        Map.of("sessionId", sessionId, "cols", cols, "rows", rows));
            }
            case "stop" -> {
                agentCommandService.sendCommand(deviceUdid, CMD_STOP_TERMINAL,
                        Map.of("sessionId", sessionId));
                sessions.remove(sessionId);
                sessionDeviceMap.remove(sessionId);
                pendingMessages.remove(sessionId);
                sessionService.endSession(sessionId);
                logger.info("Stop terminal sent to device — sessionId={}", sessionId);
            }
            default -> logger.warn("Unknown terminal WS message type: {} — sessionId={}", type, sessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        String sessionId = extractSessionId(wsSession);
        if (sessionId != null) {
            String deviceUdid = sessionDeviceMap.get(sessionId);
            // Send stop command to device so the PTY is cleaned up
            if (deviceUdid != null) {
                try {
                    agentCommandService.sendCommand(deviceUdid, CMD_STOP_TERMINAL,
                            Map.of("sessionId", sessionId));
                } catch (Exception e) {
                    logger.warn("Failed to send stop_terminal on WS close: {}", e.getMessage());
                }
            }
            sessions.remove(sessionId);
            pendingMessages.remove(sessionId);
            sessionDeviceMap.remove(sessionId);
            webSocketRedisRelay.unsubscribeSession(sessionId);
            sessionService.endSession(sessionId);
            logger.info("Terminal WebSocket closed — sessionId={}, device={}, status={}",
                    sessionId, deviceUdid, status);
        }
    }

    /**
     * Called by MqttMessageRouter when a terminal-related response arrives from a device.
     */
    public void handleDeviceResponse(String deviceUdid, Map<String, Object> response) {
        try {
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                logger.warn("Terminal device response missing data map — device={}", deviceUdid);
                return;
            }

            String type = (String) data.get("type");
            String sessionId = (String) data.get("sessionId");

            if (sessionId == null) {
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
                    logger.info("Resolved terminal session from Redis — sessionId={}, device={}", sessionId, deviceUdid);
                }
            }

            if (sessionId == null) {
                logger.warn("Cannot route terminal response — no active session for device={}", deviceUdid);
                return;
            }

            if ("terminal_started".equals(type)) {
                sessionService.updateState(sessionId, RemoteTerminalSession.STATE_ACTIVE);
            } else if ("terminal_ended".equals(type)) {
                sessionService.updateState(sessionId, RemoteTerminalSession.STATE_ENDED);
            }

            String json = objectMapper.writeValueAsString(data);


            WebSocketSession wsSession = sessions.get(sessionId);
            if (wsSession != null && wsSession.isOpen()) {
                wsSession.sendMessage(new TextMessage(json));
                logger.debug("Forwarded terminal response to browser — sessionId={}, type={}", sessionId, type);
            } else {
                // WebSocket not yet connected — buffer locally AND relay via Redis.
                // Buffer: WS may connect to THIS instance later (same-instance race).
                // Redis: WS may be on another instance (cross-instance case).
                pendingMessages.computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>()).add(json);
                webSocketRedisRelay.publishToSession(sessionId, json);
                logger.info("Buffered + relayed terminal response — sessionId={}, type={}", sessionId, type);
            }

        } catch (IOException e) {
            logger.error("Failed to forward terminal response to browser: {}", e.getMessage());
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
