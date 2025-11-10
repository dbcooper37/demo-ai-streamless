package com.demo.websocket.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id
    @Column(nullable = false, unique = true)
    private String sessionId;
    
    @Column(nullable = false)
    private String conversationId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String messageId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
    
    @Column(nullable = false)
    private Instant startTime;
    
    private Instant lastActivityTime;
    
    private int totalChunks;
    
    @Embedded
    private StreamMetadata metadata;

    public enum SessionStatus {
        INITIALIZING,
        STREAMING,
        COMPLETED,
        ERROR,
        TIMEOUT
    }
}
