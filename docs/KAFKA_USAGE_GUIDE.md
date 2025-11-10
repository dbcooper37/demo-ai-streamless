# Kafka Usage Guide - Multi-Node Chat Stream

## Quick Start

### 1. Enable Kafka

Kafka đã được enable trong `docker-compose.yml`:

```yaml
java-websocket:
  environment:
    - KAFKA_ENABLED=true  # ✅ Already enabled
    - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

### 2. Start Services

```bash
# Start all services including Kafka
docker-compose up -d

# Check Kafka is running
docker logs demo-kafka

# Check Java service connected to Kafka
docker logs demo-java-websocket | grep "Kafka"
```

Expected logs:
```
Kafka EventPublisher enabled for event sourcing and analytics
AuditTrailConsumer initialized - audit logging enabled
AnalyticsConsumer initialized - real-time analytics enabled
StreamReplayService initialized - event replay enabled
```

---

## Features Available

### 1. Event Sourcing & Audit Trail ✅

**Tự động lưu tất cả events** vào database để:
- Compliance & regulatory requirements
- Debug production issues  
- Security auditing
- User activity tracking

#### Events Được Audit

| Event Type | Description | Stored in DB |
|------------|-------------|--------------|
| `SESSION_STARTED` | User bắt đầu chat | ✅ |
| `STREAM_COMPLETED` | Message hoàn thành | ✅ |
| `STREAM_ERROR` | Có lỗi xảy ra | ✅ |
| `RECOVERY_ATTEMPT` | Client reconnect | ✅ |
| `CHAT_MESSAGE` | Message saved | ✅ |
| `CHUNK_RECEIVED` | Streaming chunk | ❌ (too many) |

#### Query Audit Logs

```java
@Autowired
private AuditLogRepository auditLogRepository;

// 1. Tìm tất cả events của user
List<AuditLog> userLogs = auditLogRepository.findByUserId("user_123");

// 2. Tìm errors trong 1 giờ qua
List<AuditLog> errors = auditLogRepository.findRecentLogs(
    Instant.now().minus(Duration.ofHours(1))
);

// 3. Tìm tất cả events của một conversation
List<AuditLog> conversationLogs = 
    auditLogRepository.findByConversationId("conv_abc");

// 4. Count events by type
List<Object[]> stats = auditLogRepository.countByEventType();
stats.forEach(row -> 
    System.out.println(row[0] + ": " + row[1])
);
```

#### View in H2 Console

```bash
# Access H2 console
http://localhost:8080/h2-console

# Connection details:
JDBC URL: jdbc:h2:mem:websocketdb
Username: sa
Password: (leave empty)

# Query audit logs
SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100;

# Find errors
SELECT * FROM audit_logs WHERE event_type LIKE '%ERROR%';

# User activity
SELECT event_type, COUNT(*) as count 
FROM audit_logs 
WHERE user_id = 'demo_user' 
GROUP BY event_type;
```

---

### 2. Real-time Analytics ✅

**Tự động track metrics** từ Kafka events:
- Streaming performance (latency, chunks/s)
- User engagement (sessions, messages)
- Error rates & patterns
- System health indicators

#### Metrics Được Track

```java
// Session metrics
analytics.sessions.started               // Counter
analytics.streams.completed              // Counter

// Streaming performance
analytics.stream.duration                // Timer (ms)
analytics.stream.words_per_second        // Gauge

// Message metrics
analytics.message.length                 // Histogram
analytics.message.chunks                 // Histogram

// Chunk metrics
analytics.chunk.size                     // Histogram
analytics.chunks.received                // Counter

// Error metrics
analytics.errors.stream                  // Counter (by error_type)
analytics.recovery.success               // Counter
analytics.recovery.failed                // Counter
```

#### View Metrics

```java
@Autowired
private MetricsService metricsService;

// Check current metrics in logs
// [METRIC] analytics.sessions.started: 42
// [METRIC] analytics.stream.duration: avg=2500ms
```

---

### 3. Stream Replay & Debugging ✅

**Replay past events** để debug hoặc rebuild data.

#### Use Case 1: Debug Specific Session

```java
@Autowired
private StreamReplayService replayService;

// Replay all events for a session
List<Map<String, Object>> events = replayService.replaySession("session_123");

// Print timeline
events.forEach(event -> {
    System.out.println(String.format(
        "%s - %s - %s",
        event.get("timestamp"),
        event.get("eventType"),
        event.get("error") != null ? event.get("error") : "OK"
    ));
});

// Output:
// 2024-01-15T10:30:00Z - SESSION_STARTED - OK
// 2024-01-15T10:30:03Z - STREAM_ERROR - Connection timeout
// 2024-01-15T10:30:05Z - RECOVERY_ATTEMPT - OK
```

#### Use Case 2: Replay from Timestamp

```java
// Replay all events from last hour
int count = replayService.replayFromTimestamp(
    "stream-events",
    Instant.now().minus(Duration.ofHours(1)),
    event -> {
        // Process each event
        System.out.println("Processing: " + event.get("eventType"));
        
        // Your custom logic here
        if ("STREAM_ERROR".equals(event.get("eventType"))) {
            alertService.send("Error detected", event);
        }
    }
);

System.out.println("Replayed " + count + " events");
```

#### Use Case 3: Replay by Offset Range

```java
// Replay specific offset range for debugging
replayService.replayFromOffset(
    "chat-events",      // topic
    0,                  // partition
    1000,               // from offset
    2000,               // to offset
    event -> {
        // Process event
        System.out.println(event);
    }
);
```

---

### 4. Custom Kafka Consumers

Thêm consumer mới để xử lý events theo cách riêng của bạn.

#### Example 1: Email Notification Consumer

```java
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class EmailNotificationConsumer {
    
    @Autowired
    private EmailService emailService;
    
    @KafkaListener(
        topics = "stream-events",
        groupId = "email-notifications"
    )
    public void sendErrorNotification(String eventJson, Acknowledgment ack) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            if ("STREAM_ERROR".equals(event.get("eventType"))) {
                // Send alert email
                emailService.send(
                    "ops-team@example.com",
                    "Stream Error Alert",
                    "Session " + event.get("sessionId") + " failed: " + event.get("error")
                );
            }
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to send notification", e);
        }
    }
}
```

#### Example 2: Search Indexer Consumer

```java
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class SearchIndexerConsumer {
    
    @Autowired
    private ElasticsearchClient esClient;
    
    @KafkaListener(
        topics = "chat-events",
        groupId = "search-indexer",
        concurrency = "3"
    )
    public void indexMessage(String eventJson, Acknowledgment ack) {
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            if ("CHAT_MESSAGE".equals(event.get("eventType"))) {
                String messageId = (String) event.get("messageId");
                
                // Fetch full message
                Message message = messageRepository.findById(messageId).orElseThrow();
                
                // Index in Elasticsearch
                esClient.index(i -> i
                    .index("chat_messages")
                    .id(messageId)
                    .document(message)
                );
                
                log.info("Message indexed: {}", messageId);
            }
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to index message", e);
            throw e; // Retry
        }
    }
}
```

---

## Architecture Flow

```
┌──────────────────────────────────────────────────────────────┐
│                    Event Flow with Kafka                      │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  1. User sends message                                       │
│     │                                                         │
│     ▼                                                         │
│  2. ChatOrchestrator processes                               │
│     │                                                         │
│     ├──▶ Redis PubSub (real-time delivery)                  │
│     │    └──▶ WebSocket → Client (< 100ms)                  │
│     │                                                         │
│     └──▶ Kafka Topics (async processing)                     │
│          │                                                    │
│          ├──▶ AuditTrailConsumer                            │
│          │    └──▶ Save to Database (audit log)             │
│          │                                                    │
│          ├──▶ AnalyticsConsumer                             │
│          │    └──▶ Calculate metrics                         │
│          │                                                    │
│          ├──▶ SearchIndexerConsumer (your custom)           │
│          │    └──▶ Index in Elasticsearch                    │
│          │                                                    │
│          └──▶ NotificationConsumer (your custom)            │
│               └──▶ Send email/Slack alerts                   │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

## Kafka Topics

### stream-events

Events về streaming lifecycle:
- `SESSION_STARTED` - Streaming session bắt đầu
- `CHUNK_RECEIVED` - Mỗi chunk nhận được
- `STREAM_COMPLETED` - Message hoàn thành
- `STREAM_ERROR` - Có lỗi xảy ra
- `RECOVERY_ATTEMPT` - Client reconnect

### chat-events

Events về chat messages:
- `CHAT_MESSAGE` - Message được lưu vào DB

---

## Monitoring

### 1. Kafka UI (Debug Mode)

```bash
# Start Kafka UI
docker-compose --profile debug up -d kafka-ui

# Access UI
http://localhost:8090

# View:
# - Topics: chat-events, stream-events
# - Messages in real-time
# - Consumer groups and lag
# - Partitions and offsets
```

### 2. Check Consumer Lag

```bash
# Check consumer lag via CLI
docker exec demo-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group audit-trail-consumer

# Output shows lag for each partition
GROUP                  TOPIC           PARTITION  CURRENT-OFFSET  LAG
audit-trail-consumer   chat-events     0          1500            0
audit-trail-consumer   stream-events   0          5000            0
```

### 3. View Logs

```bash
# Java service logs (includes Kafka events)
docker logs -f demo-java-websocket | grep -E "Kafka|METRIC"

# Expected output:
# [METRIC] analytics.sessions.started: 42
# Kafka EventPublisher enabled
# Audit log saved: type=SESSION_STARTED
```

---

## Testing

### 1. Test Event Publishing

```bash
# Send a test message
curl -X POST http://localhost:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test_session",
    "user_id": "demo_user",
    "message": "Hello Kafka!"
  }'

# Check events in Kafka UI
# → Should see events in stream-events topic

# Check audit logs in DB
# → http://localhost:8080/h2-console
# → SELECT * FROM audit_logs ORDER BY timestamp DESC;
```

### 2. Test Stream Replay

```java
// In a test controller
@RestController
@RequestMapping("/api/debug")
public class DebugController {
    
    @Autowired
    private StreamReplayService replayService;
    
    @GetMapping("/replay-session/{sessionId}")
    public List<Map<String, Object>> replaySession(@PathVariable String sessionId) {
        return replayService.replaySession(sessionId);
    }
}

// Call endpoint
// GET http://localhost:8080/api/debug/replay-session/test_session
```

### 3. Test Analytics

```bash
# Send multiple messages to generate metrics
for i in {1..10}; do
  curl -X POST http://localhost:8000/api/chat \
    -H "Content-Type: application/json" \
    -d "{\"session_id\": \"test_$i\", \"user_id\": \"demo_user\", \"message\": \"Test message $i\"}"
  sleep 2
done

# Check metrics in logs
docker logs demo-java-websocket | grep "\[METRIC\]"

# Should see:
# [METRIC] analytics.sessions.started: 10
# [METRIC] analytics.streams.completed: 10
```

---

## Configuration

### Kafka Settings (application.yml)

```yaml
spring:
  kafka:
    enabled: true  # Enable/disable Kafka
    bootstrap-servers: kafka:9092
    
    consumer:
      group-id: chat-service
      enable-auto-commit: false  # Manual commit for reliability
      
    producer:
      acks: all  # Wait for all replicas
      retries: 3
      
    topics:
      chat-events: chat-events
      stream-events: stream-events
```

### Retention Settings

```yaml
# In docker-compose.yml
kafka:
  environment:
    # Keep events for 7 days (default)
    KAFKA_LOG_RETENTION_HOURS: 168
    
    # Keep events for 30 days (extended)
    # KAFKA_LOG_RETENTION_HOURS: 720
    
    # Keep events for 1 year (event sourcing)
    # KAFKA_LOG_RETENTION_HOURS: 8760
```

---

## Troubleshooting

### Kafka not starting

```bash
# Check Kafka logs
docker logs demo-kafka

# Common issue: Port conflict
# Solution: Stop other Kafka instances
lsof -i :9092
kill -9 <PID>

# Restart
docker-compose restart kafka
```

### Consumer not receiving messages

```bash
# Check consumer group exists
docker exec demo-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --list

# Should see:
# audit-trail-consumer
# analytics-consumer

# Check consumer lag
docker exec demo-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group audit-trail-consumer
```

### Events not being saved

```bash
# Check Java service logs
docker logs demo-java-websocket | grep "Kafka"

# Should see:
# Kafka EventPublisher enabled
# AuditTrailConsumer initialized
# AnalyticsConsumer initialized

# If not, check KAFKA_ENABLED=true in docker-compose.yml
```

### Clear Kafka data (reset)

```bash
# Stop services
docker-compose down

# Remove Kafka volume
docker volume rm demo_kafka-data

# Restart
docker-compose up -d
```

---

## Advanced Usage

### Add New Kafka Consumer

1. Create consumer class:

```java
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class MyCustomConsumer {
    
    @KafkaListener(
        topics = "stream-events",
        groupId = "my-custom-consumer",
        concurrency = "2"
    )
    public void consume(String eventJson, Acknowledgment ack) {
        try {
            // Your processing logic
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            // Process event
            System.out.println("Processing: " + event.get("eventType"));
            
            // Acknowledge
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process event", e);
            throw e; // Kafka will retry
        }
    }
}
```

2. Restart service:

```bash
docker-compose restart java-websocket
```

### Replay All Historical Data

```java
// Backfill new consumer with all historical data
@Component
public class DataBackfillRunner implements CommandLineRunner {
    
    @Autowired
    private StreamReplayService replayService;
    
    @Override
    public void run(String... args) {
        // Replay last 7 days on startup
        replayService.replayFromTimestamp(
            "chat-events",
            Instant.now().minus(Duration.ofDays(7)),
            event -> {
                // Process historical event
                myNewProcessor.process(event);
            }
        );
    }
}
```

---

## Summary

✅ **Event Sourcing**: Tất cả events được lưu trong audit logs  
✅ **Analytics**: Real-time metrics từ Kafka events  
✅ **Stream Replay**: Debug và rebuild data từ historical events  
✅ **Extensibility**: Dễ dàng thêm consumers mới  
✅ **Reliability**: At-least-once delivery, automatic retry  
✅ **Scalability**: Scale consumers độc lập  

**Kafka = Backbone** của Multi-Node Chat Stream Architecture!
