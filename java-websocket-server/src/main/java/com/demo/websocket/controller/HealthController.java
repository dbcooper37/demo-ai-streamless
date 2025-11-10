package com.demo.websocket.controller;

import com.demo.websocket.infrastructure.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/health")
public class HealthController {

    private final SessionManager sessionManager;
    private final RedissonClient redissonClient;
    
    @Value("${NODE_ID:unknown}")
    private String nodeId;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    private final Instant startTime = Instant.now();

    public HealthController(SessionManager sessionManager,
                           RedissonClient redissonClient) {
        this.sessionManager = sessionManager;
        this.redissonClient = redissonClient;
    }

    /**
     * Basic health check
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", Instant.now().toString());
            health.put("nodeId", nodeId);
            health.put("port", serverPort);
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "DOWN",
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Detailed distributed system health check
     */
    @GetMapping("/distributed")
    public ResponseEntity<Map<String, Object>> distributedHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // Node information
            health.put("nodeId", nodeId);
            health.put("port", serverPort);
            health.put("uptime", java.time.Duration.between(startTime, Instant.now()).getSeconds());
            health.put("timestamp", Instant.now().toString());
            
            // Redis connectivity
            boolean redisHealthy = checkRedisHealth();
            health.put("redis", Map.of(
                    "status", redisHealthy ? "UP" : "DOWN",
                    "connected", redisHealthy
            ));
            
            // Session statistics
            int localSessions = sessionManager.getActiveSessionCount();
            int distributedSessions = getDistributedSessionCount();
            
            health.put("sessions", Map.of(
                    "local", localSessions,
                    "distributed", distributedSessions,
                    "health", localSessions <= distributedSessions ? "HEALTHY" : "WARNING"
            ));
            
            // Overall status
            String status = redisHealthy ? "UP" : "DEGRADED";
            health.put("status", status);
            
            HttpStatus httpStatus = redisHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(httpStatus).body(health);
            
        } catch (Exception e) {
            log.error("Distributed health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "DOWN",
                            "error", e.getMessage(),
                            "nodeId", nodeId
                    ));
        }
    }

    /**
     * Readiness probe for Kubernetes
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            boolean redisHealthy = checkRedisHealth();
            
            if (redisHealthy) {
                return ResponseEntity.ok(Map.of(
                        "status", "READY",
                        "nodeId", nodeId
                ));
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "status", "NOT_READY",
                                "reason", "Redis not available",
                                "nodeId", nodeId
                        ));
            }
        } catch (Exception e) {
            log.error("Readiness check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "NOT_READY",
                            "reason", e.getMessage(),
                            "nodeId", nodeId
                    ));
        }
    }

    /**
     * Liveness probe for Kubernetes
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of(
                "status", "ALIVE",
                "nodeId", nodeId,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Get node statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Node info
            stats.put("nodeId", nodeId);
            stats.put("port", serverPort);
            stats.put("uptime", java.time.Duration.between(startTime, Instant.now()).getSeconds());
            
            // Runtime stats
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            stats.put("memory", Map.of(
                    "total", totalMemory / (1024 * 1024) + " MB",
                    "used", usedMemory / (1024 * 1024) + " MB",
                    "free", freeMemory / (1024 * 1024) + " MB",
                    "usage", String.format("%.2f%%", (usedMemory * 100.0) / totalMemory)
            ));
            
            // Thread info
            stats.put("threads", Map.of(
                    "active", Thread.activeCount(),
                    "peak", Thread.getAllStackTraces().size()
            ));
            
            // Session stats
            stats.put("sessions", Map.of(
                    "local", sessionManager.getActiveSessionCount(),
                    "distributed", getDistributedSessionCount()
            ));
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", e.getMessage(),
                            "nodeId", nodeId
                    ));
        }
    }

    /**
     * Check Redis connectivity
     */
    private boolean checkRedisHealth() {
        try {
            // Ping Redis
            redissonClient.getKeys().count();
            return true;
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get distributed session count from Redis
     */
    private int getDistributedSessionCount() {
        try {
            return redissonClient.getMap("sessions:active").size();
        } catch (Exception e) {
            log.error("Failed to get distributed session count", e);
            return -1;
        }
    }
}
