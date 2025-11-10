package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.*;
import com.demo.websocket.exception.MessageNotFoundException;
import com.demo.websocket.exception.RecoveryException;
import com.demo.websocket.service.MetricsService;
import com.demo.websocket.service.SimpleDistributedLockService;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecoveryService {

    private final RedisStreamCache streamCache;
    private final MessageRepository messageRepository;
    private final MetricsService metricsService;
    private final SimpleDistributedLockService lockService;

    @Value("${recovery.session-ttl-minutes:10}")
    private int sessionTtlMinutes;

    @Value("${recovery.max-chunks-per-request:1000}")
    private int maxChunksPerRequest;

    @Value("${recovery.enable-database-fallback:true}")
    private boolean enableDatabaseFallback;

    public RecoveryService(RedisStreamCache streamCache,
                          MessageRepository messageRepository,
                          MetricsService metricsService,
                          SimpleDistributedLockService lockService) {
        this.streamCache = streamCache;
        this.messageRepository = messageRepository;
        this.metricsService = metricsService;
        this.lockService = lockService;
    }

    /**
     * Main recovery method - handles all recovery scenarios
     */
    @Transactional(readOnly = true)
    public RecoveryResponse recoverStream(RecoveryRequest request) {

        Instant recoveryStart = Instant.now();
        String sessionId = request.getSessionId();
        String messageId = request.getMessageId();

        log.info("Recovery requested: sessionId={}, messageId={}, lastChunk={}",
            sessionId, messageId, request.getLastChunkIndex());

        // Validate request
        ValidationResult validation = validateRecoveryRequest(request);
        if (!validation.isValid()) {
            log.warn("Invalid recovery request: {}", validation.getErrorMessage());
            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.ERROR)
                .build();
        }

        try {
            // Acquire distributed lock to avoid race conditions
            RLock lock = redissonClient.getLock("recovery:lock:" + sessionId);

            try {
                // Try lock with timeout
                if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                    try {
                        return executeRecovery(request, recoveryStart);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    log.warn("Failed to acquire recovery lock: sessionId={}", sessionId);
                    return RecoveryResponse.builder()
                        .status(RecoveryResponse.RecoveryStatus.ERROR)
                        .build();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RecoveryException("Interrupted during recovery", e);
            }

        } catch (Exception e) {
            log.error("Recovery failed: sessionId={}, messageId={}", sessionId, messageId, e);

            metricsService.incrementCounter("recovery.failed",
                Tags.of("error_type", e.getClass().getSimpleName()));

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.ERROR)
                .build();

        } finally {
            Duration latency = Duration.between(recoveryStart, Instant.now());
            metricsService.recordTimer("recovery.latency", latency);

            log.info("Recovery completed: sessionId={}, latency={}ms",
                sessionId, latency.toMillis());
        }
    }

    /**
     * Execute recovery logic with proper error handling
     */
    private RecoveryResponse executeRecovery(RecoveryRequest request, Instant recoveryStart) {

        String sessionId = request.getSessionId();
        String messageId = request.getMessageId();

        // Step 1: Get session from cache
        Optional<ChatSession> sessionOpt = streamCache.getSession(sessionId);

        if (sessionOpt.isEmpty()) {
            log.info("Session not in cache, checking database: sessionId={}", sessionId);
            return handleSessionNotInCache(request);
        }

        ChatSession session = sessionOpt.get();

        // Step 2: Verify session belongs to this message
        if (!session.getMessageId().equals(messageId)) {
            log.warn("Session/message mismatch: sessionId={}, expected={}, actual={}",
                sessionId, messageId, session.getMessageId());

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.ERROR)
                .build();
        }

        // Step 3: Check if session expired
        if (isSessionExpired(session)) {
            log.info("Session expired: sessionId={}, lastActivity={}",
                sessionId, session.getLastActivityTime());

            metricsService.incrementCounter("recovery.expired");

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.EXPIRED)
                .session(session)
                .build();
        }

        // Step 4: Route based on session status
        return switch (session.getStatus()) {
            case STREAMING -> recoverStreamingSession(request, session);
            case COMPLETED -> recoverCompletedSession(request, session);
            case ERROR -> RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.ERROR)
                .session(session)
                .build();
            case TIMEOUT -> RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.ERROR)
                .session(session)
                .build();
            default -> RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.NOT_FOUND)
                .build();
        };
    }

    /**
     * Recover streaming session - Return missing chunks
     */
    private RecoveryResponse recoverStreamingSession(RecoveryRequest request, ChatSession session) {

        String sessionId = session.getSessionId();
        String messageId = session.getMessageId();
        Integer lastChunkIndex = request.getLastChunkIndex() != null
            ? request.getLastChunkIndex()
            : -1;

        log.info("Recovering streaming session: messageId={}, fromIndex={}, totalChunks={}",
            messageId, lastChunkIndex + 1, session.getTotalChunks());

        try {
            // Calculate missing chunk range
            int fromIndex = lastChunkIndex + 1;
            int toIndex = session.getTotalChunks();

            // Validate chunk range
            if (fromIndex > toIndex) {
                log.warn("Invalid chunk range: from={}, to={}", fromIndex, toIndex);
                return RecoveryResponse.builder()
                    .status(RecoveryResponse.RecoveryStatus.ERROR)
                    .build();
            }

            // Check if too many chunks requested
            int chunksToRecover = toIndex - fromIndex;
            if (chunksToRecover > maxChunksPerRequest) {
                log.warn("Too many chunks requested: requested={}, max={}",
                    chunksToRecover, maxChunksPerRequest);

                // Return partial recovery
                toIndex = fromIndex + maxChunksPerRequest;
            }

            // Get missing chunks from cache (primary source)
            List<StreamChunk> missingChunks = streamCache.getChunks(messageId, fromIndex, toIndex);

            // If cache miss and database fallback enabled, try database
            if (missingChunks.isEmpty() && enableDatabaseFallback) {
                log.info("Cache miss, falling back to database: messageId={}", messageId);
                // TODO: Implement database fallback when StreamChunkRepository is available
                metricsService.incrementCounter("recovery.database_fallback");
            }

            // Verify chunk continuity
            if (!missingChunks.isEmpty()) {
                validateChunkContinuity(missingChunks, fromIndex);
            }

            log.info("Retrieved {} missing chunks: messageId={}, range=[{},{})",
                missingChunks.size(), messageId, fromIndex, toIndex);

            metricsService.incrementCounter("recovery.streaming.success",
                Tags.of("chunks", String.valueOf(missingChunks.size())));

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.RECOVERED)
                .missingChunks(missingChunks)
                .session(session)
                .shouldReconnect(true)
                .build();

        } catch (Exception e) {
            log.error("Error recovering streaming session: messageId={}", messageId, e);

            metricsService.incrementCounter("recovery.streaming.error");

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.ERROR)
                .session(session)
                .build();
        }
    }

    /**
     * Recover completed session - Return full message
     */
    private RecoveryResponse recoverCompletedSession(RecoveryRequest request, ChatSession session) {

        String messageId = session.getMessageId();

        log.info("Recovering completed session: messageId={}", messageId);

        try {
            // Strategy 1: Try to get from cache first
            List<StreamChunk> cachedChunks = streamCache.getAllChunks(messageId);

            if (!cachedChunks.isEmpty()) {
                // Reconstruct message from chunks
                Message message = reconstructMessageFromChunks(messageId, cachedChunks, session);

                log.info("Recovered completed message from cache: messageId={}, chunks={}",
                    messageId, cachedChunks.size());

                metricsService.incrementCounter("recovery.completed.cache");

                return RecoveryResponse.builder()
                    .status(RecoveryResponse.RecoveryStatus.COMPLETED)
                    .completeMessage(message)
                    .session(session)
                    .shouldReconnect(false)
                    .build();
            }

            // Strategy 2: Get from database
            Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException("Message not found: " + messageId));

            log.info("Recovered completed message from database: messageId={}", messageId);

            metricsService.incrementCounter("recovery.completed.database");

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.COMPLETED)
                .completeMessage(message)
                .session(session)
                .shouldReconnect(false)
                .build();

        } catch (MessageNotFoundException e) {
            log.error("Message not found: messageId={}", messageId);

            metricsService.incrementCounter("recovery.message_not_found");

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.NOT_FOUND)
                .build();

        } catch (Exception e) {
            log.error("Error recovering completed session: messageId={}", messageId, e);

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.ERROR)
                .build();
        }
    }

    /**
     * Handle session not in cache - fallback to database
     */
    private RecoveryResponse handleSessionNotInCache(RecoveryRequest request) {

        String messageId = request.getMessageId();

        if (messageId == null) {
            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.NOT_FOUND)
                .build();
        }

        // Try to find message in database
        Optional<Message> messageOpt = messageRepository.findById(messageId);

        if (messageOpt.isPresent() && messageOpt.get().getStatus() == Message.MessageStatus.COMPLETED) {
            log.info("Found completed message in database: messageId={}", messageId);

            metricsService.incrementCounter("recovery.cache_miss.database_hit");

            return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.COMPLETED)
                .completeMessage(messageOpt.get())
                .shouldReconnect(false)
                .build();
        }

        // Check if request is too old (expired)
        Instant requestTime = request.getClientTimestamp();
        if (requestTime != null) {
            Duration age = Duration.between(requestTime, Instant.now());
            if (age.toMinutes() > sessionTtlMinutes) {
                log.warn("Recovery request expired: age={}min", age.toMinutes());

                metricsService.incrementCounter("recovery.expired");

                return RecoveryResponse.builder()
                    .status(RecoveryResponse.RecoveryStatus.EXPIRED)
                    .build();
            }
        }

        metricsService.incrementCounter("recovery.not_found");

        return RecoveryResponse.builder()
            .status(RecoveryResponse.RecoveryStatus.NOT_FOUND)
            .build();
    }

    /**
     * Validate recovery request
     */
    private ValidationResult validateRecoveryRequest(RecoveryRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            errors.add("Session ID is required");
        }

        if (request.getMessageId() == null || request.getMessageId().isEmpty()) {
            errors.add("Message ID is required");
        }

        if (request.getLastChunkIndex() != null && request.getLastChunkIndex() < -1) {
            errors.add("Invalid last chunk index");
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * Check if session is expired
     */
    private boolean isSessionExpired(ChatSession session) {
        if (session.getLastActivityTime() == null) {
            return false;
        }

        Duration timeSinceLastActivity = Duration.between(
            session.getLastActivityTime(),
            Instant.now()
        );

        return timeSinceLastActivity.toMinutes() > sessionTtlMinutes;
    }

    /**
     * Validate chunk continuity
     */
    private void validateChunkContinuity(List<StreamChunk> chunks, int expectedStartIndex) {
        if (chunks.isEmpty()) {
            return;
        }

        // Sort chunks by index
        List<StreamChunk> sortedChunks = chunks.stream()
            .sorted(Comparator.comparingInt(StreamChunk::getIndex))
            .collect(Collectors.toList());

        // Check first chunk
        if (sortedChunks.get(0).getIndex() != expectedStartIndex) {
            log.warn("Chunk continuity broken: expected first index={}, actual={}",
                expectedStartIndex, sortedChunks.get(0).getIndex());
        }

        // Check for gaps
        for (int i = 1; i < sortedChunks.size(); i++) {
            int prevIndex = sortedChunks.get(i - 1).getIndex();
            int currIndex = sortedChunks.get(i).getIndex();

            if (currIndex != prevIndex + 1) {
                log.warn("Gap detected in chunks: after index {} jumps to {}",
                    prevIndex, currIndex);
            }
        }
    }

    /**
     * Reconstruct message from chunks
     */
    private Message reconstructMessageFromChunks(String messageId,
                                                 List<StreamChunk> chunks,
                                                 ChatSession session) {
        // Sort and concatenate chunks
        String content = chunks.stream()
            .sorted(Comparator.comparingInt(StreamChunk::getIndex))
            .map(StreamChunk::getContent)
            .collect(Collectors.joining());

        return Message.builder()
            .id(messageId)
            .conversationId(session.getConversationId())
            .userId(session.getUserId())
            .role(Message.MessageRole.ASSISTANT)
            .content(content)
            .status(Message.MessageStatus.COMPLETED)
            .createdAt(session.getStartTime())
            .updatedAt(Instant.now())
            .metadata(MessageMetadata.builder()
                .tokenCount(0)
                .build())
            .build();
    }
}
