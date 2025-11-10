package com.demo.websocket.infrastructure;

import com.demo.websocket.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecoveryService {

    private final RedisStreamCache streamCache;
    private final MessageRepository messageRepository;

    public RecoveryService(RedisStreamCache streamCache,
                          MessageRepository messageRepository) {
        this.streamCache = streamCache;
        this.messageRepository = messageRepository;
    }

    /**
     * Recover stream from interruption
     */
    public RecoveryResponse recoverStream(RecoveryRequest request) {

        Instant recoveryStart = Instant.now();

        try {
            // Get session from cache
            Optional<ChatSession> sessionOpt = streamCache.getSession(request.getSessionId());

            if (sessionOpt.isEmpty()) {
                log.warn("Session not found in cache: sessionId={}", request.getSessionId());
                return handleSessionNotFound(request);
            }

            ChatSession session = sessionOpt.get();

            // Check session status
            switch (session.getStatus()) {
                case STREAMING:
                    return recoverStreamingSession(request, session);

                case COMPLETED:
                    return recoverCompletedSession(request, session);

                case ERROR:
                    return RecoveryResponse.builder()
                            .status(RecoveryResponse.RecoveryStatus.ERROR)
                            .session(session)
                            .build();

                default:
                    return RecoveryResponse.builder()
                            .status(RecoveryResponse.RecoveryStatus.NOT_FOUND)
                            .build();
            }

        } finally {
            Duration latency = Duration.between(recoveryStart, Instant.now());
            log.debug("Recovery completed in {}ms", latency.toMillis());
        }
    }

    private RecoveryResponse recoverStreamingSession(RecoveryRequest request,
                                                     ChatSession session) {

        String messageId = session.getMessageId();
        Integer lastChunkIndex = request.getLastChunkIndex() != null
                ? request.getLastChunkIndex()
                : -1;

        log.info("Recovering streaming session: messageId={}, fromIndex={}, totalChunks={}",
                messageId, lastChunkIndex + 1, session.getTotalChunks());

        // Get missing chunks from cache
        List<StreamChunk> missingChunks = streamCache.getChunks(
                messageId,
                lastChunkIndex + 1,
                session.getTotalChunks()
        );

        log.info("Retrieved {} missing chunks for recovery", missingChunks.size());

        return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.RECOVERED)
                .missingChunks(missingChunks)
                .session(session)
                .shouldReconnect(true)
                .build();
    }

    private RecoveryResponse recoverCompletedSession(RecoveryRequest request,
                                                     ChatSession session) {

        log.info("Recovering completed session: messageId={}", session.getMessageId());

        // Try cache first
        List<StreamChunk> cachedChunks = streamCache.getAllChunks(session.getMessageId());

        if (!cachedChunks.isEmpty()) {
            // Reconstruct message from chunks
            String content = cachedChunks.stream()
                    .sorted(Comparator.comparingInt(StreamChunk::getIndex))
                    .map(StreamChunk::getContent)
                    .collect(Collectors.joining());

            Message message = Message.builder()
                    .id(session.getMessageId())
                    .content(content)
                    .status(Message.MessageStatus.COMPLETED)
                    .chunks(cachedChunks)
                    .build();

            log.debug("Recovered message from cache");

            return RecoveryResponse.builder()
                    .status(RecoveryResponse.RecoveryStatus.COMPLETED)
                    .completeMessage(message)
                    .session(session)
                    .shouldReconnect(false)
                    .build();
        }

        // Fallback to database
        Optional<Message> messageOpt = messageRepository.findById(session.getMessageId());

        if (messageOpt.isPresent()) {
            log.debug("Recovered message from repository");

            return RecoveryResponse.builder()
                    .status(RecoveryResponse.RecoveryStatus.COMPLETED)
                    .completeMessage(messageOpt.get())
                    .session(session)
                    .shouldReconnect(false)
                    .build();
        }

        log.warn("Message not found in cache or repository: messageId={}", session.getMessageId());

        return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.NOT_FOUND)
                .build();
    }

    private RecoveryResponse handleSessionNotFound(RecoveryRequest request) {

        // Try to find message in database
        if (request.getMessageId() != null) {
            Optional<Message> messageOpt = messageRepository
                    .findById(request.getMessageId());

            if (messageOpt.isPresent() &&
                    messageOpt.get().getStatus() == Message.MessageStatus.COMPLETED) {

                log.info("Found completed message in repository: messageId={}",
                        request.getMessageId());

                return RecoveryResponse.builder()
                        .status(RecoveryResponse.RecoveryStatus.COMPLETED)
                        .completeMessage(messageOpt.get())
                        .shouldReconnect(false)
                        .build();
            }
        }

        // Check if expired
        Instant requestTime = request.getClientTimestamp();
        if (requestTime != null &&
                Duration.between(requestTime, Instant.now()).toMinutes() > 5) {

            log.warn("Recovery request expired: age={}min",
                    Duration.between(requestTime, Instant.now()).toMinutes());

            return RecoveryResponse.builder()
                    .status(RecoveryResponse.RecoveryStatus.EXPIRED)
                    .build();
        }

        return RecoveryResponse.builder()
                .status(RecoveryResponse.RecoveryStatus.NOT_FOUND)
                .build();
    }
}
