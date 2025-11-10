package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Message Domain Model (PoC - POJO only, no JPA)
 * 
 * Stored in Redis for PoC. Can add DB persistence later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String conversationId;
    private String userId;
    private MessageRole role;
    private String content;
    private MessageStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<StreamChunk> chunks; // For recovery
    private MessageMetadata metadata;

    public enum MessageRole {
        USER, ASSISTANT, SYSTEM
    }

    public enum MessageStatus {
        PENDING, STREAMING, COMPLETED, FAILED
    }
}
