package com.demo.websocket.handler;

import com.demo.websocket.domain.*;
import com.demo.websocket.infrastructure.*;
import com.demo.websocket.model.ChatMessage;
import com.demo.websocket.service.ChatHistoryService;
import com.demo.websocket.service.RedisMessageListener;
import com.demo.websocket.service.MetricsService;
import com.demo.websocket.service.SecurityValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final ChatOrchestrator chatOrchestrator;
    private final RecoveryService recoveryService;
    private final ChatHistoryService chatHistoryService;
    private final RedisMessageListener redisMessageListener;
    private final MetricsService metricsService;
    private final SecurityValidator securityValidator;

    // Legacy sessionId -> List of WebSocketSessions (for backward compatibility)
    private final Map<String, ConcurrentHashMap<String, WebSocketSession>> sessionMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ObjectMapper objectMapper,
                                 SessionManager sessionManager,
                                 ChatOrchestrator chatOrchestrator,
                                 RecoveryService recoveryService,
                                 ChatHistoryService chatHistoryService,
                                 RedisMessageListener redisMessageListener,
                                 MetricsService metricsService,
                                 SecurityValidator securityValidator) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.chatOrchestrator = chatOrchestrator;
        this.recoveryService = recoveryService;
        this.chatHistoryService = chatHistoryService;
        this.redisMessageListener = redisMessageListener;
        this.metricsService = metricsService;
        this.securityValidator = securityValidator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        String sessionId = null;
        String userId = null;
        
        try {
            userId = extractUserId(wsSession);
            sessionId = extractSessionId(wsSession);
            String token = extractToken(wsSession);

            // Security validation
            if (!securityValidator.validateToken(token, userId)) {
                log.warn("Invalid token for user: userId={}", userId);
                metricsService.recordWebSocketConnection(userId, false);
                sendError(wsSession, "Authentication failed");
                wsSession.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            // Legacy session tracking (for backward compatibility with old clients)
            sessionMap.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                      .put(wsSession.getId(), wsSession);

            // Register session with distributed coordination
            sessionManager.registerSession(sessionId, wsSession, userId);

            // Subscribe to Redis PubSub for this session (legacy support)
            // NOTE: Commented out to avoid duplicate messages with ChatOrchestrator
            // ChatOrchestrator handles both legacy and enhanced streaming
            // redisMessageListener.subscribe(sessionId, this);

            log.info("WebSocket connected: wsId={}, sessionId={}, userId={}", 
                    wsSession.getId(), sessionId, userId);
            log.info("Session map now contains: {}", sessionMap.keySet());

            // Record metrics
            metricsService.recordWebSocketConnection(userId, true);

            // Send welcome message
            sendWelcomeMessage(wsSession, sessionId);

            // Send chat history
            sendChatHistory(wsSession, sessionId);

            // Start streaming session with enhanced orchestrator
            // This will handle Redis PubSub subscription internally
            chatOrchestrator.startStreamingSession(sessionId, userId,
                    new WebSocketStreamCallback(wsSession));

        } catch (SecurityException e) {
            log.error("Security violation during connection: sessionId={}", sessionId, e);
            if (userId != null) {
                metricsService.recordWebSocketConnection(userId, false);
            }
            metricsService.recordError("SECURITY_VIOLATION", "WebSocketHandler");
            sendError(wsSession, "Security check failed");
            wsSession.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (Exception e) {
            log.error("Error establishing connection", e);
            if (userId != null) {
                metricsService.recordWebSocketConnection(userId, false);
            }
            metricsService.recordError("CONNECTION_ERROR", "WebSocketHandler");
            sendError(wsSession, "Connection failed: " + e.getMessage());
            wsSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        String sessionId = sessionManager.getSessionId(wsSession);
        if (sessionId == null) {
            // Fallback for legacy sessions
            sessionId = extractSessionId(wsSession);
            log.warn("Session not found in manager for WebSocket: {}, using fallback", wsSession.getId());
        }

        // Record message received
        metricsService.recordMessageReceived("text");

        try {
            String payload = message.getPayload();
            log.debug("Received message from {}: {}", wsSession.getId(), payload);

            // Try to parse as JSON for enhanced features
            try {
                Map<String, Object> jsonPayload = objectMapper.readValue(payload, Map.class);
                String type = (String) jsonPayload.get("type");

                switch (type != null ? type : "") {
                    case "reconnect":
                        handleReconnect(wsSession, sessionId, jsonPayload);
                        return;

                    case "heartbeat":
                        handleHeartbeat(wsSession, sessionId);
                        return;

                    case "ping":
                        wsSession.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                        return;

                    default:
                        log.warn("Unknown message type: {}", type);
                }
            } catch (Exception e) {
                // Not JSON or parsing failed, handle as plain text
                if ("ping".equals(payload)) {
                    wsSession.sendMessage(new TextMessage("pong"));
                }
            }

        } catch (Exception e) {
            log.error("Error handling message: sessionId={}", sessionId, e);
            metricsService.recordError("MESSAGE_PROCESSING_ERROR", "WebSocketHandler");
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

            metricsService.recordRecoveryAttempt(false); // Will update to true if successful
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

                    metricsService.recordRecoveryAttempt(true);
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
        if (sessionId == null) {
            sessionId = extractSessionId(wsSession);
        }

        log.info("WebSocket closed: wsId={}, sessionId={}, status={}",
                wsSession.getId(), sessionId, status);

        // Record disconnection
        String userId = extractUserId(wsSession);
        metricsService.recordWebSocketDisconnection(userId);

        // Legacy cleanup
        ConcurrentHashMap<String, WebSocketSession> sessions = sessionMap.get(sessionId);
        if (sessions != null) {
            sessions.remove(wsSession.getId());
            if (sessions.isEmpty()) {
                sessionMap.remove(sessionId);
                // Unsubscribe from Redis if no more connections for this session
                // NOTE: Commented out because we're not using RedisMessageListener subscription
                // redisMessageListener.unsubscribe(sessionId);
            }
        }

        // Enhanced cleanup
        if (sessionId != null) {
            sessionManager.unregisterSession(sessionId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        String sessionId = sessionManager.getSessionId(wsSession);
        log.error("WebSocket transport error: wsId={}, sessionId={}",
                wsSession.getId(), sessionId, exception);

        // Record error
        metricsService.recordError("TRANSPORT_ERROR", "WebSocketHandler");

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

    /**
     * Send chat history to a specific WebSocket session
     */
    private void sendChatHistory(WebSocketSession wsSession, String sessionId) {
        try {
            List<ChatMessage> history = chatHistoryService.getHistory(sessionId);
            if (!history.isEmpty()) {
                String historyJson = objectMapper.writeValueAsString(Map.of(
                    "type", "history",
                    "messages", history
                ));
                wsSession.sendMessage(new TextMessage(historyJson));
                log.info("Sent {} history messages to session {}", history.size(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error sending history to session {}: {}", sessionId, e.getMessage());
        }
    }

    private void sendChunk(WebSocketSession wsSession, StreamChunk chunk) {
        try {
            // Convert StreamChunk to ChatMessage format for frontend compatibility
            String sessionId = sessionManager.getSessionId(wsSession);
            if (sessionId == null) {
                sessionId = extractSessionId(wsSession);
            }
            String userId = extractUserId(wsSession);
            
            ChatMessage chatMessage = ChatMessage.builder()
                    .messageId(chunk.getMessageId())
                    .sessionId(sessionId)
                    .userId(userId)
                    .role("assistant")
                    .content(chunk.getContent())
                    .chunk(chunk.getContent())
                    .timestamp(chunk.getTimestamp().toEpochMilli())
                    .isComplete(false)
                    .build();
            
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "message",
                    "data", chatMessage
            ));

            log.info("Sending chunk to WebSocket session {}: index={}, contentLength={}", 
                    wsSession.getId(), chunk.getIndex(), 
                    chunk.getContent() != null ? chunk.getContent().length() : 0);
            
            wsSession.sendMessage(new TextMessage(payload));

        } catch (IOException e) {
            log.error("Failed to send chunk to session {}", wsSession.getId(), e);
        }
    }

    private void sendCompleteMessage(WebSocketSession wsSession, Message message) {
        try {
            // Convert Message to ChatMessage format for frontend compatibility
            String sessionId = sessionManager.getSessionId(wsSession);
            if (sessionId == null) {
                sessionId = extractSessionId(wsSession);
            }
            
            ChatMessage chatMessage = ChatMessage.builder()
                    .messageId(message.getId())
                    .sessionId(sessionId)
                    .userId(message.getUserId())
                    .role(message.getRole().name().toLowerCase())
                    .content(message.getContent())
                    .timestamp(message.getCreatedAt().toEpochMilli())
                    .isComplete(true)
                    .build();
            
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "message",
                    "data", chatMessage
            ));

            log.info("Sending complete message to session {}: messageId={}", 
                    wsSession.getId(), message.getId());
            
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

    /**
     * Broadcast message to all WebSocket sessions for a given chat session
     * Legacy method for backward compatibility with RedisMessageListener
     */
    public void broadcastToSession(String sessionId, ChatMessage message) {
        ConcurrentHashMap<String, WebSocketSession> sessions = sessionMap.get(sessionId);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("No active WebSocket sessions for chat session: {}", sessionId);
            log.warn("Available sessions: {}", sessionMap.keySet());
            return;
        }

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(Map.of(
                "type", "message",
                "data", message
            ));
        } catch (Exception e) {
            log.error("Error serializing message: {}", e.getMessage());
            return;
        }

        log.info("Broadcasting to {} WebSocket sessions for chat session {}", 
                sessions.size(), sessionId);
        
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    log.info("Sending message to WebSocket session: {}", session.getId());
                    session.sendMessage(new TextMessage(messageJson));
                } else {
                    log.warn("WebSocket session {} is not open", session.getId());
                }
            } catch (IOException e) {
                log.error("Error sending message to WebSocket session {}: {}",
                         session.getId(), e.getMessage());
            }
        });

        log.info("Broadcasted message to {} WebSocket sessions for chat session {}",
                 sessions.size(), sessionId);
    }

    /**
     * Extract session ID from WebSocket session URI
     * Expected format: /ws/chat?session_id=xxx
     */
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

    private String extractToken(WebSocketSession session) {
        // Extract from query params
        String query = session.getUri().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring("token=".length());
                }
            }
        }
        // For development/testing, allow without token
        log.warn("No token provided, using development mode");
        return "dev-token";
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
