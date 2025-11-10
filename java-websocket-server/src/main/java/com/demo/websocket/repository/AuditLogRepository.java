package com.demo.websocket.repository;

import com.demo.websocket.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Audit Logs
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /**
     * Find audit logs by event type
     */
    List<AuditLog> findByEventType(String eventType);

    /**
     * Find audit logs by user ID
     */
    List<AuditLog> findByUserId(String userId);

    /**
     * Find audit logs by conversation ID
     */
    List<AuditLog> findByConversationId(String conversationId);

    /**
     * Find audit logs by session ID
     */
    List<AuditLog> findBySessionId(String sessionId);

    /**
     * Find audit logs within time range
     */
    List<AuditLog> findByTimestampBetween(Instant start, Instant end);

    /**
     * Find audit logs by user and time range
     */
    List<AuditLog> findByUserIdAndTimestampBetween(String userId, Instant start, Instant end);

    /**
     * Find error events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventType LIKE '%ERROR%' ORDER BY a.timestamp DESC")
    List<AuditLog> findErrorEvents();

    /**
     * Count events by type
     */
    @Query("SELECT a.eventType, COUNT(a) FROM AuditLog a GROUP BY a.eventType")
    List<Object[]> countByEventType();

    /**
     * Find recent audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentLogs(Instant since);
}
