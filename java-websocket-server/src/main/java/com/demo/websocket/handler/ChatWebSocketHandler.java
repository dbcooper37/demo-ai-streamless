package com.demo.websocket.handler;

import com.demo.websocket.model.ChatMessage;
import com.demo.websocket.service.ChatHistoryService;
import com.demo.websocket.service.RedisMessageListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final RedisMessageListener redisMessageListener;
    private final ChatHistoryService chatHistoryService;

    // sessionId -> List of WebSocketSessions
    private final Map<String, ConcurrentHashMap<String, WebSocketSession>> sessionMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ObjectMapper objectMapper,
                                 RedisMessageListener redisMessageListener,
                                 ChatHistoryService chatHistoryService) {
        this.objectMapper = objectMapper;
        this.redisMessageListener = redisMessageListener;
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        log.info("WebSocket connected: {} for session: {}", session.getId(), sessionId);

        // Add session to map
        sessionMap.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                  .put(session.getId(), session);

        // Subscribe to Redis PubSub for this session
        redisMessageListener.subscribe(sessionId, this);

        // Send chat history to newly connected client
        sendChatHistory(session, sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from {}: {}", session.getId(), payload);

        // Handle ping/pong or other client messages if needed
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        log.info("WebSocket disconnected: {} for session: {}", session.getId(), sessionId);

        // Remove session from map
        ConcurrentHashMap<String, WebSocketSession> sessions = sessionMap.get(sessionId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                sessionMap.remove(sessionId);
                // Unsubscribe from Redis if no more connections for this session
                redisMessageListener.unsubscribe(sessionId);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket error for session {}: {}", session.getId(), exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    /**
     * Send chat history to a specific WebSocket session
     */
    private void sendChatHistory(WebSocketSession session, String sessionId) {
        try {
            List<ChatMessage> history = chatHistoryService.getHistory(sessionId);
            if (!history.isEmpty()) {
                String historyJson = objectMapper.writeValueAsString(Map.of(
                    "type", "history",
                    "messages", history
                ));
                session.sendMessage(new TextMessage(historyJson));
                log.info("Sent {} history messages to session {}", history.size(), session.getId());
            }
        } catch (Exception e) {
            log.error("Error sending history to session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Broadcast message to all WebSocket sessions for a given chat session
     */
    public void broadcastToSession(String sessionId, ChatMessage message) {
        ConcurrentHashMap<String, WebSocketSession> sessions = sessionMap.get(sessionId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No active WebSocket sessions for chat session: {}", sessionId);
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

        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(messageJson));
                }
            } catch (IOException e) {
                log.error("Error sending message to WebSocket session {}: {}",
                         session.getId(), e.getMessage());
            }
        });

        log.debug("Broadcasted message to {} WebSocket sessions for chat session {}",
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
}
