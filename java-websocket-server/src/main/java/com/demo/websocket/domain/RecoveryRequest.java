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
public class RecoveryRequest {
    private String sessionId;
    private String messageId;
    private Integer lastChunkIndex;
    private Instant clientTimestamp;
}
