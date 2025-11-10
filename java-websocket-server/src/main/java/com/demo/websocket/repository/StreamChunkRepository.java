package com.demo.websocket.repository;

import com.demo.websocket.domain.StreamChunk;
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
 * Repository for StreamChunk persistence
 */
@Repository
public interface StreamChunkRepository extends JpaRepository<StreamChunk, Long> {

    /**
     * Find chunks by message ID and index range
     */
    @Query("SELECT sc FROM StreamChunk sc " +
           "WHERE sc.messageId = :messageId " +
           "AND sc.index >= :fromIndex " +
           "AND sc.index < :toIndex " +
           "ORDER BY sc.index")
    List<StreamChunk> findByMessageIdAndIndexBetween(
        @Param("messageId") String messageId,
        @Param("fromIndex") int fromIndex,
        @Param("toIndex") int toIndex
    );

    /**
     * Find all chunks for a message
     */
    @Query("SELECT sc FROM StreamChunk sc " +
           "WHERE sc.messageId = :messageId " +
           "ORDER BY sc.index")
    List<StreamChunk> findAllByMessageIdOrderByIndex(
        @Param("messageId") String messageId
    );

    /**
     * Get max chunk index for a message
     */
    @Query("SELECT MAX(sc.index) FROM StreamChunk sc " +
           "WHERE sc.messageId = :messageId")
    Optional<Integer> findMaxIndexByMessageId(
        @Param("messageId") String messageId
    );

    /**
     * Count chunks for a message
     */
    @Query("SELECT COUNT(sc) FROM StreamChunk sc " +
           "WHERE sc.messageId = :messageId")
    long countByMessageId(@Param("messageId") String messageId);

    /**
     * Delete old chunks (cleanup job)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StreamChunk sc " +
           "WHERE sc.timestamp < :threshold")
    int deleteByTimestampBefore(@Param("threshold") Instant threshold);
    
    /**
     * Delete chunks by message ID
     */
    @Modifying
    @Transactional
    void deleteByMessageId(String messageId);
}
