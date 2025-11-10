package com.demo.websocket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Conversation entities
 * Note: Conversation entity needs to be created
 */
@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    /**
     * Find conversation by ID
     */
    Optional<ConversationEntity> findByConversationId(String conversationId);

    /**
     * Find conversations by user ID
     */
    @Query("SELECT c FROM ConversationEntity c " +
           "WHERE c.userId = :userId " +
           "ORDER BY c.updatedAt DESC")
    List<ConversationEntity> findByUserId(@Param("userId") String userId);

    /**
     * Find recent conversations
     */
    @Query("SELECT c FROM ConversationEntity c " +
           "WHERE c.userId = :userId " +
           "AND c.updatedAt > :since " +
           "ORDER BY c.updatedAt DESC")
    List<ConversationEntity> findRecentByUserId(
        @Param("userId") String userId,
        @Param("since") Instant since
    );
}

/**
 * Simple Conversation entity for now
 */
class ConversationEntity {
    private String conversationId;
    private String userId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private int messageCount;

    // Getters and setters will be added
}
