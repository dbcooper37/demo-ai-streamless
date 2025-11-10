package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.ChatSession;
import com.demo.websocket.domain.StreamChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RedisStreamCache {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // Key patterns
    private static final String SESSION_KEY = "stream:session:{sessionId}";
    private static final String CHUNKS_KEY = "stream:chunks:{messageId}";
    private static final String METADATA_KEY = "stream:metadata:{messageId}";
    private static final String LOCK_KEY = "stream:lock:{messageId}";

    // TTL configurations
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Duration CHUNKS_TTL = Duration.ofMinutes(5);

    public RedisStreamCache(StringRedisTemplate redisTemplate,
                           RedissonClient redissonClient,
                           ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Initialize stream session in cache
     * Uses Redis Hash for session data
     */
    public void initializeStream(ChatSession session) {
        String key = SESSION_KEY.replace("{sessionId}", session.getSessionId());

        try {
            Map<String, String> sessionData = new HashMap<>();
            sessionData.put("sessionId", session.getSessionId());
            sessionData.put("messageId", session.getMessageId());
            sessionData.put("userId", session.getUserId());
            sessionData.put("conversationId", session.getConversationId() != null ? session.getConversationId() : "");
            sessionData.put("status", session.getStatus().name());
            sessionData.put("startTime", session.getStartTime().toString());
            sessionData.put("totalChunks", "0");

            redisTemplate.opsForHash().putAll(key, sessionData);
            redisTemplate.expire(key, SESSION_TTL);

            log.debug("Initialized stream session in cache: sessionId={}", session.getSessionId());

        } catch (Exception e) {
            log.error("Failed to initialize stream in cache", e);
            throw new RuntimeException("Stream initialization failed", e);
        }
    }

    /**
     * Append chunk to stream using Redis List
     * Optimized for sequential writes and range reads
     */
    public void appendChunk(String messageId, StreamChunk chunk) {
        String key = CHUNKS_KEY.replace("{messageId}", messageId);

        try {
            // Use distributed lock to ensure chunk ordering
            RLock lock = redissonClient.getLock(LOCK_KEY.replace("{messageId}", messageId));

            try {
                if (lock.tryLock(100, 5000, TimeUnit.MILLISECONDS)) {
                    try {
                        // Serialize chunk
                        String chunkJson = objectMapper.writeValueAsString(chunk);

                        // Append to list (right push for sequential order)
                        redisTemplate.opsForList().rightPush(key, chunkJson);

                        // Set/update TTL
                        redisTemplate.expire(key, CHUNKS_TTL);

                        // Update chunk index for verification
                        String metaKey = METADATA_KEY.replace("{messageId}", messageId);
                        redisTemplate.opsForValue().increment(metaKey + ":lastIndex");

                        log.debug("Appended chunk: messageId={}, index={}", messageId, chunk.getIndex());

                    } finally {
                        lock.unlock();
                    }
                } else {
                    throw new RuntimeException("Failed to acquire lock for chunk append");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while acquiring lock", e);
            }

        } catch (Exception e) {
            log.error("Failed to append chunk: messageId={}, index={}",
                    messageId, chunk.getIndex(), e);
            throw new RuntimeException("Chunk append failed", e);
        }
    }

    /**
     * Get chunks by range using Redis LRANGE
     * Optimized for recovery scenarios
     */
    public List<StreamChunk> getChunks(String messageId, int fromIndex, int toIndex) {
        String key = CHUNKS_KEY.replace("{messageId}", messageId);

        try {
            Instant start = Instant.now();

            // Redis LRANGE is 0-indexed, inclusive on both ends
            List<String> chunkJsons = redisTemplate.opsForList()
                    .range(key, fromIndex, toIndex - 1);

            if (chunkJsons == null || chunkJsons.isEmpty()) {
                log.warn("No chunks found: messageId={}, range=[{},{})",
                        messageId, fromIndex, toIndex);
                return Collections.emptyList();
            }

            List<StreamChunk> chunks = chunkJsons.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, StreamChunk.class);
                        } catch (JsonProcessingException e) {
                            log.error("Failed to deserialize chunk", e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Duration latency = Duration.between(start, Instant.now());
            log.debug("Retrieved {} chunks: messageId={}, range=[{},{}), latency={}ms",
                    chunks.size(), messageId, fromIndex, toIndex, latency.toMillis());

            return chunks;

        } catch (Exception e) {
            log.error("Failed to get chunks: messageId={}", messageId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get all chunks for a completed message
     */
    public List<StreamChunk> getAllChunks(String messageId) {
        String key = CHUNKS_KEY.replace("{messageId}", messageId);

        try {
            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                return Collections.emptyList();
            }

            return getChunks(messageId, 0, size.intValue());

        } catch (Exception e) {
            log.error("Failed to get all chunks: messageId={}", messageId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Mark stream as complete and set TTL
     * Uses Redis MULTI/EXEC for atomicity
     */
    public void markComplete(String messageId, Duration ttl) {
        try {
            redisTemplate.execute(new SessionCallback<Void>() {
                @Override
                public Void execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // Update metadata
                    String metaKey = METADATA_KEY.replace("{messageId}", messageId);
                    operations.opsForValue().set(metaKey + ":status", "COMPLETED");
                    operations.opsForValue().set(metaKey + ":completedAt",
                            Instant.now().toString());
                    operations.expire(metaKey, ttl);

                    // Update chunks TTL
                    String chunksKey = CHUNKS_KEY.replace("{messageId}", messageId);
                    operations.expire(chunksKey, ttl);

                    operations.exec();
                    return null;
                }
            });

            log.info("Marked stream as complete: messageId={}, ttl={}min",
                    messageId, ttl.toMinutes());

        } catch (Exception e) {
            log.error("Failed to mark stream complete: messageId={}", messageId, e);
            throw new RuntimeException("Mark complete failed", e);
        }
    }

    /**
     * Update session in cache
     */
    public void updateSession(ChatSession session) {
        String key = SESSION_KEY.replace("{sessionId}", session.getSessionId());

        try {
            redisTemplate.opsForHash().put(key, "status", session.getStatus().name());
            redisTemplate.opsForHash().put(key, "totalChunks",
                    String.valueOf(session.getTotalChunks()));
            if (session.getLastActivityTime() != null) {
                redisTemplate.opsForHash().put(key, "lastActivityTime",
                        session.getLastActivityTime().toString());
            }

            log.debug("Updated session in cache: sessionId={}", session.getSessionId());

        } catch (Exception e) {
            log.error("Failed to update session: sessionId={}", session.getSessionId(), e);
        }
    }

    /**
     * Get session from cache
     */
    public Optional<ChatSession> getSession(String sessionId) {
        String key = SESSION_KEY.replace("{sessionId}", sessionId);

        try {
            Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(key);

            if (sessionData.isEmpty()) {
                return Optional.empty();
            }

            ChatSession.ChatSessionBuilder builder = ChatSession.builder()
                    .sessionId((String) sessionData.get("sessionId"))
                    .messageId((String) sessionData.get("messageId"))
                    .userId((String) sessionData.get("userId"))
                    .conversationId((String) sessionData.get("conversationId"))
                    .status(ChatSession.SessionStatus.valueOf((String) sessionData.get("status")))
                    .startTime(Instant.parse((String) sessionData.get("startTime")))
                    .totalChunks(Integer.parseInt((String) sessionData.get("totalChunks")));

            if (sessionData.containsKey("lastActivityTime")) {
                builder.lastActivityTime(
                        Instant.parse((String) sessionData.get("lastActivityTime")));
            }

            ChatSession session = builder.build();
            return Optional.of(session);

        } catch (Exception e) {
            log.error("Failed to get session: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }
}
