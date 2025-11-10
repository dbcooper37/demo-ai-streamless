package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.ChatSession;
import com.demo.websocket.domain.Message;
import com.demo.websocket.domain.StreamChunk;
import com.demo.websocket.domain.StreamMetadata;
import com.demo.websocket.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ChatOrchestrator {

    private final RedisStreamCache streamCache;
    private final MessageRepository messageRepository;
    private final RedisPubSubPublisher pubSubPublisher;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    // Track active streaming sessions
    private final Map<String, StreamingContext> activeStreams = new ConcurrentHashMap<>();

    public ChatOrchestrator(RedisStreamCache streamCache,
                           MessageRepository messageRepository,
                           RedisPubSubPublisher pubSubPublisher,
                           RedisMessageListenerContainer listenerContainer,
                           ObjectMapper objectMapper) {
        this.streamCache = streamCache;
        this.messageRepository = messageRepository;
        this.pubSubPublisher = pubSubPublisher;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    /**
     * Start listening for streaming chat from AI service
     * This subscribes to the legacy chat:stream channel and converts to new format
     */
    public void startStreamingSession(String sessionId,
                                      String userId,
                                      StreamCallback callback) {

        String messageId = UUID.randomUUID().toString();

        // Create chat session
        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .messageId(messageId)
                .conversationId(sessionId)
                .status(ChatSession.SessionStatus.INITIALIZING)
                .startTime(Instant.now())
                .totalChunks(0)
                .metadata(StreamMetadata.builder().build())
                .build();

        // Initialize stream in cache
        streamCache.initializeStream(session);

        // Create streaming context
        StreamingContext context = new StreamingContext(session, callback);
        activeStreams.put(sessionId, context);

        // Subscribe to legacy Redis PubSub channel
        String legacyChannel = "chat:stream:" + sessionId;
        subscribeToLegacyChannel(legacyChannel, context);

        log.info("Started streaming session: sessionId={}, messageId={}", sessionId, messageId);
    }

    /**
     * Subscribe to legacy chat:stream channel and convert messages
     */
    private void subscribeToLegacyChannel(String channel, StreamingContext context) {
        MessageListener listener = (message, pattern) -> {
            try {
                String body = new String(message.getBody());
                ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);

                handleLegacyMessage(chatMessage, context);

            } catch (Exception e) {
                log.error("Error processing legacy message from channel: {}", channel, e);
                context.callback.onError(e);
            }
        };

        ChannelTopic topic = new ChannelTopic(channel);
        listenerContainer.addMessageListener(listener, topic);

        log.info("Subscribed to legacy channel: {}", channel);
    }

    /**
     * Handle legacy chat message and convert to new streaming format
     */
    private void handleLegacyMessage(ChatMessage chatMessage, StreamingContext context) {
        ChatSession session = context.session;

        // Update session status
        if (session.getStatus() == ChatSession.SessionStatus.INITIALIZING) {
            session.setStatus(ChatSession.SessionStatus.STREAMING);
            streamCache.updateSession(session);
        }

        // Create stream chunk
        StreamChunk chunk = StreamChunk.builder()
                .messageId(session.getMessageId())
                .index(context.chunkIndex.getAndIncrement())
                .content(chatMessage.getChunk() != null ? chatMessage.getChunk() : chatMessage.getContent())
                .type(StreamChunk.ChunkType.TEXT)
                .timestamp(Instant.now())
                .build();

        // Append to cache
        streamCache.appendChunk(session.getMessageId(), chunk);

        // Publish to new PubSub format (for multi-node)
        pubSubPublisher.publishChunk(session.getSessionId(), chunk);

        // Callback
        context.callback.onChunk(chunk);

        // Update session
        session.setLastActivityTime(Instant.now());
        session.setTotalChunks(context.chunkIndex.get());
        streamCache.updateSession(session);

        // Check if complete
        if (chatMessage.getIs_complete() != null && chatMessage.getIs_complete()) {
            handleStreamComplete(chatMessage, context);
        }
    }

    /**
     * Handle stream completion
     */
    private void handleStreamComplete(ChatMessage chatMessage, StreamingContext context) {
        ChatSession session = context.session;

        try {
            Duration latency = Duration.between(session.getStartTime(), Instant.now());

            // Update session
            session.setStatus(ChatSession.SessionStatus.COMPLETED);
            session.setTotalChunks(context.chunkIndex.get());

            // Mark stream as complete in cache
            streamCache.markComplete(session.getMessageId(), Duration.ofMinutes(5));

            // Save complete message to repository
            Message message = Message.builder()
                    .id(session.getMessageId())
                    .conversationId(session.getConversationId())
                    .userId(session.getUserId())
                    .role(Message.MessageRole.ASSISTANT)
                    .content(chatMessage.getContent())
                    .status(Message.MessageStatus.COMPLETED)
                    .createdAt(session.getStartTime())
                    .updatedAt(Instant.now())
                    .metadata(com.demo.websocket.domain.MessageMetadata.builder()
                            .tokenCount(0)
                            .build())
                    .build();

            messageRepository.save(message);

            // Publish complete event
            pubSubPublisher.publishComplete(session.getSessionId(), message);

            // Callback
            context.callback.onComplete(message);

            // Cleanup
            activeStreams.remove(session.getSessionId());

            log.info("Stream completed: messageId={}, chunks={}, latency={}ms",
                    session.getMessageId(),
                    context.chunkIndex.get(),
                    latency.toMillis());

        } catch (Exception e) {
            log.error("Error completing stream", e);
            handleStreamError(session, context.callback, e);
        }
    }

    /**
     * Resubscribe to ongoing stream for reconnection
     */
    public void resubscribeStream(String sessionId,
                                 ChatSession session,
                                 StreamCallback callback) {

        log.info("Resubscribing to stream: sessionId={}, messageId={}",
                sessionId, session.getMessageId());

        // Create new streaming context
        StreamingContext context = new StreamingContext(session, callback);
        context.chunkIndex.set(session.getTotalChunks());
        activeStreams.put(sessionId, context);

        // Subscribe to PubSub for remaining chunks
        pubSubPublisher.subscribe(sessionId, new PubSubListener() {
            @Override
            public void onChunk(StreamChunk chunk) {
                callback.onChunk(chunk);
            }

            @Override
            public void onComplete(Message message) {
                callback.onComplete(message);
                activeStreams.remove(sessionId);
            }

            @Override
            public void onError(String error) {
                callback.onError(new RuntimeException(error));
            }
        });
    }

    /**
     * Handle stream error
     */
    private void handleStreamError(ChatSession session,
                                   StreamCallback callback,
                                   Throwable error) {
        log.error("Stream error: sessionId={}, messageId={}",
                session.getSessionId(), session.getMessageId(), error);

        session.setStatus(ChatSession.SessionStatus.ERROR);
        streamCache.updateSession(session);

        // Update message status
        messageRepository.findById(session.getMessageId())
                .ifPresent(msg -> {
                    msg.setStatus(Message.MessageStatus.FAILED);
                    messageRepository.save(msg);
                });

        pubSubPublisher.publishError(session.getSessionId(), error.getMessage());
        callback.onError(error);

        // Cleanup
        activeStreams.remove(session.getSessionId());
    }

    /**
     * Streaming context to track session state
     */
    private static class StreamingContext {
        final ChatSession session;
        final StreamCallback callback;
        final AtomicInteger chunkIndex;
        final Instant startTime;

        StreamingContext(ChatSession session, StreamCallback callback) {
            this.session = session;
            this.callback = callback;
            this.chunkIndex = new AtomicInteger(0);
            this.startTime = Instant.now();
        }
    }
}
