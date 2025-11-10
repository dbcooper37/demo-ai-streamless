package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryResponse {
    private RecoveryStatus status;
    private List<StreamChunk> missingChunks;
    private Message completeMessage;
    private ChatSession session;
    private boolean shouldReconnect;

    public enum RecoveryStatus {
        RECOVERED,
        COMPLETED,
        NOT_FOUND,
        EXPIRED,
        ERROR
    }
}
