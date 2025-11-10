package com.demo.websocket.consumer;

import com.demo.websocket.domain.AuditLog;
import com.demo.websocket.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka Consumer for Audit Trail
 * 
 * Listens to all chat/stream events and stores them for:
 * - Compliance and regulatory requirements
 * - Debugging and troubleshooting
 * - Security auditing
 * - User activity tracking
 * 
 * Enable with: KAFKA_ENABLED=true
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class AuditTrailConsumer {

    private final ObjectMapper objectMapper;
    private final AuditLogRepository auditLogRepository;

    public AuditTrailConsumer(ObjectMapper objectMapper, AuditLogRepository auditLogRepository) {
        this.objectMapper = objectMapper;
        this.auditLogRepository = auditLogRepository;
        log.info("AuditTrailConsumer initialized - audit logging enabled");
    }

    /**
     * Consume chat events for audit trail
     */
    @KafkaListener(
        topics = "chat-events",
        groupId = "audit-trail-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeChatEvent(String eventJson, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            // Create audit log entry
            AuditLog auditLog = AuditLog.builder()
                .eventType((String) event.get("eventType"))
                .timestamp(Instant.parse((String) event.get("timestamp")))
                .userId((String) event.get("userId"))
                .conversationId((String) event.get("conversationId"))
                .messageId((String) event.get("messageId"))
                .eventData(eventJson)
                .source("chat-events")
                .build();
            
            // Save to database (or S3 for long-term storage)
            auditLogRepository.save(auditLog);
            
            log.debug("Audit log saved: type={}, messageId={}", 
                auditLog.getEventType(), auditLog.getMessageId());
            
            // Acknowledge after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process audit event", e);
            // Don't acknowledge - will retry
            throw new RuntimeException("Audit processing failed", e);
        }
    }

    /**
     * Consume stream events for audit trail
     */
    @KafkaListener(
        topics = "stream-events",
        groupId = "audit-trail-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStreamEvent(String eventJson, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            // Only store significant events (not every chunk)
            String eventType = (String) event.get("eventType");
            if (shouldAuditStreamEvent(eventType)) {
                AuditLog auditLog = AuditLog.builder()
                    .eventType(eventType)
                    .timestamp(Instant.parse((String) event.get("timestamp")))
                    .sessionId((String) event.get("sessionId"))
                    .messageId((String) event.get("messageId"))
                    .eventData(eventJson)
                    .source("stream-events")
                    .build();
                
                auditLogRepository.save(auditLog);
                
                log.debug("Stream audit log saved: type={}, sessionId={}", 
                    auditLog.getEventType(), auditLog.getSessionId());
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process stream audit event", e);
            throw new RuntimeException("Stream audit processing failed", e);
        }
    }

    /**
     * Determine if stream event should be audited
     * (Skip CHUNK_RECEIVED to reduce storage)
     */
    private boolean shouldAuditStreamEvent(String eventType) {
        return switch (eventType) {
            case "SESSION_STARTED",
                 "STREAM_COMPLETED",
                 "STREAM_ERROR",
                 "RECOVERY_ATTEMPT" -> true;
            case "CHUNK_RECEIVED" -> false;  // Too many, skip
            default -> true;
        };
    }
}
