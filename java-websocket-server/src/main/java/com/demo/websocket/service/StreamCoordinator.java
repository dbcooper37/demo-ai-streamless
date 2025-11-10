package com.demo.websocket.service;

import com.demo.websocket.domain.ChatSession;
import com.demo.websocket.domain.Message;
import com.demo.websocket.domain.StreamChunk;
import com.demo.websocket.infrastructure.PubSubListener;
import com.demo.websocket.infrastructure.RedisPubSubPublisher;
import com.demo.websocket.infrastructure.RedisStreamCache;
import com.demo.websocket.infrastructure.StreamCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stream Coordinator
 * 
 * Coordinates streaming operations across multiple nodes:
 * - Stream lifecycle management
 * - Multi-node synchronization via Redis PubSub
 * - Backpressure handling
 * - Stream recovery
 */
@Service
@Slf4j
public class StreamCoordinator {

    private final RedisStreamCache streamCache;
    private final RedisPubSubPublisher pubSubPublisher;
    private final HierarchicalCacheManager cacheManager;
    private final MetricsService metricsService;
    
    // Active streams tracking
    private final Map<String, StreamContext> activeStreams;
    
    // Backpressure configuration
    private static final int MAX_PENDING_CHUNKS = 1000;
    private static final Duration BACKPRESSURE_DELAY = Duration.ofMillis(10);

    public StreamCoordinator(
            RedisStreamCache streamCache,
            RedisPubSubPublisher pubSubPublisher,
            HierarchicalCacheManager cacheManager,
            MetricsService metricsService) {
        this.streamCache = streamCache;
        this.pubSubPublisher = pubSubPublisher;
        this.cacheManager = cacheManager;
        this.metricsService = metricsService;
        this.activeStreams = new ConcurrentHashMap<>();
    }

    /**
     * Initialize a new streaming session
     */
    public void initializeStream(ChatSession session, StreamCallback callback) {
        String sessionId = session.getSessionId();
        
        log.info("Initializing stream: sessionId={}, messageId={}", 
            sessionId, session.getMessageId());

        // Create stream context
        StreamContext context = new StreamContext(session, callback);
        activeStreams.put(sessionId, context);

        // Initialize in cache
        streamCache.initializeStream(session);
        cacheManager.put(sessionId, session);

        // Subscribe to PubSub for this session
        subscribeToStream(sessionId, context);

        // Update metrics
        metricsService.recordStreamStarted(sessionId);
        context.startTime = Instant.now();

        log.info("Stream initialized: sessionId={}", sessionId);
    }

    /**
     * Process and distribute a stream chunk
     */
    public void processChunk(String sessionId, StreamChunk chunk) {
        StreamContext context = activeStreams.get(sessionId);
        if (context == null) {
            log.warn("No active stream context for sessionId={}", sessionId);
            return;
        }

        try {
            // Check backpressure
            if (context.pendingChunks.get() > MAX_PENDING_CHUNKS) {
                log.warn("Backpressure applied for sessionId={}, pending={}", 
                    sessionId, context.pendingChunks.get());
                Thread.sleep(BACKPRESSURE_DELAY.toMillis());
            }

            // Store chunk in cache
            streamCache.appendChunk(context.session.getMessageId(), chunk);

            // Publish to PubSub for multi-node distribution
            pubSubPublisher.publishChunk(sessionId, chunk);

            // Deliver to local callback
            context.pendingChunks.incrementAndGet();
            context.callback.onChunk(chunk);
            context.pendingChunks.decrementAndGet();

            // Update session
            context.chunkCount.incrementAndGet();
            context.session.setTotalChunks(context.chunkCount.get());
            context.session.setLastActivityTime(Instant.now());
            
            // Update cache
            cacheManager.update(sessionId, context.session);

            log.debug("Chunk processed: sessionId={}, index={}", sessionId, chunk.getIndex());

        } catch (Exception e) {
            log.error("Error processing chunk: sessionId={}", sessionId, e);
            handleStreamError(sessionId, context, e);
        }
    }

    /**
     * Complete a stream
     */
    public void completeStream(String sessionId, Message message) {
        StreamContext context = activeStreams.get(sessionId);
        if (context == null) {
            log.warn("No active stream context for sessionId={}", sessionId);
            return;
        }

        try {
            // Update session status
            context.session.setStatus(ChatSession.SessionStatus.COMPLETED);
            context.session.setTotalChunks(context.chunkCount.get());

            // Mark complete in cache
            streamCache.markComplete(context.session.getMessageId(), Duration.ofMinutes(5));
            cacheManager.update(sessionId, context.session);

            // Publish completion event
            pubSubPublisher.publishComplete(sessionId, message);

            // Notify callback
            context.callback.onComplete(message);

            // Calculate metrics
            Duration streamDuration = Duration.between(context.startTime, Instant.now());
            metricsService.recordStreamCompleted(sessionId, streamDuration, context.chunkCount.get());

            log.info("Stream completed: sessionId={}, chunks={}, duration={}ms",
                sessionId, context.chunkCount.get(), streamDuration.toMillis());

        } catch (Exception e) {
            log.error("Error completing stream: sessionId={}", sessionId, e);
            handleStreamError(sessionId, context, e);
        } finally {
            // Cleanup
            cleanup(sessionId);
        }
    }

    /**
     * Handle stream error
     */
    public void handleStreamError(String sessionId, StreamContext context, Throwable error) {
        log.error("Stream error: sessionId={}, messageId={}", 
            sessionId, context.session.getMessageId(), error);

        // Update session status
        context.session.setStatus(ChatSession.SessionStatus.ERROR);
        cacheManager.update(sessionId, context.session);

        // Publish error event
        pubSubPublisher.publishError(sessionId, error.getMessage());

        // Notify callback
        context.callback.onError(error);

        // Update metrics
        metricsService.recordStreamError(sessionId, error.getClass().getSimpleName());

        // Cleanup
        cleanup(sessionId);
    }

    /**
     * Recover an existing stream
     */
    public List<StreamChunk> recoverStream(String sessionId, int fromIndex) {
        log.info("Recovering stream: sessionId={}, fromIndex={}", sessionId, fromIndex);

        try {
            // Get session from cache
            ChatSession session = cacheManager.get(sessionId)
                .orElseGet(() -> streamCache.getSession(sessionId).orElse(null));

            if (session == null) {
                log.warn("Session not found for recovery: sessionId={}", sessionId);
                metricsService.recordRecoveryAttempt(false);
                return List.of();
            }

            // Get chunks from cache
            int totalChunks = session.getTotalChunks();
            List<StreamChunk> chunks = streamCache.getChunks(session.getMessageId(), fromIndex, totalChunks);
            
            metricsService.recordRecoveryAttempt(true);
            log.info("Stream recovered: sessionId={}, chunks={}", sessionId, chunks.size());
            
            return chunks;

        } catch (Exception e) {
            log.error("Error recovering stream: sessionId={}", sessionId, e);
            metricsService.recordRecoveryAttempt(false);
            return List.of();
        }
    }

    /**
     * Subscribe to stream updates via PubSub
     */
    private void subscribeToStream(String sessionId, StreamContext context) {
        pubSubPublisher.subscribe(sessionId, new PubSubListener() {
            @Override
            public void onChunk(StreamChunk chunk) {
                // Chunks are already processed locally, this is for multi-node sync
                log.debug("Received chunk from PubSub: sessionId={}, index={}", 
                    sessionId, chunk.getIndex());
            }

            @Override
            public void onComplete(Message message) {
                log.info("Stream completion received from PubSub: sessionId={}", sessionId);
            }

            @Override
            public void onError(String error) {
                log.error("Error received from PubSub: sessionId={}, error={}", sessionId, error);
            }
        });
    }

    /**
     * Cleanup stream resources
     */
    private void cleanup(String sessionId) {
        activeStreams.remove(sessionId);
        log.debug("Cleaned up stream: sessionId={}", sessionId);
    }

    /**
     * Get active stream count
     */
    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    /**
     * Check if stream is active
     */
    public boolean isStreamActive(String sessionId) {
        return activeStreams.containsKey(sessionId);
    }

    /**
     * Stream context to track state
     */
    private static class StreamContext {
        final ChatSession session;
        final StreamCallback callback;
        final AtomicInteger chunkCount;
        final AtomicInteger pendingChunks;
        Instant startTime;

        StreamContext(ChatSession session, StreamCallback callback) {
            this.session = session;
            this.callback = callback;
            this.chunkCount = new AtomicInteger(0);
            this.pendingChunks = new AtomicInteger(0);
            this.startTime = Instant.now();
        }
    }
}
