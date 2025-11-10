package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.Message;
import com.demo.websocket.domain.PubSubMessage;
import com.demo.websocket.domain.StreamChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class RedisPubSubPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CHUNK_CHANNEL = "stream:channel:{sessionId}:chunk";
    private static final String COMPLETE_CHANNEL = "stream:channel:{sessionId}:complete";
    private static final String ERROR_CHANNEL = "stream:channel:{sessionId}:error";

    public RedisPubSubPublisher(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish chunk to session-specific channel
     * Uses Redis PubSub for real-time delivery across nodes
     */
    public void publishChunk(String sessionId, StreamChunk chunk) {
        String channel = CHUNK_CHANNEL.replace("{sessionId}", sessionId);

        try {
            PubSubMessage message = PubSubMessage.builder()
                    .type(PubSubMessage.Type.CHUNK)
                    .sessionId(sessionId)
                    .messageId(chunk.getMessageId())
                    .data(chunk)
                    .timestamp(Instant.now())
                    .build();

            String payload = objectMapper.writeValueAsString(message);

            Long subscribers = redisTemplate.convertAndSend(channel, payload);

            if (subscribers == 0) {
                log.debug("No active subscribers for session: {}", sessionId);
            }

            log.debug("Published chunk: sessionId={}, index={}, subscribers={}",
                    sessionId, chunk.getIndex(), subscribers);

        } catch (Exception e) {
            log.error("Failed to publish chunk: sessionId={}, index={}",
                    sessionId, chunk.getIndex(), e);
        }
    }

    /**
     * Publish completion event
     */
    public void publishComplete(String sessionId, Message message) {
        String channel = COMPLETE_CHANNEL.replace("{sessionId}", sessionId);

        try {
            PubSubMessage pubSubMessage = PubSubMessage.builder()
                    .type(PubSubMessage.Type.COMPLETE)
                    .sessionId(sessionId)
                    .messageId(message.getId())
                    .data(message)
                    .timestamp(Instant.now())
                    .build();

            String payload = objectMapper.writeValueAsString(pubSubMessage);
            Long subscribers = redisTemplate.convertAndSend(channel, payload);

            log.info("Published complete event: sessionId={}, subscribers={}",
                    sessionId, subscribers);

        } catch (Exception e) {
            log.error("Failed to publish complete: sessionId={}", sessionId, e);
        }
    }

    /**
     * Publish error event
     */
    public void publishError(String sessionId, String error) {
        String channel = ERROR_CHANNEL.replace("{sessionId}", sessionId);

        try {
            PubSubMessage message = PubSubMessage.builder()
                    .type(PubSubMessage.Type.ERROR)
                    .sessionId(sessionId)
                    .error(error)
                    .timestamp(Instant.now())
                    .build();

            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, payload);

            log.warn("Published error event: sessionId={}, error={}", sessionId, error);

        } catch (Exception e) {
            log.error("Failed to publish error: sessionId={}", sessionId, e);
        }
    }

    /**
     * Subscribe to session channels
     * Used for reconnection scenarios
     */
    public void subscribe(String sessionId, PubSubListener listener) {
        String chunkChannel = CHUNK_CHANNEL.replace("{sessionId}", sessionId);
        String completeChannel = COMPLETE_CHANNEL.replace("{sessionId}", sessionId);
        String errorChannel = ERROR_CHANNEL.replace("{sessionId}", sessionId);

        MessageListener messageListener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody());
                PubSubMessage pubSubMessage = objectMapper.readValue(payload,
                        PubSubMessage.class);

                switch (pubSubMessage.getType()) {
                    case CHUNK:
                        StreamChunk chunk = objectMapper.convertValue(
                                pubSubMessage.getData(), StreamChunk.class);
                        listener.onChunk(chunk);
                        break;

                    case COMPLETE:
                        Message msg = objectMapper.convertValue(
                                pubSubMessage.getData(), Message.class);
                        listener.onComplete(msg);
                        break;

                    case ERROR:
                        listener.onError(pubSubMessage.getError());
                        break;
                }

                log.debug("Received PubSub message: type={}, sessionId={}",
                        pubSubMessage.getType(), sessionId);

            } catch (Exception e) {
                log.error("Error processing PubSub message", e);
                listener.onError("Message processing failed: " + e.getMessage());
            }
        };

        // Subscribe to all channels for this session
        redisTemplate.getConnectionFactory()
                .getConnection()
                .subscribe(messageListener,
                        chunkChannel.getBytes(),
                        completeChannel.getBytes(),
                        errorChannel.getBytes());

        log.info("Subscribed to channels for session: {}", sessionId);
    }
}
