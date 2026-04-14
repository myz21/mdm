package com.arcyintel.arcops.apple_mdm.services.vnc;

import com.arcyintel.arcops.apple_mdm.models.session.VncSession;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.CMD_STOP_VNC;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Binary WebSocket handler that relays VNC/RFB frames between a browser (noVNC viewer)
 * and a macOS agent (TCP↔WebSocket bridge).
 *
 * <p>Browser connects to: /vnc-tunnel/ws?session={sessionId}&role=viewer
 * <p>Agent connects to:   /vnc-tunnel/ws?session={sessionId}&role=agent
 */
@Component
@RequiredArgsConstructor
public class VncTunnelHandler extends BinaryWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(VncTunnelHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 60_000;
    private static final int BUFFER_SIZE_LIMIT = 5 * 1024 * 1024;

    private final VncSessionService sessionService;
    private final VncBinaryRelay vncBinaryRelay;
    private final AgentCommandService agentCommandService;

    /** sessionId → viewer (browser/noVNC) WebSocket session */
    private final ConcurrentHashMap<String, WebSocketSession> viewers = new ConcurrentHashMap<>();

    /** sessionId → agent (macOS) WebSocket session */
    private final ConcurrentHashMap<String, WebSocketSession> agents = new ConcurrentHashMap<>();

    /** Per-session pending message queues for race-condition handling */
    private final ConcurrentHashMap<String, PendingQueue> pendingQueues = new ConcurrentHashMap<>();

    static class PendingQueue {
        /** Messages from viewer buffered until agent connects */
        final Queue<ByteBuffer> toAgent = new ConcurrentLinkedQueue<>();
        /** Messages from agent buffered until viewer connects */
        final Queue<ByteBuffer> toViewer = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        logger.info("VNC WebSocket connection established — uri={}, remoteAddr={}",
                wsSession.getUri(), wsSession.getRemoteAddress());

        String sessionId = extractParam(wsSession, "session");
        String role = extractParam(wsSession, "role");

        if (sessionId == null || role == null) {
            logger.warn("VNC WebSocket rejected — missing params, uri={}, session={}, role={}",
                    wsSession.getUri(), sessionId, role);
            wsSession.close(CloseStatus.BAD_DATA.withReason("Missing session or role parameter"));
            return;
        }

        VncSession session = sessionService.getSession(sessionId);
        if (session == null) {
            logger.warn("VNC WebSocket rejected — session not found in Redis, sessionId={}, role={}", sessionId, role);
            wsSession.close(CloseStatus.BAD_DATA.withReason("Invalid or expired VNC session"));
            return;
        }

        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                wsSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

        PendingQueue pending = pendingQueues.computeIfAbsent(sessionId, k -> new PendingQueue());

        if ("agent".equals(role)) {
            agents.put(sessionId, safeSession);
            sessionService.updateState(sessionId, VncSession.STATE_AGENT_CONNECTED);
            logger.info("VNC agent connected — sessionId={}", sessionId);

            // Subscribe to Redis relay for viewer→agent frames from other instances
            vncBinaryRelay.subscribe(sessionId, "agent", data -> {
                try {
                    WebSocketSession localAgent = agents.get(sessionId);
                    if (localAgent != null && localAgent.isOpen()) {
                        localAgent.sendMessage(new BinaryMessage(data));
                    }
                } catch (Exception e) {
                    logger.error("VNC Redis relay→agent error — sessionId={}: {}", sessionId, e.getMessage());
                }
            });

            // Flush any viewer→agent messages that arrived before agent connected
            ByteBuffer buf;
            while ((buf = pending.toAgent.poll()) != null) {
                safeSession.sendMessage(new BinaryMessage(buf));
            }

            // If viewer is already connected, mark session as active
            if (viewers.containsKey(sessionId)) {
                sessionService.updateState(sessionId, VncSession.STATE_ACTIVE);
            }
        } else if ("viewer".equals(role)) {
            viewers.put(sessionId, safeSession);
            sessionService.updateState(sessionId, VncSession.STATE_VIEWER_CONNECTED);
            logger.info("VNC viewer connected — sessionId={}", sessionId);

            // Subscribe to Redis relay for agent→viewer frames from other instances
            vncBinaryRelay.subscribe(sessionId, "viewer", data -> {
                try {
                    WebSocketSession localViewer = viewers.get(sessionId);
                    if (localViewer != null && localViewer.isOpen()) {
                        localViewer.sendMessage(new BinaryMessage(data));
                    }
                } catch (Exception e) {
                    logger.error("VNC Redis relay→viewer error — sessionId={}: {}", sessionId, e.getMessage());
                }
            });

            // Flush any agent→viewer messages that arrived before viewer connected
            ByteBuffer buf;
            while ((buf = pending.toViewer.poll()) != null) {
                safeSession.sendMessage(new BinaryMessage(buf));
            }

            // If agent is already connected, mark session as active
            if (agents.containsKey(sessionId)) {
                sessionService.updateState(sessionId, VncSession.STATE_ACTIVE);
            }
        } else {
            wsSession.close(CloseStatus.BAD_DATA.withReason("Invalid role: must be 'viewer' or 'agent'"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) throws Exception {
        String sessionId = extractParam(wsSession, "session");
        String role = extractParam(wsSession, "role");
        if (sessionId == null || role == null) return;

        ByteBuffer payload = message.getPayload();
        int size = payload.remaining();
        PendingQueue pending = pendingQueues.computeIfAbsent(sessionId, k -> new PendingQueue());

        if ("viewer".equals(role)) {
            // Viewer → Agent
            WebSocketSession agentSession = agents.get(sessionId);
            if (agentSession != null && agentSession.isOpen()) {
                try {
                    agentSession.sendMessage(new BinaryMessage(payload));
                } catch (Exception e) {
                    logger.error("VNC relay error viewer→agent — sessionId={}, size={}: {}", sessionId, size, e.getMessage());
                    throw e;
                }
            } else {
                // Agent not on this instance — relay via Redis + buffer locally
                vncBinaryRelay.publish(sessionId, "agent", payload);
                pending.toAgent.add(copyBuffer(payload));
            }
        } else if ("agent".equals(role)) {
            // Agent → Viewer
            WebSocketSession viewerSession = viewers.get(sessionId);
            if (viewerSession != null && viewerSession.isOpen()) {
                try {
                    viewerSession.sendMessage(new BinaryMessage(payload));
                } catch (Exception e) {
                    logger.error("VNC relay error agent→viewer — sessionId={}, size={}: {}", sessionId, size, e.getMessage());
                    throw e;
                }
            } else {
                // Viewer not on this instance — relay via Redis + buffer locally
                vncBinaryRelay.publish(sessionId, "viewer", payload);
                pending.toViewer.add(copyBuffer(payload));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        String sessionId = extractParam(wsSession, "session");
        String role = extractParam(wsSession, "role");
        if (sessionId == null || role == null) {
            logger.warn("VNC WebSocket closed — unknown session/role, uri={}, status={}", wsSession.getUri(), status);
            return;
        }

        logger.info("VNC {} disconnected — sessionId={}, status={}", role, sessionId, status);

        if ("viewer".equals(role)) {
            viewers.remove(sessionId);
            vncBinaryRelay.unsubscribe(sessionId, "viewer");
            // Close the agent side too (same instance)
            closeAndRemove(agents, sessionId, "agent");
            vncBinaryRelay.unsubscribe(sessionId, "agent");
            // Send MQTT stop to device so agent releases VNC tunnel (cross-instance safety)
            VncSession session = sessionService.getSession(sessionId);
            if (session != null) {
                try {
                    agentCommandService.sendCommand(session.deviceUdid(), CMD_STOP_VNC,
                            Map.of("sessionId", sessionId));
                    logger.info("Sent StopVnc to device on viewer disconnect — sessionId={}", sessionId);
                } catch (Exception e) {
                    logger.warn("Failed to send StopVnc on viewer disconnect: {}", e.getMessage());
                }
            }
        } else if ("agent".equals(role)) {
            agents.remove(sessionId);
            vncBinaryRelay.unsubscribe(sessionId, "agent");
            // Close the viewer side too
            closeAndRemove(viewers, sessionId, "viewer");
            vncBinaryRelay.unsubscribe(sessionId, "viewer");
        }

        pendingQueues.remove(sessionId);
        sessionService.endSession(sessionId);
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        String sessionId = extractParam(wsSession, "session");
        String role = extractParam(wsSession, "role");
        logger.error("VNC transport error — sessionId={}, role={}: {}", sessionId, role, exception.getMessage(), exception);
    }

    private void closeAndRemove(ConcurrentHashMap<String, WebSocketSession> map, String sessionId, String side) {
        WebSocketSession session = map.remove(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.GOING_AWAY.withReason("Other side disconnected"));
                logger.info("Closed VNC {} session — sessionId={}", side, sessionId);
            } catch (IOException e) {
                logger.warn("Failed to close VNC {} session — sessionId={}: {}", side, sessionId, e.getMessage());
            }
        }
    }

    private ByteBuffer copyBuffer(ByteBuffer original) {
        ByteBuffer copy = ByteBuffer.allocate(original.remaining());
        copy.put(original.duplicate());
        copy.flip();
        return copy;
    }

    private String extractParam(WebSocketSession wsSession, String param) {
        URI uri = wsSession.getUri();
        if (uri == null) return null;
        return UriComponentsBuilder.fromUri(uri).build()
                .getQueryParams()
                .getFirst(param);
    }
}
