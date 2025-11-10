package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Chat Session Domain Model (PoC - POJO only, no JPA)
 * 
 * Stored in Redis for PoC. Can add DB persistence later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String sessionId;
    private String conversationId;
    private String userId;
    private String messageId;
    private SessionStatus status;
    private Instant startTime;
    private Instant lastActivityTime;
    private int totalChunks;
    private StreamMetadata metadata;

    public enum SessionStatus {
        INITIALIZING,
        STREAMING,
        COMPLETED,
        ERROR,
        TIMEOUT
    }
}
