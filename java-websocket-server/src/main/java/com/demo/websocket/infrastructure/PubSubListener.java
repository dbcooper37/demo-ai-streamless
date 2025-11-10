package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.Message;
import com.demo.websocket.domain.StreamChunk;

/**
 * Listener interface for PubSub events
 */
public interface PubSubListener {
    void onChunk(StreamChunk chunk);
    void onComplete(Message message);
    void onError(String error);
}
