package com.demo.websocket.service;

import com.demo.websocket.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatHistoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(RedisTemplate<String, String> redisTemplate,
                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Get chat history for a session from Redis
     */
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        String key = "chat:history:" + sessionId;

        try {
            List<String> historyJson = redisTemplate.opsForList().range(key, 0, -1);
            if (historyJson != null) {
                for (String json : historyJson) {
                    ChatMessage message = objectMapper.readValue(json, ChatMessage.class);
                    messages.add(message);
                }
                log.debug("Retrieved {} messages from history for session {}", messages.size(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error retrieving history for session {}: {}", sessionId, e.getMessage());
        }

        return messages;
    }

    /**
     * Clear history for a session
     */
    public void clearHistory(String sessionId) {
        String key = "chat:history:" + sessionId;
        redisTemplate.delete(key);
        log.info("Cleared history for session {}", sessionId);
    }
}
