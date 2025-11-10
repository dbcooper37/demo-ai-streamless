package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.WebSocketSessionWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SessionManager {

    private final ConcurrentHashMap<String, WebSocketSessionWrapper> activeSessions;
    private final ConcurrentHashMap<String, String> sessionIdToUserId;
    private final RedissonClient redissonClient;
    private final ScheduledExecutorService cleanupExecutor;

    // Distributed session registry using Redis
    private static final String ACTIVE_SESSIONS_KEY = "sessions:active";
    private static final String USER_SESSIONS_KEY = "sessions:user:{userId}";

    public SessionManager(RedissonClient redissonClient) {
        this.activeSessions = new ConcurrentHashMap<>();
        this.sessionIdToUserId = new ConcurrentHashMap<>();
        this.redissonClient = redissonClient;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

        // Schedule heartbeat and cleanup
        cleanupExecutor.scheduleAtFixedRate(this::performHeartbeat, 30, 30, TimeUnit.SECONDS);
        cleanupExecutor.scheduleAtFixedRate(this::cleanupStale, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Register WebSocket session with distributed coordination
     */
    public void registerSession(String sessionId,
                                WebSocketSession wsSession,
                                String userId) {

        try {
            // Create wrapper with metadata
            WebSocketSessionWrapper wrapper = WebSocketSessionWrapper.builder()
                    .sessionId(sessionId)
                    .wsSession(wsSession)
                    .userId(userId)
                    .connectedAt(Instant.now())
                    .lastHeartbeat(Instant.now())
                    .build();

            // Local registration
            activeSessions.put(sessionId, wrapper);
            sessionIdToUserId.put(sessionId, userId);

            // Distributed registration in Redis
            RMap<String, String> activeSessionsMap = redissonClient.getMap(ACTIVE_SESSIONS_KEY);
            activeSessionsMap.put(sessionId, userId);

            // Track user's sessions
            RSet<String> userSessions = redissonClient.getSet(
                    USER_SESSIONS_KEY.replace("{userId}", userId));
            userSessions.add(sessionId);

            // Set expiration for automatic cleanup
            userSessions.expire(Duration.ofMinutes(30));

            log.info("Session registered: sessionId={}, userId={}, total={}",
                    sessionId, userId, activeSessions.size());

        } catch (Exception e) {
            log.error("Failed to register session: sessionId={}", sessionId, e);
            throw new RuntimeException("Session registration failed", e);
        }
    }

    /**
     * Unregister session with cleanup
     */
    public void unregisterSession(String sessionId) {
        try {
            WebSocketSessionWrapper wrapper = activeSessions.remove(sessionId);
            if (wrapper == null) {
                return;
            }

            String userId = sessionIdToUserId.remove(sessionId);

            // Distributed cleanup
            RMap<String, String> activeSessionsMap = redissonClient.getMap(ACTIVE_SESSIONS_KEY);
            activeSessionsMap.remove(sessionId);

            if (userId != null) {
                RSet<String> userSessions = redissonClient.getSet(
                        USER_SESSIONS_KEY.replace("{userId}", userId));
                userSessions.remove(sessionId);
            }

            log.info("Session unregistered: sessionId={}, userId={}, duration={}s",
                    sessionId, userId,
                    Duration.between(wrapper.getConnectedAt(), Instant.now()).getSeconds());

        } catch (Exception e) {
            log.error("Failed to unregister session: sessionId={}", sessionId, e);
        }
    }

    /**
     * Get WebSocket session
     */
    public Optional<WebSocketSession> getSession(String sessionId) {
        WebSocketSessionWrapper wrapper = activeSessions.get(sessionId);
        return Optional.ofNullable(wrapper)
                .map(WebSocketSessionWrapper::getWsSession);
    }

    /**
     * Get session ID from WebSocketSession
     */
    public String getSessionId(WebSocketSession wsSession) {
        return activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().getWsSession().equals(wsSession))
                .map(entry -> entry.getKey())
                .findFirst()
                .orElse(null);
    }

    /**
     * Get user ID for a session
     */
    public String getUserId(String sessionId) {
        return sessionIdToUserId.get(sessionId);
    }

    /**
     * Mark session as having an error
     */
    public void markSessionError(String sessionId) {
        log.warn("Session marked as error: {}", sessionId);
        // Could add error tracking here if needed
    }

    /**
     * Update heartbeat for session
     */
    public void updateHeartbeat(String sessionId) {
        WebSocketSessionWrapper wrapper = activeSessions.get(sessionId);
        if (wrapper != null) {
            wrapper.setLastHeartbeat(Instant.now());
        }
    }

    /**
     * Get all active sessions for a user
     */
    public Set<String> getUserSessions(String userId) {
        RSet<String> userSessions = redissonClient.getSet(
                USER_SESSIONS_KEY.replace("{userId}", userId));
        return new HashSet<>(userSessions.readAll());
    }

    /**
     * Get count of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Perform heartbeat check
     */
    private void performHeartbeat() {
        try {
            Instant timeout = Instant.now().minus(Duration.ofMinutes(5));

            activeSessions.values().removeIf(wrapper -> {
                if (wrapper.getLastHeartbeat().isBefore(timeout)) {
                    log.warn("Session timed out: sessionId={}, lastHeartbeat={}",
                            wrapper.getSessionId(), wrapper.getLastHeartbeat());
                    unregisterSession(wrapper.getSessionId());
                    return true;
                }
                return false;
            });

        } catch (Exception e) {
            log.error("Error during heartbeat check", e);
        }
    }

    /**
     * Clean up stale entries from distributed registry
     */
    private void cleanupStale() {
        try {
            RMap<String, String> activeSessionsMap = redissonClient.getMap(ACTIVE_SESSIONS_KEY);

            Set<String> distributedSessions = new HashSet<>(activeSessionsMap.keySet());
            Set<String> localSessions = activeSessions.keySet();

            // Find sessions in Redis but not in local map
            distributedSessions.removeAll(localSessions);

            if (!distributedSessions.isEmpty()) {
                log.info("Cleaning up {} stale distributed sessions",
                        distributedSessions.size());

                distributedSessions.forEach(activeSessionsMap::remove);
            }

        } catch (Exception e) {
            log.error("Error during stale cleanup", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SessionManager...");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
