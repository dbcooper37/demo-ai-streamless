package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketSessionWrapper {
    private String sessionId;
    private WebSocketSession wsSession;
    private String userId;
    private Instant connectedAt;
    private Instant lastHeartbeat;
}
