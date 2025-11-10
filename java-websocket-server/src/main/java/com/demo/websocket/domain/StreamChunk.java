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
