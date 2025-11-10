package com.demo.websocket.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_conversation", columnList = "conversationId"),
    @Index(name = "idx_user", columnList = "userId")
})
public class Message {
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
