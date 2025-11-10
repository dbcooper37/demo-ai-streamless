package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.Message;
import com.demo.websocket.domain.StreamChunk;

/**
 * Callback interface for streaming events
 */
public interface StreamCallback {
    void onChunk(StreamChunk chunk);
    void onComplete(Message message);
    void onError(Throwable error);
}
