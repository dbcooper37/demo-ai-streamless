package com.demo.websocket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Simple Distributed Lock Service (PoC)
 * 
 * Uses Redis SET NX for lightweight distributed locking.
 * 
 * For production, consider using Redisson for:
 * - Advanced lock features (fair locks, read/write locks)
 * - Automatic lease renewal
 * - Lock watchdog
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimpleDistributedLockService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String LOCK_PREFIX = "lock:";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * Acquire a distributed lock
     * 
     * @param key Lock key (e.g., "recovery:session123")
     * @return Lock token if acquired, null otherwise
     */
    public String tryLock(String key) {
        return tryLock(key, DEFAULT_TIMEOUT);
    }
    
    /**
     * Acquire a distributed lock with timeout
     * 
     * @param key Lock key
     * @param timeout Lock expiration time
     * @return Lock token if acquired, null otherwise
     */
    public String tryLock(String key, Duration timeout) {
        String lockKey = LOCK_PREFIX + key;
        String lockValue = UUID.randomUUID().toString();
        
        try {
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, timeout);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("🔒 Lock acquired: key={}, token={}", key, lockValue);
                return lockValue;
            } else {
                log.debug("⏳ Lock not available: key={}", key);
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Error acquiring lock: key={}", key, e);
            return null;
        }
    }
    
    /**
     * Release a distributed lock
     * 
     * @param key Lock key
     * @param token Lock token from tryLock()
     * @return true if released, false otherwise
     */
    public boolean unlock(String key, String token) {
        if (token == null) {
            log.warn("⚠️ Cannot unlock: null token for key={}", key);
            return false;
        }
        
        String lockKey = LOCK_PREFIX + key;
        
        try {
            // Only delete if token matches (prevent releasing other's lock)
            String currentValue = redisTemplate.opsForValue().get(lockKey);
            if (token.equals(currentValue)) {
                redisTemplate.delete(lockKey);
                log.debug("🔓 Lock released: key={}, token={}", key, token);
                return true;
            } else {
                log.warn("⚠️ Lock token mismatch: key={}, expected={}, actual={}", 
                         key, token, currentValue);
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Error releasing lock: key={}, token={}", key, token, e);
            return false;
        }
    }
    
    /**
     * Execute operation with lock
     * 
     * @param key Lock key
     * @param operation Operation to execute while holding lock
     * @param <T> Return type
     * @return Operation result, or null if lock not acquired
     */
    public <T> T executeWithLock(String key, LockOperation<T> operation) {
        return executeWithLock(key, DEFAULT_TIMEOUT, operation);
    }
    
    /**
     * Execute operation with lock and timeout
     * 
     * @param key Lock key
     * @param timeout Lock timeout
     * @param operation Operation to execute
     * @param <T> Return type
     * @return Operation result, or null if lock not acquired
     */
    public <T> T executeWithLock(String key, Duration timeout, LockOperation<T> operation) {
        String token = tryLock(key, timeout);
        if (token == null) {
            log.warn("⚠️ Cannot execute: lock not acquired for key={}", key);
            return null;
        }
        
        try {
            return operation.execute();
        } finally {
            unlock(key, token);
        }
    }
    
    /**
     * Check if lock exists
     */
    public boolean isLocked(String key) {
        String lockKey = LOCK_PREFIX + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
    
    /**
     * Force release lock (use with caution!)
     */
    public void forceUnlock(String key) {
        String lockKey = LOCK_PREFIX + key;
        redisTemplate.delete(lockKey);
        log.warn("⚠️ Force unlocked: key={}", key);
    }
    
    @FunctionalInterface
    public interface LockOperation<T> {
        T execute() throws Exception;
    }
}
