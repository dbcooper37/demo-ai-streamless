package com.demo.websocket.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {
    
    private MessageType type;
    private Object data;
    private String error;
    private Instant timestamp;
    private Map<String, Object> metadata;
    
    public enum MessageType {
        // Client → Server
        CHAT_REQUEST,
        RECONNECT,
        CANCEL_STREAM,
        HEARTBEAT,
        
        // Server → Client
        WELCOME,
        CHUNK,
        COMPLETE,
        ERROR,
        HEARTBEAT_ACK,
        RECOVERY_STATUS
    }
    
    // Convert to ChatRequest
    @SuppressWarnings("unchecked")
    public ChatRequest toChatRequest() {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            return ChatRequest.builder()
                .conversationId((String) map.get("conversationId"))
                .message((String) map.get("message"))
                .context((Map<String, Object>) map.get("context"))
                .build();
        }
        return null;
    }
    
    // Convert to RecoveryRequest
    @SuppressWarnings("unchecked")
    public RecoveryRequest toRecoveryRequest() {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            return RecoveryRequest.builder()
                .sessionId((String) map.get("sessionId"))
                .messageId((String) map.get("messageId"))
                .lastChunkIndex((Integer) map.get("lastChunkIndex"))
                .clientTimestamp(Instant.now())
                .build();
        }
        return null;
    }
    
    // Factory methods
    public static WebSocketMessage welcome(String sessionId, String userId) {
        return WebSocketMessage.builder()
            .type(MessageType.WELCOME)
            .data(Map.of(
                "sessionId", sessionId,
                "userId", userId,
                "timestamp", Instant.now()
            ))
            .timestamp(Instant.now())
            .build();
    }
    
    public static WebSocketMessage chunk(StreamChunk chunk) {
        return WebSocketMessage.builder()
            .type(MessageType.CHUNK)
            .data(chunk)
            .timestamp(Instant.now())
            .build();
    }
    
    public static WebSocketMessage complete(Message message) {
        return WebSocketMessage.builder()
            .type(MessageType.COMPLETE)
            .data(message)
            .timestamp(Instant.now())
            .build();
    }
    
    public static WebSocketMessage error(String errorMessage) {
        return WebSocketMessage.builder()
            .type(MessageType.ERROR)
            .error(errorMessage)
            .timestamp(Instant.now())
            .build();
    }
    
    public static WebSocketMessage heartbeatAck() {
        return WebSocketMessage.builder()
            .type(MessageType.HEARTBEAT_ACK)
            .timestamp(Instant.now())
            .build();
    }
    
    public static WebSocketMessage recoveryStatus(String status, int chunksRecovered) {
        return WebSocketMessage.builder()
            .type(MessageType.RECOVERY_STATUS)
            .data(Map.of(
                "status", status,
                "chunksRecovered", chunksRecovered
            ))
            .timestamp(Instant.now())
            .build();
    }
}
