package com.demo.websocket.repository;

import com.demo.websocket.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ChatSession persistence
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    /**
     * Find session by session ID
     */
    Optional<ChatSession> findBySessionId(String sessionId);

    /**
     * Find active sessions by user ID
     */
    @Query("SELECT cs FROM ChatSession cs " +
           "WHERE cs.userId = :userId " +
           "AND cs.status IN ('INITIALIZING', 'STREAMING')")
    List<ChatSession> findActiveByUserId(@Param("userId") String userId);

    /**
     * Find sessions by status
     */
    List<ChatSession> findByStatus(ChatSession.SessionStatus status);

    /**
     * Find expired sessions
     */
    @Query("SELECT cs FROM ChatSession cs " +
           "WHERE cs.lastActivityTime < :threshold " +
           "AND cs.status IN ('INITIALIZING', 'STREAMING')")
    List<ChatSession> findExpiredSessions(@Param("threshold") Instant threshold);

    /**
     * Update session status
     */
    @Modifying
    @Transactional
    @Query("UPDATE ChatSession cs " +
           "SET cs.status = :status, cs.lastActivityTime = :now " +
           "WHERE cs.sessionId = :sessionId")
    int updateSessionStatus(
        @Param("sessionId") String sessionId,
        @Param("status") ChatSession.SessionStatus status,
        @Param("now") Instant now
    );

    /**
     * Delete old sessions (cleanup job)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ChatSession cs " +
           "WHERE cs.lastActivityTime < :threshold " +
           "AND cs.status IN ('COMPLETED', 'ERROR', 'TIMEOUT')")
    int deleteOldSessions(@Param("threshold") Instant threshold);
}
