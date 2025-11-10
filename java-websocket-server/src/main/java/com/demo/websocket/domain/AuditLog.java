package com.demo.websocket.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Audit Log Entity for Event Sourcing
 * 
 * Stores all significant events from Kafka for:
 * - Compliance and regulatory requirements
 * - Security auditing
 * - Debugging and troubleshooting
 * - Analytics and reporting
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_conversation_id", columnList = "conversation_id"),
        @Index(name = "idx_audit_message_id", columnList = "message_id"),
        @Index(name = "idx_audit_session_id", columnList = "session_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * Event type (SESSION_STARTED, CHUNK_RECEIVED, STREAM_COMPLETED, etc.)
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * Event timestamp
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * User ID (for user activity tracking)
     */
    @Column(name = "user_id", length = 255)
    private String userId;

    /**
     * Conversation ID (for conversation tracking)
     */
    @Column(name = "conversation_id", length = 255)
    private String conversationId;

    /**
     * Message ID (for message tracking)
     */
    @Column(name = "message_id", length = 255)
    private String messageId;

    /**
     * Session ID (for session tracking)
     */
    @Column(name = "session_id", length = 255)
    private String sessionId;

    /**
     * Full event data as JSON
     */
    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    /**
     * Source topic (chat-events, stream-events, etc.)
     */
    @Column(name = "source", length = 100)
    private String source;

    /**
     * When this audit log was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
