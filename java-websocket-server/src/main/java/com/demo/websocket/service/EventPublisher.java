package com.demo.websocket.service;

import com.demo.websocket.domain.ChatSession;
import com.demo.websocket.domain.Message;
import com.demo.websocket.domain.StreamChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Event Publisher for Kafka
 * 
 * Publishes domain events to Kafka for:
 * - Event sourcing
 * - Audit logging
 * - Analytics
 * - Multi-service coordination
 */
@Service
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MetricsService metricsService;

    @Value("${kafka.topics.chat-events:chat-events}")
    private String chatEventsTopic;

    @Value("${kafka.topics.stream-events:stream-events}")
    private String streamEventsTopic;

    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, MetricsService metricsService) {
        this.kafkaTemplate = kafkaTemplate;
        this.metricsService = metricsService;
    }

    /**
     * Publish session started event
     */
    public void publishSessionStarted(ChatSession session) {
        if (!kafkaEnabled) {
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "SESSION_STARTED");
        event.put("timestamp", Instant.now().toString());
        event.put("sessionId", session.getSessionId());
        event.put("userId", session.getUserId());
        event.put("messageId", session.getMessageId());
        event.put("conversationId", session.getConversationId());

        publishEvent(streamEventsTopic, session.getSessionId(), event, "SESSION_STARTED");
    }

    /**
     * Publish chunk received event
     */
    public void publishChunkReceived(String sessionId, StreamChunk chunk) {
        if (!kafkaEnabled) {
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CHUNK_RECEIVED");
        event.put("timestamp", Instant.now().toString());
        event.put("sessionId", sessionId);
        event.put("messageId", chunk.getMessageId());
        event.put("chunkIndex", chunk.getIndex());
        event.put("contentLength", chunk.getContent() != null ? chunk.getContent().length() : 0);

        publishEvent(streamEventsTopic, sessionId, event, "CHUNK_RECEIVED");
    }

    /**
     * Publish stream completed event
     */
    public void publishStreamCompleted(String sessionId, Message message, int totalChunks) {
        if (!kafkaEnabled) {
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "STREAM_COMPLETED");
        event.put("timestamp", Instant.now().toString());
        event.put("sessionId", sessionId);
        event.put("messageId", message.getId());
        event.put("conversationId", message.getConversationId());
        event.put("totalChunks", totalChunks);
        event.put("contentLength", message.getContent() != null ? message.getContent().length() : 0);

        publishEvent(streamEventsTopic, sessionId, event, "STREAM_COMPLETED");
    }

    /**
     * Publish stream error event
     */
    public void publishStreamError(String sessionId, String messageId, String error) {
        if (!kafkaEnabled) {
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "STREAM_ERROR");
        event.put("timestamp", Instant.now().toString());
        event.put("sessionId", sessionId);
        event.put("messageId", messageId);
        event.put("error", error);

        publishEvent(streamEventsTopic, sessionId, event, "STREAM_ERROR");
    }

    /**
     * Publish recovery attempt event
     */
    public void publishRecoveryAttempt(String sessionId, int fromIndex, boolean success) {
        if (!kafkaEnabled) {
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "RECOVERY_ATTEMPT");
        event.put("timestamp", Instant.now().toString());
        event.put("sessionId", sessionId);
        event.put("fromIndex", fromIndex);
        event.put("success", success);

        publishEvent(streamEventsTopic, sessionId, event, "RECOVERY_ATTEMPT");
    }

    /**
     * Publish chat message event
     */
    public void publishChatMessage(Message message) {
        if (!kafkaEnabled) {
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CHAT_MESSAGE");
        event.put("timestamp", Instant.now().toString());
        event.put("messageId", message.getId());
        event.put("conversationId", message.getConversationId());
        event.put("userId", message.getUserId());
        event.put("role", message.getRole().toString());
        event.put("status", message.getStatus().toString());

        publishEvent(chatEventsTopic, message.getConversationId(), event, "CHAT_MESSAGE");
    }

    /**
     * Generic event publisher
     */
    private void publishEvent(String topic, String key, Object event, String eventType) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(topic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Event published successfully: type={}, topic={}, partition={}, offset={}",
                        eventType, topic, 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish event: type={}, topic={}", eventType, topic, ex);
                    metricsService.recordError("KAFKA_PUBLISH_ERROR", "EventPublisher");
                }
            });

        } catch (Exception e) {
            log.error("Error publishing event: type={}", eventType, e);
            metricsService.recordError("KAFKA_PUBLISH_ERROR", "EventPublisher");
        }
    }
}
