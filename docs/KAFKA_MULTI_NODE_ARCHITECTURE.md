# 7.3 Kafka trong Multi-Node Chat Stream Architecture

## Tổng Quan

Kafka đóng vai trò then chốt trong kiến trúc Multi-Node để đảm bảo:
- **Consistency**: Dữ liệu đồng bộ giữa các nodes
- **Reliability**: Không mất message khi node crash
- **Scalability**: Xử lý hàng nghìn connections đồng thời
- **Observability**: Theo dõi toàn bộ lifecycle của messages

```
┌─────────────────────────────────────────────────────────────┐
│                  Multi-Node Architecture                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Node 1          Node 2          Node 3          Node N      │
│    │               │               │               │         │
│    └───────────────┴───────────────┴───────────────┘         │
│                           │                                   │
│                           ▼                                   │
│                  ┌─────────────────┐                         │
│                  │  Kafka Cluster  │                         │
│                  │  ┌───────────┐  │                         │
│                  │  │ chat-     │  │  Event Sourcing        │
│                  │  │ events    │  │  Message Replay        │
│                  │  ├───────────┤  │  Cross-Node Sync       │
│                  │  │ stream-   │  │  Analytics             │
│                  │  │ events    │  │                         │
│                  │  └───────────┘  │                         │
│                  └─────────────────┘                         │
│                           │                                   │
│                           ▼                                   │
│                  ┌─────────────────┐                         │
│                  │  Redis Cluster  │                         │
│                  │  (Cache Layer)  │                         │
│                  └─────────────────┘                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. Event Sourcing & Audit Trail

### 1.1 Concept

**Event Sourcing** = Store events, not state
- Thay vì lưu state hiện tại, lưu tất cả các events đã xảy ra
- State có thể được tái tạo bằng cách replay events
- Kafka log = immutable event store

### 1.2 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Event Sourcing Flow                        │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Chat Action          Event Published         Event Stored   │
│       │                     │                      │          │
│       ▼                     ▼                      ▼          │
│  ┌─────────┐         ┌──────────┐         ┌──────────┐      │
│  │ User    │         │  Kafka   │         │  Topic   │      │
│  │ sends   │────────▶│ Producer │────────▶│ Partition│      │
│  │ message │         │          │         │  0,1,2,3 │      │
│  └─────────┘         └──────────┘         └──────────┘      │
│                                                   │           │
│                                                   │ Replicated│
│                                                   ▼           │
│                                            ┌──────────┐      │
│                                            │  Kafka   │      │
│                                            │  Log     │      │
│                                            │ (Durable)│      │
│                                            └──────────┘      │
│                                                               │
│  Use Cases:                                                  │
│  1. Audit: Who did what, when?                              │
│  2. Compliance: Regulatory requirements                      │
│  3. Debug: Reproduce production issues                       │
│  4. Analytics: User behavior analysis                        │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 1.3 Implementation

#### Event Types

```java
// 1. SESSION_STARTED - Track khi user bắt đầu chat
{
  "eventType": "SESSION_STARTED",
  "timestamp": "2024-01-15T10:30:00Z",
  "sessionId": "session_123",
  "userId": "user_456",
  "messageId": "msg_789",
  "conversationId": "conv_abc",
  "metadata": {
    "nodeId": "ws-node-1",
    "clientIp": "192.168.1.100",
    "userAgent": "Chrome/120.0"
  }
}

// 2. CHUNK_RECEIVED - Track mỗi streaming chunk
{
  "eventType": "CHUNK_RECEIVED",
  "timestamp": "2024-01-15T10:30:01.234Z",
  "sessionId": "session_123",
  "messageId": "msg_789",
  "chunkIndex": 5,
  "contentLength": 42,
  "latencyMs": 150,
  "metadata": {
    "nodeId": "ws-node-1"
  }
}

// 3. STREAM_COMPLETED - Track khi message hoàn thành
{
  "eventType": "STREAM_COMPLETED",
  "timestamp": "2024-01-15T10:30:05Z",
  "sessionId": "session_123",
  "messageId": "msg_789",
  "conversationId": "conv_abc",
  "totalChunks": 50,
  "contentLength": 2048,
  "totalLatencyMs": 5000,
  "metadata": {
    "nodeId": "ws-node-1",
    "tokensUsed": 512
  }
}

// 4. STREAM_ERROR - Track errors
{
  "eventType": "STREAM_ERROR",
  "timestamp": "2024-01-15T10:30:03Z",
  "sessionId": "session_123",
  "messageId": "msg_789",
  "error": "Connection timeout",
  "errorCode": "TIMEOUT_ERROR",
  "metadata": {
    "nodeId": "ws-node-1",
    "chunkIndex": 25
  }
}
```

#### Consumer Examples

```java
// Audit Trail Consumer
@Service
@Slf4j
public class AuditTrailConsumer {
    
    @KafkaListener(topics = "stream-events", groupId = "audit-group")
    public void consumeEvent(String eventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            // Store in long-term storage (S3, Data Lake, etc.)
            auditLogRepository.save(AuditLog.builder()
                .eventType(event.get("eventType"))
                .timestamp(Instant.parse(event.get("timestamp")))
                .eventData(eventJson)
                .build());
                
            log.info("Audit event stored: type={}", event.get("eventType"));
            
        } catch (Exception e) {
            log.error("Failed to process audit event", e);
        }
    }
}

// Analytics Consumer
@Service
@Slf4j
public class AnalyticsConsumer {
    
    @KafkaListener(topics = "stream-events", groupId = "analytics-group")
    public void consumeEvent(String eventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String eventType = (String) event.get("eventType");
            
            switch (eventType) {
                case "STREAM_COMPLETED":
                    // Calculate metrics
                    int totalChunks = (int) event.get("totalChunks");
                    long latency = (long) event.get("totalLatencyMs");
                    
                    metricsService.recordHistogram("stream.chunks", totalChunks);
                    metricsService.recordTimer("stream.latency", Duration.ofMillis(latency));
                    
                    // Store for time-series analysis
                    analyticsRepository.save(StreamMetrics.builder()
                        .timestamp(Instant.now())
                        .totalChunks(totalChunks)
                        .latencyMs(latency)
                        .build());
                    break;
                    
                case "STREAM_ERROR":
                    // Alert on errors
                    alertService.sendAlert("Stream error detected", event);
                    break;
            }
            
        } catch (Exception e) {
            log.error("Failed to process analytics event", e);
        }
    }
}
```

### 1.4 Queries Enabled by Event Sourcing

```sql
-- 1. Tìm tất cả sessions của user trong 1 ngày
SELECT * FROM audit_logs 
WHERE event_type = 'SESSION_STARTED' 
  AND event_data->>'userId' = 'user_456'
  AND timestamp >= '2024-01-15 00:00:00';

-- 2. Tính average streaming latency per node
SELECT 
  event_data->>'nodeId' as node,
  AVG((event_data->>'totalLatencyMs')::int) as avg_latency_ms
FROM audit_logs 
WHERE event_type = 'STREAM_COMPLETED'
GROUP BY event_data->>'nodeId';

-- 3. Tìm sessions có error
SELECT * FROM audit_logs 
WHERE event_type = 'STREAM_ERROR'
  AND timestamp >= NOW() - INTERVAL '1 hour'
ORDER BY timestamp DESC;
```

---

## 2. Async Background Processing

### 2.1 Concept

**Decouple** real-time streaming from heavy processing:
- Real-time path: WebSocket → Redis → Client (low latency)
- Background path: Kafka → Workers → Storage (high throughput)

### 2.2 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│              Async Processing Architecture                    │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌───────────┐                                               │
│  │  Client   │                                               │
│  └─────┬─────┘                                               │
│        │                                                      │
│        │ WebSocket (Real-time, <100ms)                       │
│        ▼                                                      │
│  ┌───────────┐         ┌──────────┐                         │
│  │ WebSocket │────────▶│  Redis   │                         │
│  │  Server   │         │  PubSub  │                         │
│  └─────┬─────┘         └──────────┘                         │
│        │                                                      │
│        │ Kafka Event (Async, no latency impact)             │
│        ▼                                                      │
│  ┌───────────┐                                               │
│  │   Kafka   │                                               │
│  │  Topics   │                                               │
│  └─────┬─────┘                                               │
│        │                                                      │
│        ├──────────────┬──────────────┬──────────────┐       │
│        │              │              │              │        │
│        ▼              ▼              ▼              ▼        │
│  ┌─────────┐   ┌──────────┐   ┌──────────┐   ┌─────────┐  │
│  │ Indexer │   │Analytics │   │ ML Model │   │ Notif.  │  │
│  │ Worker  │   │  Worker  │   │  Worker  │   │ Worker  │  │
│  └────┬────┘   └────┬─────┘   └────┬─────┘   └────┬────┘  │
│       │             │              │              │         │
│       ▼             ▼              ▼              ▼         │
│  ┌─────────┐   ┌──────────┐   ┌──────────┐   ┌─────────┐  │
│  │ Search  │   │ Time-    │   │ Training │   │ Email/  │  │
│  │  Index  │   │ Series   │   │   Data   │   │  SMS    │  │
│  └─────────┘   └──────────┘   └──────────┘   └─────────┘  │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 Implementation Examples

#### Worker 1: Search Indexer

```java
@Service
@Slf4j
public class SearchIndexerWorker {
    
    private final ElasticsearchClient elasticsearchClient;
    
    @KafkaListener(
        topics = "chat-events",
        groupId = "search-indexer",
        concurrency = "3"  // 3 parallel consumers
    )
    public void indexChatMessage(String eventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            if ("CHAT_MESSAGE".equals(event.get("eventType"))) {
                // Extract message details
                String messageId = (String) event.get("messageId");
                String conversationId = (String) event.get("conversationId");
                String userId = (String) event.get("userId");
                
                // Fetch full message from database
                Message message = messageRepository.findById(messageId)
                    .orElseThrow();
                
                // Index in Elasticsearch
                elasticsearchClient.index(IndexRequest.of(i -> i
                    .index("chat_messages")
                    .id(messageId)
                    .document(Map.of(
                        "conversationId", conversationId,
                        "userId", userId,
                        "content", message.getContent(),
                        "timestamp", message.getCreatedAt(),
                        "role", message.getRole()
                    ))
                ));
                
                log.info("Message indexed: messageId={}", messageId);
            }
            
        } catch (Exception e) {
            log.error("Failed to index message", e);
            // Send to DLQ (Dead Letter Queue) for retry
            throw e;
        }
    }
}
```

#### Worker 2: Analytics Aggregator

```java
@Service
@Slf4j
public class AnalyticsAggregatorWorker {
    
    private final InfluxDBClient influxDBClient;
    
    @KafkaListener(
        topics = "stream-events",
        groupId = "analytics-aggregator",
        concurrency = "5"
    )
    public void aggregateMetrics(String eventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String eventType = (String) event.get("eventType");
            
            // Time-series data points
            Point point = Point.measurement("chat_metrics")
                .addTag("event_type", eventType)
                .addTag("node_id", (String) event.get("nodeId"))
                .time(Instant.parse((String) event.get("timestamp")), WritePrecision.MS);
            
            switch (eventType) {
                case "CHUNK_RECEIVED":
                    point.addField("chunk_index", (Integer) event.get("chunkIndex"))
                         .addField("content_length", (Integer) event.get("contentLength"))
                         .addField("latency_ms", (Integer) event.get("latencyMs"));
                    break;
                    
                case "STREAM_COMPLETED":
                    point.addField("total_chunks", (Integer) event.get("totalChunks"))
                         .addField("total_latency_ms", (Integer) event.get("totalLatencyMs"))
                         .addField("content_length", (Integer) event.get("contentLength"));
                    break;
            }
            
            // Write to time-series database
            influxDBClient.writeApi().writePoint(point);
            
            log.debug("Metric aggregated: type={}", eventType);
            
        } catch (Exception e) {
            log.error("Failed to aggregate metrics", e);
        }
    }
}
```

#### Worker 3: ML Training Data Pipeline

```java
@Service
@Slf4j
public class MLTrainingDataWorker {
    
    private final S3Client s3Client;
    private final String bucketName = "ml-training-data";
    
    @KafkaListener(
        topics = "chat-events",
        groupId = "ml-training",
        concurrency = "2"
    )
    public void collectTrainingData(String eventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            if ("CHAT_MESSAGE".equals(event.get("eventType"))) {
                String messageId = (String) event.get("messageId");
                
                // Fetch full message with context
                Message message = messageRepository.findById(messageId)
                    .orElseThrow();
                
                // Fetch conversation history
                List<Message> history = messageRepository
                    .findByConversationIdOrderByCreatedAt(
                        message.getConversationId()
                    );
                
                // Format as training data
                TrainingDataPoint dataPoint = TrainingDataPoint.builder()
                    .conversationId(message.getConversationId())
                    .userMessage(findPreviousUserMessage(history, message))
                    .assistantResponse(message.getContent())
                    .timestamp(message.getCreatedAt())
                    .metadata(Map.of(
                        "messageId", messageId,
                        "chunkCount", calculateChunkCount(messageId)
                    ))
                    .build();
                
                // Save to S3 for batch training
                String key = String.format(
                    "training-data/%s/%s.json",
                    LocalDate.now(),
                    messageId
                );
                
                s3Client.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build(),
                    RequestBody.fromString(objectMapper.writeValueAsString(dataPoint))
                );
                
                log.info("Training data saved: key={}", key);
            }
            
        } catch (Exception e) {
            log.error("Failed to save training data", e);
        }
    }
}
```

#### Worker 4: Notification Service

```java
@Service
@Slf4j
public class NotificationWorker {
    
    private final EmailService emailService;
    private final WebhookService webhookService;
    
    @KafkaListener(
        topics = "stream-events",
        groupId = "notifications",
        concurrency = "3"
    )
    public void sendNotifications(String eventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String eventType = (String) event.get("eventType");
            
            // Alert on errors
            if ("STREAM_ERROR".equals(eventType)) {
                String error = (String) event.get("error");
                String sessionId = (String) event.get("sessionId");
                
                // Send to monitoring team
                emailService.send(
                    "ops-team@example.com",
                    "Stream Error Alert",
                    String.format("Session %s failed: %s", sessionId, error)
                );
                
                // Webhook to Slack/PagerDuty
                webhookService.sendAlert(Map.of(
                    "severity", "high",
                    "event", "stream_error",
                    "details", event
                ));
                
                log.warn("Error notification sent: sessionId={}", sessionId);
            }
            
            // Track SLA violations
            if ("STREAM_COMPLETED".equals(eventType)) {
                long latency = (long) event.get("totalLatencyMs");
                
                // Alert if latency > 10 seconds
                if (latency > 10000) {
                    webhookService.sendAlert(Map.of(
                        "severity", "medium",
                        "event", "sla_violation",
                        "latency_ms", latency,
                        "threshold_ms", 10000
                    ));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send notification", e);
        }
    }
}
```

### 2.4 Benefits

1. **Decoupling**: Real-time path không bị ảnh hưởng bởi heavy processing
2. **Scalability**: Scale workers độc lập với WebSocket servers
3. **Reliability**: Kafka guarantees message delivery, retry on failure
4. **Flexibility**: Thêm workers mới không cần modify existing code

---

## 3. Guaranteed Message Delivery

### 3.1 Concept

**At-Least-Once Delivery** trong Multi-Node:
- Kafka replication: Data không bị mất khi node crash
- Consumer acknowledgment: Chỉ commit offset khi processed thành công
- Retry mechanism: Tự động retry failed messages

### 3.2 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│           Guaranteed Delivery Architecture                    │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Producer Side:                                              │
│  ┌──────────┐      acks=all       ┌────────────────┐        │
│  │ Producer │─────────────────────▶│ Kafka Leader   │        │
│  └──────────┘                      │   Partition    │        │
│       │                            └────────┬───────┘        │
│       │                                     │                 │
│       │                            ┌────────▼───────┐        │
│       │                            │ Replica 1      │        │
│       │                            └────────┬───────┘        │
│       │                                     │                 │
│       │                            ┌────────▼───────┐        │
│       │◀─────────ACK───────────────│ Replica 2      │        │
│       │                            └────────────────┘        │
│       │                                                       │
│       │ Retry on failure (max 3 times)                      │
│       │                                                       │
│                                                               │
│  Consumer Side:                                              │
│  ┌──────────┐      fetch         ┌────────────────┐        │
│  │ Consumer │◀────────────────────│ Kafka Broker   │        │
│  └────┬─────┘                     └────────────────┘        │
│       │                                                       │
│       ├─▶ 1. Read message                                   │
│       ├─▶ 2. Process business logic                         │
│       ├─▶ 3. Save to database                               │
│       └─▶ 4. Commit offset (acknowledge)                    │
│                                                               │
│       If step 2-3 fail → Don't commit → Auto-retry          │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 3.3 Implementation

#### Producer Configuration (Already Done)

```java
// In KafkaConfig.java
config.put(ProducerConfig.ACKS_CONFIG, "all");  // Wait for all replicas
config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // Prevent duplicates
config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
```

#### Consumer Configuration (Already Done)

```java
// In KafkaConfig.java
config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // Manual commit
config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // Start from beginning if no offset
```

#### Manual Acknowledgment Pattern

```java
@Service
@Slf4j
public class ReliableMessageConsumer {
    
    @KafkaListener(
        topics = "chat-events",
        groupId = "reliable-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMessage(
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment  // Manual ack
    ) {
        String messageId = record.key();
        String eventJson = record.value();
        
        try {
            // Step 1: Parse message
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            // Step 2: Validate
            if (!validateEvent(event)) {
                log.error("Invalid event: offset={}", record.offset());
                // Send to DLQ
                sendToDeadLetterQueue(record);
                // Ack to skip this message
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Process with idempotency check
            if (!isAlreadyProcessed(messageId)) {
                processEvent(event);
                markAsProcessed(messageId);
            } else {
                log.warn("Duplicate message detected (already processed): messageId={}", messageId);
            }
            
            // Step 4: Commit offset only after successful processing
            acknowledgment.acknowledge();
            
            log.info("Message processed successfully: offset={}, messageId={}", 
                record.offset(), messageId);
            
        } catch (Exception e) {
            log.error("Failed to process message: offset={}, messageId={}", 
                record.offset(), messageId, e);
            
            // Don't acknowledge → Kafka will redeliver
            // Add exponential backoff to avoid tight retry loop
            try {
                Thread.sleep(calculateBackoff(record.offset()));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            
            throw new RetryableException("Processing failed, will retry", e);
        }
    }
    
    private boolean isAlreadyProcessed(String messageId) {
        // Check in Redis or database
        return redisTemplate.hasKey("processed::" + messageId);
    }
    
    private void markAsProcessed(String messageId) {
        // Store with TTL to prevent memory leak
        redisTemplate.opsForValue().set(
            "processed::" + messageId,
            "1",
            Duration.ofDays(7)
        );
    }
    
    private long calculateBackoff(long offset) {
        // Exponential backoff: 1s, 2s, 4s, 8s, max 30s
        long attempt = offset % 10; // Simple attempt counter
        return Math.min(1000 * (1 << attempt), 30000);
    }
    
    private void sendToDeadLetterQueue(ConsumerRecord<String, String> record) {
        // Send to DLQ topic for manual investigation
        kafkaTemplate.send("chat-events-dlq", record.key(), record.value());
    }
}
```

#### Transaction Support (Advanced)

```java
@Service
@Slf4j
public class TransactionalMessageProcessor {
    
    @Transactional  // Database transaction
    @KafkaListener(
        topics = "chat-events",
        groupId = "transactional-consumer"
    )
    public void processWithTransaction(
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment
    ) {
        try {
            // All operations in one transaction
            Message message = parseMessage(record.value());
            
            // 1. Save to database
            messageRepository.save(message);
            
            // 2. Update conversation
            conversationService.updateLastMessage(message.getConversationId(), message);
            
            // 3. Increment counters
            statisticsService.incrementMessageCount(message.getUserId());
            
            // If any step fails, entire transaction rolls back
            // Don't acknowledge → message will be redelivered
            
            // Commit only if all successful
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Transaction failed, will rollback and retry", e);
            throw e; // Rollback transaction
        }
    }
}
```

### 3.4 Delivery Guarantees

```
┌────────────────────────────────────────────────────────────┐
│            Delivery Guarantee Levels                       │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  1. At-Most-Once (acks=0, auto-commit=true)              │
│     - Fastest, but may lose messages                      │
│     - Use case: Metrics that can tolerate loss            │
│                                                            │
│  2. At-Least-Once (acks=all, manual commit) ✅ USED      │
│     - Guarantees delivery, may have duplicates           │
│     - Use case: Critical events (our implementation)      │
│     - Require idempotency in consumer                     │
│                                                            │
│  3. Exactly-Once (idempotent producer + transactions)     │
│     - Strongest guarantee, most complex                   │
│     - Use case: Financial transactions                    │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 4. Stream Replay & Recovery

### 4.1 Concept

**Time Travel**: Replay past events để:
- Debug production issues
- Reconstruct state after data corruption
- Test new features với production data
- Backfill new consumers

### 4.2 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                Stream Replay Architecture                     │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Kafka Topic: chat-events                                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Offset 0     Offset 1000   Offset 2000   Offset 3000  │  │
│  │   ▼             ▼             ▼             ▼          │  │
│  │  [===]────────[===]─────────[===]─────────[===]────▶  │  │
│  │  Day 1       Day 2         Day 3         Day 4        │  │
│  │  (7 days retention by default)                         │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  Consumer Groups:                                            │
│                                                               │
│  1. Real-time Consumer                                       │
│     └─▶ Reads from latest offset (following tail)           │
│                                                               │
│  2. Replay Consumer                                          │
│     └─▶ Resets offset to specific timestamp/offset          │
│         └─▶ Replays all events from that point              │
│                                                               │
│  3. New Feature Consumer                                     │
│     └─▶ Starts from earliest offset                          │
│         └─▶ Processes all historical data                    │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 4.3 Implementation

#### Replay by Timestamp

```java
@Service
@Slf4j
public class StreamReplayService {
    
    private final KafkaAdmin kafkaAdmin;
    private final KafkaConsumer<String, String> consumer;
    
    /**
     * Replay events from a specific timestamp
     */
    public void replayFromTimestamp(String topic, Instant fromTimestamp) {
        try {
            // 1. Get all partitions for topic
            List<TopicPartition> partitions = consumer.partitionsFor(topic)
                .stream()
                .map(p -> new TopicPartition(topic, p.partition()))
                .collect(Collectors.toList());
            
            // 2. Assign partitions
            consumer.assign(partitions);
            
            // 3. Get offset for timestamp on each partition
            Map<TopicPartition, Long> timestampToSearch = partitions.stream()
                .collect(Collectors.toMap(
                    tp -> tp,
                    tp -> fromTimestamp.toEpochMilli()
                ));
            
            Map<TopicPartition, OffsetAndTimestamp> offsets = 
                consumer.offsetsForTimes(timestampToSearch);
            
            // 4. Seek to computed offsets
            offsets.forEach((partition, offsetAndTimestamp) -> {
                if (offsetAndTimestamp != null) {
                    consumer.seek(partition, offsetAndTimestamp.offset());
                    log.info("Seeking partition {} to offset {}", 
                        partition.partition(), offsetAndTimestamp.offset());
                } else {
                    // Timestamp not found, seek to beginning
                    consumer.seekToBeginning(List.of(partition));
                    log.warn("Timestamp not found for partition {}, seeking to beginning", 
                        partition.partition());
                }
            });
            
            // 5. Start consuming
            log.info("Replay started from timestamp: {}", fromTimestamp);
            int messagesReplayed = 0;
            
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                
                if (records.isEmpty()) {
                    log.info("No more records to replay");
                    break;
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    // Process event
                    processReplayEvent(record);
                    messagesReplayed++;
                    
                    if (messagesReplayed % 1000 == 0) {
                        log.info("Replayed {} messages", messagesReplayed);
                    }
                }
            }
            
            log.info("Replay completed: {} messages replayed", messagesReplayed);
            
        } catch (Exception e) {
            log.error("Replay failed", e);
            throw new RuntimeException("Failed to replay stream", e);
        }
    }
    
    /**
     * Replay events by offset range
     */
    public void replayFromOffset(String topic, int partition, long fromOffset, long toOffset) {
        try {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(List.of(topicPartition));
            
            // Seek to start offset
            consumer.seek(topicPartition, fromOffset);
            
            log.info("Replaying partition {} from offset {} to {}", partition, fromOffset, toOffset);
            int messagesReplayed = 0;
            
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                
                for (ConsumerRecord<String, String> record : records) {
                    // Stop if reached end offset
                    if (record.offset() >= toOffset) {
                        log.info("Reached end offset: {}", toOffset);
                        return;
                    }
                    
                    processReplayEvent(record);
                    messagesReplayed++;
                }
                
                if (records.isEmpty()) {
                    break;
                }
            }
            
            log.info("Replay completed: {} messages replayed", messagesReplayed);
            
        } catch (Exception e) {
            log.error("Replay failed", e);
            throw new RuntimeException("Failed to replay stream", e);
        }
    }
    
    private void processReplayEvent(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
            
            // Mark as replay to avoid side effects
            event.put("__replay", true);
            
            // Process event (e.g., rebuild index, recalculate metrics)
            eventProcessor.process(event);
            
        } catch (Exception e) {
            log.error("Failed to process replay event: offset={}", record.offset(), e);
        }
    }
}
```

#### Use Case 1: Rebuild Search Index

```java
@Service
@Slf4j
public class IndexRebuildService {
    
    private final StreamReplayService replayService;
    private final ElasticsearchClient elasticsearchClient;
    
    /**
     * Rebuild entire search index from Kafka events
     */
    public void rebuildSearchIndex() {
        log.info("Starting search index rebuild...");
        
        try {
            // 1. Delete existing index
            elasticsearchClient.indices().delete(d -> d.index("chat_messages"));
            
            // 2. Create new index with mapping
            elasticsearchClient.indices().create(c -> c
                .index("chat_messages")
                .mappings(m -> m
                    .properties("conversationId", p -> p.keyword(k -> k))
                    .properties("userId", p -> p.keyword(k -> k))
                    .properties("content", p -> p.text(t -> t))
                    .properties("timestamp", p -> p.date(d -> d))
                )
            );
            
            // 3. Replay all CHAT_MESSAGE events
            AtomicInteger indexed = new AtomicInteger(0);
            
            replayService.replayFromTimestamp(
                "chat-events",
                Instant.now().minus(Duration.ofDays(7))  // Last 7 days
            );
            
            log.info("Search index rebuild completed: {} documents indexed", indexed.get());
            
        } catch (Exception e) {
            log.error("Index rebuild failed", e);
            throw new RuntimeException("Failed to rebuild index", e);
        }
    }
}
```

#### Use Case 2: Debug Production Issue

```java
@Service
@Slf4j
public class DebugReplayService {
    
    /**
     * Replay specific session to debug issue
     */
    public void replaySessionForDebug(String sessionId) {
        log.info("Replaying session for debug: sessionId={}", sessionId);
        
        try {
            // Create temporary consumer
            KafkaConsumer<String, String> consumer = createDebugConsumer();
            
            List<TopicPartition> partitions = consumer.partitionsFor("stream-events")
                .stream()
                .map(p -> new TopicPartition("stream-events", p.partition()))
                .collect(Collectors.toList());
            
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            
            List<Map<String, Object>> sessionEvents = new ArrayList<>();
            
            // Scan all events
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                
                if (records.isEmpty()) break;
                
                for (ConsumerRecord<String, String> record : records) {
                    Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
                    
                    if (sessionId.equals(event.get("sessionId"))) {
                        sessionEvents.add(event);
                    }
                }
            }
            
            consumer.close();
            
            // Print timeline
            log.info("Found {} events for session {}", sessionEvents.size(), sessionId);
            
            sessionEvents.stream()
                .sorted(Comparator.comparing(e -> (String) e.get("timestamp")))
                .forEach(event -> {
                    log.info("  {} - {} - {}", 
                        event.get("timestamp"),
                        event.get("eventType"),
                        event.get("metadata")
                    );
                });
            
            // Save to file for analysis
            String filename = String.format("debug_session_%s_%s.json", 
                sessionId, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            Files.writeString(
                Paths.get("/tmp/" + filename),
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(sessionEvents)
            );
            
            log.info("Debug data saved to: /tmp/{}", filename);
            
        } catch (Exception e) {
            log.error("Debug replay failed", e);
        }
    }
    
    private KafkaConsumer<String, String> createDebugConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "debug-replay-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        return new KafkaConsumer<>(props);
    }
}
```

#### Use Case 3: Backfill New Consumer

```java
@Service
@Slf4j
public class NewFeatureConsumer {
    
    /**
     * New feature that needs historical data
     */
    @KafkaListener(
        topics = "chat-events",
        groupId = "conversation-summary-generator",  // New consumer group
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void generateConversationSummaries(String eventJson, Acknowledgment ack) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            if ("STREAM_COMPLETED".equals(event.get("eventType"))) {
                String conversationId = (String) event.get("conversationId");
                
                // Generate AI summary of conversation
                String summary = aiService.generateSummary(conversationId);
                
                // Save summary
                conversationSummaryRepository.save(ConversationSummary.builder()
                    .conversationId(conversationId)
                    .summary(summary)
                    .generatedAt(Instant.now())
                    .build());
                
                log.info("Summary generated for conversation: {}", conversationId);
            }
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            throw e;
        }
    }
}

// Reset offset to process all historical data
@Component
@Slf4j
public class ConsumerOffsetManager {
    
    @Autowired
    private KafkaAdmin kafkaAdmin;
    
    /**
     * Reset consumer group to earliest offset to backfill data
     */
    public void resetConsumerToEarliest(String groupId, String topic) {
        try {
            AdminClient adminClient = AdminClient.create(
                kafkaAdmin.getConfigurationProperties()
            );
            
            // Delete consumer group offsets
            adminClient.deleteConsumerGroups(List.of(groupId))
                .all()
                .get(30, TimeUnit.SECONDS);
            
            log.info("Consumer group {} reset to earliest for topic {}", groupId, topic);
            
            // Next time consumer starts, it will read from earliest offset
            
        } catch (Exception e) {
            log.error("Failed to reset consumer", e);
        }
    }
}
```

### 4.4 Retention & Compaction

```yaml
# kafka configuration
KAFKA_LOG_RETENTION_HOURS: 168  # 7 days (current)
KAFKA_LOG_RETENTION_BYTES: 1073741824  # 1GB per partition

# For longer retention (event sourcing)
KAFKA_LOG_RETENTION_HOURS: 8760  # 1 year
KAFKA_LOG_RETENTION_BYTES: -1  # Unlimited

# Log compaction for state topics (keep only latest)
KAFKA_LOG_CLEANUP_POLICY: compact
KAFKA_LOG_SEGMENT_MS: 3600000  # 1 hour
```

---

## 5. Multi-Node Coordination với Kafka

### 5.1 Problem Statement

```
┌────────────────────────────────────────────────────────────┐
│         Multi-Node Challenges                              │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Challenge 1: User connects to Node 1                     │
│               Message streaming from Node 2                │
│               → How to deliver to correct node?            │
│                                                            │
│  Challenge 2: Node crashes during streaming               │
│               → How to continue from another node?         │
│                                                            │
│  Challenge 3: Load balancing                              │
│               → How to distribute connections evenly?      │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 5.2 Solution với Kafka

```java
@Service
@Slf4j
public class MultiNodeCoordinator {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SessionManager sessionManager;
    
    /**
     * Broadcast message to all nodes
     * Each node checks if it has the WebSocket connection
     */
    public void broadcastToAllNodes(String sessionId, ChatMessage message) {
        // Publish to Kafka topic
        kafkaTemplate.send("multi-node-broadcast", sessionId, message);
    }
    
    /**
     * Each node listens and delivers if it has the connection
     */
    @KafkaListener(
        topics = "multi-node-broadcast",
        groupId = "ws-nodes",  // Each node in same group
        concurrency = "1"  // Single consumer per node
    )
    public void receiveBroadcast(String sessionId, ChatMessage message) {
        // Check if this node has the WebSocket connection
        Optional<WebSocketSession> wsSession = sessionManager.getWebSocketSession(sessionId);
        
        if (wsSession.isPresent() && wsSession.get().isOpen()) {
            try {
                // Deliver to client
                wsSession.get().sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(message)
                ));
                
                log.info("Message delivered on node {}: sessionId={}", 
                    nodeId, sessionId);
                    
            } catch (IOException e) {
                log.error("Failed to send to WebSocket", e);
            }
        } else {
            log.debug("Session not on this node: sessionId={}", sessionId);
        }
    }
}
```

---

## 6. Monitoring & Observability

### 6.1 Kafka Metrics Dashboard

```java
@Service
@Slf4j
public class KafkaMetricsCollector {
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void collectMetrics() {
        // Collect Kafka metrics
        Map<String, Object> metrics = Map.of(
            "kafka.producer.record_send_rate", getProducerSendRate(),
            "kafka.consumer.records_consumed_rate", getConsumerConsumeRate(),
            "kafka.consumer.lag", getConsumerLag(),
            "kafka.broker.bytes_in_rate", getBrokerBytesInRate()
        );
        
        // Send to monitoring system
        metricsService.recordGauge("kafka", metrics);
    }
}
```

---

## 7. Best Practices

### 7.1 Do's ✅

1. **Use Idempotent Producer**: Prevent duplicate events
2. **Manual Acknowledgment**: Control when message is committed
3. **Monitor Consumer Lag**: Alert if lag > threshold
4. **Use Dead Letter Queue**: Handle poison messages
5. **Implement Retry Logic**: Exponential backoff
6. **Version Events**: Support schema evolution
7. **Compress Messages**: Use Snappy/LZ4
8. **Partition by Key**: sessionId for ordering

### 7.2 Don'ts ❌

1. **Don't Auto-commit**: Lose control of exactly-when-once
2. **Don't Ignore Errors**: May lose messages
3. **Don't Block in Listener**: Causes rebalancing
4. **Don't Store Large Payloads**: Use reference pattern
5. **Don't Skip Monitoring**: Lag = production issue
6. **Don't Forget Retention**: Data loss after TTL
7. **Don't Over-partition**: Overhead increases
8. **Don't Share Consumer**: Concurrency issues

---

## 8. Summary

| Use Case | Kafka Topic | Benefit |
|----------|-------------|---------|
| Event Sourcing | `chat-events` | Complete audit trail |
| Analytics | `stream-events` | Real-time metrics |
| Search Index | `chat-events` | Background indexing |
| ML Training | `chat-events` | Training data pipeline |
| Monitoring | `stream-events` | Error alerting |
| Multi-Node | `multi-node-broadcast` | Cross-node delivery |
| Recovery | All topics | Stream replay |

Kafka trong Multi-Node Chat = **Backbone** của hệ thống:
- **Reliability**: At-least-once delivery
- **Scalability**: Parallel processing
- **Flexibility**: Add consumers dynamically
- **Observability**: Complete event history
