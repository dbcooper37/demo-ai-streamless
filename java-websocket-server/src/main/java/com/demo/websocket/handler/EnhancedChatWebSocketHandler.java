package com.demo.websocket.handler;

import com.demo.websocket.domain.*;
import com.demo.websocket.infrastructure.*;
import com.demo.websocket.service.ChatHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component("enhancedChatWebSocketHandler")
public class EnhancedChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final ChatOrchestrator chatOrchestrator;
    private final RecoveryService recoveryService;
    private final ChatHistoryService chatHistoryService;

    public EnhancedChatWebSocketHandler(ObjectMapper objectMapper,
                                       SessionManager sessionManager,
                                       ChatOrchestrator chatOrchestrator,
                                       RecoveryService recoveryService,
                                       ChatHistoryService chatHistoryService) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.chatOrchestrator = chatOrchestrator;
        this.recoveryService = recoveryService;
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        try {
            String userId = extractUserId(wsSession);
            String sessionId = extractSessionId(wsSession);

            // Register session with distributed coordination
            sessionManager.registerSession(sessionId, wsSession, userId);

            log.info("WebSocket connected: wsId={}, sessionId={}, userId={}",
                    wsSession.getId(), sessionId, userId);

            // Send welcome message
            sendWelcomeMessage(wsSession, sessionId);

            // Send chat history
            sendChatHistory(wsSession, sessionId);

            // Start streaming session
            chatOrchestrator.startStreamingSession(sessionId, userId,
                    new WebSocketStreamCallback(wsSession));

        } catch (Exception e) {
            log.error("Error establishing connection", e);
            sendError(wsSession, "Connection failed: " + e.getMessage());
            wsSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        String sessionId = sessionManager.getSessionId(wsSession);
        if (sessionId == null) {
            log.warn("Session not found for WebSocket: {}", wsSession.getId());
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    message.getPayload(), Map.class);

            String type = (String) payload.get("type");

            switch (type != null ? type : "") {
                case "reconnect":
                    handleReconnect(wsSession, sessionId, payload);
                    break;

                case "heartbeat":
                    handleHeartbeat(wsSession, sessionId);
                    break;

                case "ping":
                    wsSession.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                    break;

                default:
                    log.warn("Unknown message type: {}", type);
            }

        } catch (Exception e) {
            log.error("Error handling message: sessionId={}", sessionId, e);
            sendError(wsSession, "Message processing failed");
        }
    }

    private void handleReconnect(WebSocketSession wsSession,
                                String sessionId,
                                Map<String, Object> payload) {

        try {
            String messageId = (String) payload.get("messageId");
            Integer lastChunkIndex = (Integer) payload.get("lastChunkIndex");

            RecoveryRequest recoveryRequest = RecoveryRequest.builder()
                    .sessionId(sessionId)
                    .messageId(messageId)
                    .lastChunkIndex(lastChunkIndex)
                    .clientTimestamp(Instant.now())
                    .build();

            log.info("Recovery requested: sessionId={}, messageId={}, lastChunk={}",
                    sessionId, messageId, lastChunkIndex);

            RecoveryResponse recovery = recoveryService.recoverStream(recoveryRequest);

            switch (recovery.getStatus()) {
                case RECOVERED:
                    // Send missing chunks
                    recovery.getMissingChunks().forEach(chunk ->
                            sendChunk(wsSession, chunk));

                    // Resubscribe to ongoing stream
                    if (recovery.getSession().getStatus() == ChatSession.SessionStatus.STREAMING) {
                        chatOrchestrator.resubscribeStream(
                                sessionId,
                                recovery.getSession(),
                                new WebSocketStreamCallback(wsSession)
                        );
                    }

                    sendRecoveryStatus(wsSession, "recovered", recovery.getMissingChunks().size());
                    break;

                case COMPLETED:
                    // Send complete message
                    sendCompleteMessage(wsSession, recovery.getCompleteMessage());
                    sendRecoveryStatus(wsSession, "completed", 0);
                    break;

                case NOT_FOUND:
                case EXPIRED:
                    sendRecoveryStatus(wsSession, recovery.getStatus().name().toLowerCase(), 0);
                    break;

                case ERROR:
                    sendError(wsSession, "Recovery failed");
                    break;
            }

        } catch (Exception e) {
            log.error("Error during reconnection: sessionId={}", sessionId, e);
            sendError(wsSession, "Reconnection failed");
        }
    }

    private void handleHeartbeat(WebSocketSession wsSession, String sessionId) {
        sessionManager.updateHeartbeat(sessionId);
        try {
            wsSession.sendMessage(new TextMessage("{\"type\":\"heartbeat_ack\"}"));
        } catch (IOException e) {
            log.error("Failed to send heartbeat ack", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        String sessionId = sessionManager.getSessionId(wsSession);
        if (sessionId != null) {
            log.info("WebSocket closed: wsId={}, sessionId={}, status={}",
                    wsSession.getId(), sessionId, status);

            sessionManager.unregisterSession(sessionId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        String sessionId = sessionManager.getSessionId(wsSession);
        log.error("WebSocket transport error: wsId={}, sessionId={}",
                wsSession.getId(), sessionId, exception);

        if (sessionId != null) {
            sessionManager.markSessionError(sessionId);
        }
    }

    // Helper methods

    private void sendWelcomeMessage(WebSocketSession wsSession, String sessionId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "welcome",
                    "sessionId", sessionId,
                    "timestamp", Instant.now().toString()
            ));

            wsSession.sendMessage(new TextMessage(payload));
            log.debug("Sent welcome message to session: {}", sessionId);

        } catch (Exception e) {
            log.error("Failed to send welcome message", e);
        }
    }

    private void sendChatHistory(WebSocketSession wsSession, String sessionId) {
        try {
            var history = chatHistoryService.getHistory(sessionId);
            if (!history.isEmpty()) {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "type", "history",
                        "messages", history
                ));

                wsSession.sendMessage(new TextMessage(payload));
                log.info("Sent {} history messages to session {}", history.size(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error sending history to session {}", sessionId, e);
        }
    }

    private void sendChunk(WebSocketSession wsSession, StreamChunk chunk) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "chunk",
                    "data", chunk
            ));

            wsSession.sendMessage(new TextMessage(payload));

        } catch (IOException e) {
            log.error("Failed to send chunk", e);
        }
    }

    private void sendCompleteMessage(WebSocketSession wsSession, Message message) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "complete",
                    "data", message
            ));

            wsSession.sendMessage(new TextMessage(payload));

        } catch (IOException e) {
            log.error("Failed to send complete message", e);
        }
    }

    private void sendRecoveryStatus(WebSocketSession wsSession, String status, int chunksRecovered) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "recovery_status",
                    "status", status,
                    "chunksRecovered", chunksRecovered
            ));

            wsSession.sendMessage(new TextMessage(payload));

        } catch (IOException e) {
            log.error("Failed to send recovery status", e);
        }
    }

    private void sendError(WebSocketSession wsSession, String error) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "error", error,
                    "timestamp", Instant.now().toString()
            ));

            wsSession.sendMessage(new TextMessage(payload));
        } catch (IOException e) {
            log.error("Failed to send error message", e);
        }
    }

    private String extractSessionId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("session_id=")) {
            return query.substring("session_id=".length());
        }
        return "default";
    }

    private String extractUserId(WebSocketSession session) {
        // Extract from query params or use default
        String query = session.getUri().getQuery();
        if (query != null && query.contains("user_id=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("user_id=")) {
                    return param.substring("user_id=".length());
                }
            }
        }
        return "default_user";
    }

    /**
     * WebSocket stream callback implementation
     */
    private class WebSocketStreamCallback implements StreamCallback {
        private final WebSocketSession wsSession;

        WebSocketStreamCallback(WebSocketSession wsSession) {
            this.wsSession = wsSession;
        }

        @Override
        public void onChunk(StreamChunk chunk) {
            sendChunk(wsSession, chunk);
        }

        @Override
        public void onComplete(Message message) {
            sendCompleteMessage(wsSession, message);
        }

        @Override
        public void onError(Throwable error) {
            sendError(wsSession, error.getMessage());
        }
    }
}
