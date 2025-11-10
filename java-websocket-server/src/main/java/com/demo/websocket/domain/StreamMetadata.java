package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamMetadata {
    private String model;
    private int tokenCount;
    private Duration latency;
    private String aiRequestId;
    private Map<String, Object> customMetadata;
}
