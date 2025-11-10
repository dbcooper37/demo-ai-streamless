package com.demo.websocket.consumer;

import com.demo.websocket.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka Consumer for Real-time Analytics
 * 
 * Processes events to calculate:
 * - Streaming performance metrics
 * - User engagement statistics
 * - Error rates and patterns
 * - System health indicators
 * 
 * Enable with: KAFKA_ENABLED=true
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class AnalyticsConsumer {

    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    
    // Track session start times for latency calculation
    private final Map<String, Instant> sessionStartTimes = new ConcurrentHashMap<>();

    public AnalyticsConsumer(ObjectMapper objectMapper, MetricsService metricsService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        log.info("AnalyticsConsumer initialized - real-time analytics enabled");
    }

    /**
     * Process stream events for analytics
     */
    @KafkaListener(
        topics = "stream-events",
        groupId = "analytics-consumer",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"  // 3 parallel consumers for high throughput
    )
    public void processStreamEvent(String eventJson, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String eventType = (String) event.get("eventType");
            String sessionId = (String) event.get("sessionId");
            
            switch (eventType) {
                case "SESSION_STARTED" -> handleSessionStarted(event, sessionId);
                case "CHUNK_RECEIVED" -> handleChunkReceived(event);
                case "STREAM_COMPLETED" -> handleStreamCompleted(event, sessionId);
                case "STREAM_ERROR" -> handleStreamError(event);
                case "RECOVERY_ATTEMPT" -> handleRecoveryAttempt(event);
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process analytics event", e);
            throw new RuntimeException("Analytics processing failed", e);
        }
    }

    private void handleSessionStarted(Map<String, Object> event, String sessionId) {
        // Track session start time
        Instant timestamp = Instant.parse((String) event.get("timestamp"));
        sessionStartTimes.put(sessionId, timestamp);
        
        // Increment session counter
        metricsService.incrementCounter("analytics.sessions.started");
        
        log.debug("Session started tracked: sessionId={}", sessionId);
    }

    private void handleChunkReceived(Map<String, Object> event) {
        // Track chunk metrics
        int chunkIndex = ((Number) event.get("chunkIndex")).intValue();
        int contentLength = ((Number) event.get("contentLength")).intValue();
        
        // Record chunk size distribution
        metricsService.recordHistogram("analytics.chunk.size", contentLength);
        
        // Track chunk rate (chunks per second)
        metricsService.incrementCounter("analytics.chunks.received");
        
        log.trace("Chunk metrics recorded: index={}, size={}", chunkIndex, contentLength);
    }

    private void handleStreamCompleted(Map<String, Object> event, String sessionId) {
        int totalChunks = ((Number) event.get("totalChunks")).intValue();
        int contentLength = ((Number) event.get("contentLength")).intValue();
        
        // Calculate total streaming time
        Instant startTime = sessionStartTimes.remove(sessionId);
        if (startTime != null) {
            Duration streamingDuration = Duration.between(
                startTime, 
                Instant.parse((String) event.get("timestamp"))
            );
            
            metricsService.recordTimer("analytics.stream.duration", streamingDuration);
            
            // Calculate words per second
            double wordsPerSecond = (contentLength / 5.0) / streamingDuration.getSeconds();
            metricsService.recordGauge("analytics.stream.words_per_second", wordsPerSecond);
            
            log.info("Stream completed metrics: sessionId={}, duration={}ms, chunks={}, words/s={}", 
                sessionId, streamingDuration.toMillis(), totalChunks, wordsPerSecond);
        }
        
        // Record message length distribution
        metricsService.recordHistogram("analytics.message.length", contentLength);
        metricsService.recordHistogram("analytics.message.chunks", totalChunks);
        
        // Increment completion counter
        metricsService.incrementCounter("analytics.streams.completed");
    }

    private void handleStreamError(Map<String, Object> event) {
        String error = (String) event.get("error");
        
        // Increment error counter by type
        metricsService.incrementCounter("analytics.errors.stream", 
            Map.of("error_type", extractErrorType(error)));
        
        log.warn("Stream error tracked: error={}", error);
    }

    private void handleRecoveryAttempt(Map<String, Object> event) {
        boolean success = (Boolean) event.get("success");
        int fromIndex = ((Number) event.get("fromIndex")).intValue();
        
        // Track recovery success rate
        if (success) {
            metricsService.incrementCounter("analytics.recovery.success");
        } else {
            metricsService.incrementCounter("analytics.recovery.failed");
        }
        
        log.info("Recovery attempt tracked: success={}, fromIndex={}", success, fromIndex);
    }

    private String extractErrorType(String error) {
        if (error == null) return "unknown";
        if (error.contains("timeout")) return "timeout";
        if (error.contains("connection")) return "connection";
        if (error.contains("rate limit")) return "rate_limit";
        return "other";
    }
}
