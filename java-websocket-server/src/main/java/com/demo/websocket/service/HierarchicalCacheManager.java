package com.demo.websocket.service;

import com.demo.websocket.domain.ChatSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hierarchical Caching Strategy
 *
 * Cache Levels:
 * L1: Caffeine (Local In-Memory) - Ultra-low latency (~1μs)
 * L2: Redis (Distributed) - Low latency (~1ms)
 * L3: Database (Source of Truth) - Higher latency (~10-50ms)
 *
 * Cache-Aside Pattern with Write-Through optimization
 */
@Service
@Slf4j
public class HierarchicalCacheManager {

    private final RedisTemplate<String, ChatSession> redisTemplate;
    private final MetricsService metricsService;
    
    // L1 Cache: Caffeine (local in-memory)
    private final Cache<String, ChatSession> l1Cache;
    
    // Stats tracking
    private final ScheduledExecutorService statsExecutor;

    public HierarchicalCacheManager(
            RedisTemplate<String, ChatSession> redisTemplate,
            MetricsService metricsService) {
        this.redisTemplate = redisTemplate;
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
     * Get session from cache hierarchy
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

        log.debug("Cache miss for sessionId={}", sessionId);
        return Optional.empty();
    }

    /**
     * Put session into cache hierarchy (write-through)
     */
    public void put(String sessionId, ChatSession session) {
        // Write to L1 cache
        l1Cache.put(sessionId, session);
        
        // Write to L2 cache (Redis) with TTL
        String redisKey = getRedisKey(sessionId);
        redisTemplate.opsForValue().set(redisKey, session, Duration.ofMinutes(10));
        
        log.debug("Cached session: sessionId={}", sessionId);
    }

    /**
     * Update session in cache hierarchy
     */
    public void update(String sessionId, ChatSession session) {
        put(sessionId, session);  // Same as put for write-through
    }

    /**
     * Invalidate session from all cache levels
     */
    public void invalidate(String sessionId) {
        // Remove from L1
        l1Cache.invalidate(sessionId);
        
        // Remove from L2
        String redisKey = getRedisKey(sessionId);
        redisTemplate.delete(redisKey);
        
        log.debug("Invalidated session: sessionId={}", sessionId);
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
