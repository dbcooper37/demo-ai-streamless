package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String messageId;
    
    @Column(name = "chunkIndex", nullable = false)
    private int index;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkType type;
    
    @Column(nullable = false)
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
