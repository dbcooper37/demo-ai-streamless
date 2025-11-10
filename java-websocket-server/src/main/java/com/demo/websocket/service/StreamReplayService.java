package com.demo.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for Replaying Kafka Streams
 * 
 * Enables:
 * - Debugging production issues
 * - Rebuilding indexes/caches
 * - Backfilling new consumers
 * - Testing with production data
 * 
 * Enable with: KAFKA_ENABLED=true
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class StreamReplayService {

    private final ObjectMapper objectMapper;
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public StreamReplayService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.info("StreamReplayService initialized - event replay enabled");
    }

    /**
     * Replay events from a specific timestamp
     * 
     * @param topic Topic to replay from
     * @param fromTimestamp Start timestamp
     * @param eventProcessor Consumer to process each event
     */
    public int replayFromTimestamp(String topic, Instant fromTimestamp, EventProcessor eventProcessor) {
        log.info("Starting replay from timestamp: topic={}, from={}", topic, fromTimestamp);
        
        try (KafkaConsumer<String, String> consumer = createReplayConsumer()) {
            // Get all partitions
            List<TopicPartition> partitions = consumer.partitionsFor(topic)
                .stream()
                .map(p -> new TopicPartition(topic, p.partition()))
                .collect(Collectors.toList());
            
            consumer.assign(partitions);
            
            // Seek to timestamp
            Map<TopicPartition, Long> timestampsToSearch = partitions.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> fromTimestamp.toEpochMilli()));
            
            consumer.offsetsForTimes(timestampsToSearch)
                .forEach((partition, offsetAndTimestamp) -> {
                    if (offsetAndTimestamp != null) {
                        consumer.seek(partition, offsetAndTimestamp.offset());
                        log.info("Partition {} seeking to offset {}", 
                            partition.partition(), offsetAndTimestamp.offset());
                    } else {
                        consumer.seekToBeginning(List.of(partition));
                        log.warn("Timestamp not found for partition {}, seeking to beginning", 
                            partition.partition());
                    }
                });
            
            // Process events
            int processedCount = 0;
            long lastLogTime = System.currentTimeMillis();
            
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                
                if (records.isEmpty()) {
                    log.info("No more records to replay");
                    break;
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
                        event.put("__replay", true);  // Mark as replay
                        event.put("__original_offset", record.offset());
                        event.put("__original_partition", record.partition());
                        
                        eventProcessor.process(event);
                        processedCount++;
                        
                        // Log progress every 10 seconds
                        long now = System.currentTimeMillis();
                        if (now - lastLogTime > 10000) {
                            log.info("Replay progress: {} events processed", processedCount);
                            lastLogTime = now;
                        }
                        
                    } catch (Exception e) {
                        log.error("Error processing replay event: offset={}", record.offset(), e);
                    }
                }
            }
            
            log.info("Replay completed: {} events processed from topic {}", processedCount, topic);
            return processedCount;
            
        } catch (Exception e) {
            log.error("Replay failed for topic: {}", topic, e);
            throw new RuntimeException("Failed to replay stream", e);
        }
    }

    /**
     * Replay events by offset range
     */
    public int replayFromOffset(String topic, int partition, long fromOffset, long toOffset, 
                                EventProcessor eventProcessor) {
        log.info("Starting replay from offset: topic={}, partition={}, from={}, to={}", 
            topic, partition, fromOffset, toOffset);
        
        try (KafkaConsumer<String, String> consumer = createReplayConsumer()) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, fromOffset);
            
            int processedCount = 0;
            
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                
                for (ConsumerRecord<String, String> record : records) {
                    if (record.offset() >= toOffset) {
                        log.info("Reached end offset: {}", toOffset);
                        return processedCount;
                    }
                    
                    try {
                        Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
                        event.put("__replay", true);
                        
                        eventProcessor.process(event);
                        processedCount++;
                        
                    } catch (Exception e) {
                        log.error("Error processing replay event: offset={}", record.offset(), e);
                    }
                }
                
                if (records.isEmpty()) {
                    break;
                }
            }
            
            log.info("Replay completed: {} events processed", processedCount);
            return processedCount;
            
        } catch (Exception e) {
            log.error("Replay failed", e);
            throw new RuntimeException("Failed to replay stream", e);
        }
    }

    /**
     * Replay specific session events (for debugging)
     */
    public List<Map<String, Object>> replaySession(String sessionId) {
        log.info("Replaying session for debug: sessionId={}", sessionId);
        
        List<Map<String, Object>> sessionEvents = new ArrayList<>();
        
        try (KafkaConsumer<String, String> consumer = createReplayConsumer()) {
            // Search in stream-events topic
            List<TopicPartition> partitions = consumer.partitionsFor("stream-events")
                .stream()
                .map(p -> new TopicPartition("stream-events", p.partition()))
                .collect(Collectors.toList());
            
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                
                if (records.isEmpty()) break;
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
                        
                        if (sessionId.equals(event.get("sessionId"))) {
                            event.put("__offset", record.offset());
                            event.put("__partition", record.partition());
                            sessionEvents.add(event);
                        }
                        
                    } catch (Exception e) {
                        log.error("Error parsing event", e);
                    }
                }
            }
            
            // Sort by timestamp
            sessionEvents.sort(Comparator.comparing(e -> (String) e.get("timestamp")));
            
            log.info("Session replay completed: {} events found for sessionId={}", 
                sessionEvents.size(), sessionId);
            
            return sessionEvents;
            
        } catch (Exception e) {
            log.error("Session replay failed", e);
            throw new RuntimeException("Failed to replay session", e);
        }
    }

    /**
     * Create a consumer for replay (separate from main consumers)
     */
    private KafkaConsumer<String, String> createReplayConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "replay-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return new KafkaConsumer<>(props);
    }

    /**
     * Functional interface for processing replay events
     */
    @FunctionalInterface
    public interface EventProcessor {
        void process(Map<String, Object> event) throws Exception;
    }
}
