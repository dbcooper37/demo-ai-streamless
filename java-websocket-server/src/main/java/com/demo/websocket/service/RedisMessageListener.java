package com.demo.websocket.service;

import com.demo.websocket.handler.ChatWebSocketHandler;
import com.demo.websocket.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RedisMessageListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;
    private final Map<String, ChannelTopic> subscriptions = new ConcurrentHashMap<>();

    private ChatWebSocketHandler webSocketHandler;

    public RedisMessageListener(ObjectMapper objectMapper,
                                @Lazy RedisMessageListenerContainer listenerContainer) {
        this.objectMapper = objectMapper;
        this.listenerContainer = listenerContainer;
    }

    public void setWebSocketHandler(ChatWebSocketHandler handler) {
        this.webSocketHandler = handler;
    }

    /**
     * Subscribe to Redis PubSub channel for a specific session
     */
    public void subscribe(String sessionId, ChatWebSocketHandler handler) {
        if (webSocketHandler == null) {
            webSocketHandler = handler;
        }

        String channelName = "chat:stream:" + sessionId;

        if (!subscriptions.containsKey(sessionId)) {
            ChannelTopic topic = new ChannelTopic(channelName);
            subscriptions.put(sessionId, topic);
            listenerContainer.addMessageListener(this, topic);
            log.info("Subscribed to Redis channel: {}", channelName);
        }
    }

    /**
     * Unsubscribe from Redis PubSub channel for a specific session
     */
    public void unsubscribe(String sessionId) {
        ChannelTopic topic = subscriptions.remove(sessionId);
        if (topic != null) {
            listenerContainer.removeMessageListener(this, topic);
            log.info("Unsubscribed from Redis channel: {}", topic.getTopic());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            log.debug("Received message from Redis channel {}: {}", channel, body);

            // Parse message
            ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);

            // Extract session ID from channel name (chat:stream:sessionId)
            String sessionId = channel.substring("chat:stream:".length());

            // Broadcast to WebSocket clients
            if (webSocketHandler != null) {
                webSocketHandler.broadcastToSession(sessionId, chatMessage);
            }

        } catch (Exception e) {
            log.error("Error processing Redis message: {}", e.getMessage(), e);
        }
    }
}
