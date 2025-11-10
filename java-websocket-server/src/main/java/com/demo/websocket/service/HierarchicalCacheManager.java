package com.demo.websocket.service;

import com.demo.websocket.domain.ChatSession;
import com.demo.websocket.repository.ChatSessionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hierarchical Caching Strategy with Cache-Aside Pattern
 *
 * Cache Levels:
 * L1: Caffeine (Local In-Memory) - Ultra-low latency (~1μs)
 * L2: Redis (Distributed) - Low latency (~1ms)
 * L3: Database (Source of Truth) - Higher latency (~10-50ms)
 *
 * Cache-Aside Pattern: Read from cache, if miss then read from DB and populate cache
 * Write-Through: Write to cache and DB simultaneously
 */
@Service
@Slf4j
public class HierarchicalCacheManager {

    private final RedisTemplate<String, ChatSession> redisTemplate;
    private final ChatSessionRepository chatSessionRepository;
    private final MetricsService metricsService;
    
    // L1 Cache: Caffeine (local in-memory)
    private final Cache<String, ChatSession> l1Cache;
    
    // Stats tracking
    private final ScheduledExecutorService statsExecutor;

    public HierarchicalCacheManager(
            @Qualifier("sessionRedisTemplate") RedisTemplate<String, ChatSession> redisTemplate,
            ChatSessionRepository chatSessionRepository,
            MetricsService metricsService) {
        this.redisTemplate = redisTemplate;
        this.chatSessionRepository = chatSessionRepository;
        this.metricsService = metricsService;
        
        // Initialize L1 cache with optimal settings
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(10_000)  // Max 10k sessions in memory
            .expireAfterWrite(Duration.ofMinutes(5))  // Evict after 5 min
            .expireAfterAccess(Duration.ofMinutes(2))  // Evict if not accessed for 2 min
            .recordStats()  // Enable statistics
            .build();
        
        // Start stats reporting
        this.statsExecutor = Executors.newSingleThreadScheduledExecutor();
        startStatsReporting();
    }

    /**
     * Get session from cache hierarchy with Database fallback (Cache-Aside Pattern)
     * L1 → L2 → L3 (Database)
     */
    public Optional<ChatSession> get(String sessionId) {
        // Try L1 cache first
        ChatSession session = l1Cache.getIfPresent(sessionId);
        if (session != null) {
            metricsService.recordCacheHit("L1");
            log.debug("L1 cache hit: sessionId={}", sessionId);
            return Optional.of(session);
        }
        metricsService.recordCacheMiss("L1");

        // Try L2 cache (Redis)
        String redisKey = getRedisKey(sessionId);
        session = redisTemplate.opsForValue().get(redisKey);
        if (session != null) {
            metricsService.recordCacheHit("L2");
            log.debug("L2 cache hit: sessionId={}", sessionId);
            
            // Populate L1 cache
            l1Cache.put(sessionId, session);
            return Optional.of(session);
        }
        metricsService.recordCacheMiss("L2");

        // L3: Query from Database (Cache-Aside Pattern)
        log.debug("Cache miss, querying database for sessionId={}", sessionId);
        Optional<ChatSession> dbSession = chatSessionRepository.findBySessionId(sessionId);
        
        if (dbSession.isPresent()) {
            metricsService.recordCacheHit("L3_DB");
            log.debug("Database hit: sessionId={}", sessionId);
            
            // Populate cache hierarchy (write-back to cache)
            ChatSession foundSession = dbSession.get();
            l1Cache.put(sessionId, foundSession);
            redisTemplate.opsForValue().set(redisKey, foundSession, Duration.ofMinutes(10));
            
            return dbSession;
        }

        metricsService.recordCacheMiss("L3_DB");
        log.debug("Complete miss (L1+L2+DB) for sessionId={}", sessionId);
        return Optional.empty();
    }

    /**
     * Put session into cache hierarchy and database (write-through)
     */
    public void put(String sessionId, ChatSession session) {
        // Write to L1 cache
        l1Cache.put(sessionId, session);
        
        // Write to L2 cache (Redis) with TTL
        String redisKey = getRedisKey(sessionId);
        redisTemplate.opsForValue().set(redisKey, session, Duration.ofMinutes(10));
        
        // Write to L3 (Database) - Write-Through Pattern
        try {
            chatSessionRepository.save(session);
            log.debug("Saved session to database: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Failed to save session to database: sessionId={}", sessionId, e);
            // Continue even if DB write fails - cache still has the data
        }
        
        log.debug("Cached and persisted session: sessionId={}", sessionId);
    }

    /**
     * Update session in cache hierarchy
     */
    public void update(String sessionId, ChatSession session) {
        put(sessionId, session);  // Same as put for write-through
    }

    /**
     * Invalidate session from all cache levels (but keep in database)
     */
    public void invalidate(String sessionId) {
        // Remove from L1
        l1Cache.invalidate(sessionId);
        
        // Remove from L2
        String redisKey = getRedisKey(sessionId);
        redisTemplate.delete(redisKey);
        
        // Note: We don't delete from database - it's the source of truth
        // Database deletion should be done explicitly if needed
        
        log.debug("Invalidated cache for session: sessionId={}", sessionId);
    }
    
    /**
     * Delete session from cache and database
     */
    public void delete(String sessionId) {
        // Remove from cache hierarchy
        invalidate(sessionId);
        
        // Delete from database
        try {
            chatSessionRepository.deleteById(sessionId);
            log.debug("Deleted session from database: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Failed to delete session from database: sessionId={}", sessionId, e);
        }
    }

    /**
     * Invalidate all cache entries (use with caution)
     */
    public void invalidateAll() {
        l1Cache.invalidateAll();
        log.warn("Invalidated all L1 cache entries");
    }

    /**
     * Warm up cache with frequently accessed data
     */
    public void warmUp(String sessionId, ChatSession session) {
        put(sessionId, session);
        log.debug("Warmed up cache: sessionId={}", sessionId);
    }

    /**
     * Get cache statistics
     */
    public CacheStats getL1Stats() {
        return l1Cache.stats();
    }

    /**
     * Get cache size
     */
    public long getL1Size() {
        return l1Cache.estimatedSize();
    }

    /**
     * Cleanup expired entries
     */
    public void cleanup() {
        l1Cache.cleanUp();
        log.debug("Cleaned up L1 cache");
    }

    /**
     * Get Redis key for session
     */
    private String getRedisKey(String sessionId) {
        return "session:" + sessionId;
    }

    /**
     * Start periodic stats reporting
     */
    private void startStatsReporting() {
        statsExecutor.scheduleAtFixedRate(() -> {
            try {
                CacheStats stats = getL1Stats();
                log.info("L1 Cache Stats - Size: {}, Hits: {}, Misses: {}, Hit Rate: {:.2f}%",
                    getL1Size(),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.hitRate() * 100);
            } catch (Exception e) {
                log.error("Error reporting cache stats", e);
            }
        }, 1, 5, TimeUnit.MINUTES);
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        statsExecutor.shutdown();
        try {
            if (!statsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                statsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            statsExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
