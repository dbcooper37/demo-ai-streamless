package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    private String conversationId;
    private String message;
    private Map<String, Object> context;
    private RequestOptions options;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestOptions {
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Boolean stream;
    }
}
