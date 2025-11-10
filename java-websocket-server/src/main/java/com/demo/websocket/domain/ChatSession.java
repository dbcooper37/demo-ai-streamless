package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
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
