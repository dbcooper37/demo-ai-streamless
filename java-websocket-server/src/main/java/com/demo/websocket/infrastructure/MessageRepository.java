package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@Slf4j
public class MessageRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String MESSAGE_KEY = "message:{messageId}";
    private static final Duration MESSAGE_TTL = Duration.ofHours(24);

    public MessageRepository(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Save message to Redis
     */
    public Message save(Message message) {
        String key = MESSAGE_KEY.replace("{messageId}", message.getId());

        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForValue().set(key, json, MESSAGE_TTL);
            log.debug("Saved message: messageId={}", message.getId());
            return message;
        } catch (Exception e) {
            log.error("Failed to save message: messageId={}", message.getId(), e);
            throw new RuntimeException("Failed to save message", e);
        }
    }

    /**
     * Find message by ID
     */
    public Optional<Message> findById(String messageId) {
        String key = MESSAGE_KEY.replace("{messageId}", messageId);

        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }

            Message message = objectMapper.readValue(json, Message.class);
            return Optional.of(message);

        } catch (Exception e) {
            log.error("Failed to find message: messageId={}", messageId, e);
            return Optional.empty();
        }
    }

    /**
     * Delete message
     */
    public void deleteById(String messageId) {
        String key = MESSAGE_KEY.replace("{messageId}", messageId);
        redisTemplate.delete(key);
        log.debug("Deleted message: messageId={}", messageId);
    }
}
