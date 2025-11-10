package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Stream Chunk Domain Model (PoC - POJO only, no JPA)
 * 
 * Stored in Redis for PoC. Can add DB persistence later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String messageId;
    private int index;
    private String content;
    private ChunkType type;
    private Instant timestamp;
    private Map<String, Object> metadata;

    public enum ChunkType {
        TEXT,
        CODE,
        THINKING,
        TOOL_USE,
        CITATION
    }
}
