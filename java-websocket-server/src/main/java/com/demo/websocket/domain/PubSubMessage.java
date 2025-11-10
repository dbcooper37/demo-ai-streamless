package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PubSubMessage {
    private Type type;
    private String sessionId;
    private String messageId;
    private Object data;
    private String error;
    private Instant timestamp;

    public enum Type {
        CHUNK,
        COMPLETE,
        ERROR
    }
}
