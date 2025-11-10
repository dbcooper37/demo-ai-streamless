# 📚 Documentation - Multi-Node Chat Stream Architecture

## Tổng Quan

Đây là tài liệu đầy đủ về kiến trúc Multi-Node Chat Stream với Kafka integration.

---

## 📖 Documents

### 1. [FIXES_SUMMARY.md](../FIXES_SUMMARY.md)
**Tóm tắt các vấn đề đã fix**

Issues resolved:
- ✅ UI text replacement → Fixed accumulation logic
- ✅ Kafka not being used → Integrated into architecture

**Đọc nếu**: Bạn muốn biết những gì đã được sửa và cách implement.

---

### 2. [KAFKA_MULTI_NODE_ARCHITECTURE.md](./KAFKA_MULTI_NODE_ARCHITECTURE.md)
**Kiến trúc chi tiết về Kafka trong Multi-Node**

Topics covered:
- Event Sourcing & Audit Trail
- Async Background Processing  
- Guaranteed Message Delivery
- Stream Replay & Recovery
- Multi-Node Coordination

**Đọc nếu**: Bạn muốn hiểu sâu về architecture và design patterns.

**Nội dung**:
- 📊 Architecture diagrams
- 💻 Implementation examples
- 🔧 Configuration details
- 🎯 Use cases & scenarios
- ⚡ Performance analysis

---

### 3. [KAFKA_USAGE_GUIDE.md](./KAFKA_USAGE_GUIDE.md)
**Hướng dẫn sử dụng các tính năng Kafka**

Guides:
- ✅ Quick start
- ✅ Enable/disable Kafka
- ✅ Query audit logs
- ✅ View analytics
- ✅ Replay streams
- ✅ Add custom consumers
- ✅ Monitor & troubleshoot

**Đọc nếu**: Bạn muốn thực hành và sử dụng features.

**Practical Examples**:
```java
// Query audit logs
auditLogRepository.findByUserId("user_123");

// Replay session
replayService.replaySession("session_123");

// Add custom consumer
@KafkaListener(topics = "chat-events")
public void processEvent(String event) { ... }
```

---

### 4. [KAFKA_SUMMARY.md](./KAFKA_SUMMARY.md)
**Tóm tắt toàn bộ Kafka integration**

Quick reference:
- 🎯 Why Kafka?
- 🏗️ Architecture overview
- 📊 4 use cases chi tiết
- 🔄 Message flow
- 📈 Metrics & monitoring
- ⚡ Performance impact
- 🎓 Best practices

**Đọc nếu**: Bạn muốn overview nhanh về toàn bộ system.

---

## 🚀 Quick Start

### 1. Enable Kafka

```bash
# Already enabled in docker-compose.yml
java-websocket:
  environment:
    - KAFKA_ENABLED=true
    - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

### 2. Start Services

```bash
docker-compose up -d

# Check logs
docker logs demo-java-websocket | grep "Kafka"

# Expected:
# Kafka EventPublisher enabled for event sourcing and analytics
# AuditTrailConsumer initialized - audit logging enabled
# AnalyticsConsumer initialized - real-time analytics enabled
```

### 3. Send Test Message

```bash
curl -X POST http://localhost:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test_session",
    "user_id": "demo_user",
    "message": "Hello Kafka!"
  }'
```

### 4. View Results

```bash
# 1. Kafka UI
http://localhost:8090
# Topics: chat-events, stream-events

# 2. H2 Console (Audit Logs)
http://localhost:8080/h2-console
# SELECT * FROM audit_logs ORDER BY timestamp DESC;

# 3. Logs (Metrics)
docker logs demo-java-websocket | grep "\[METRIC\]"
```

---

## 🎯 Use Cases

### 1. Event Sourcing & Audit Trail

```java
// Query user activity
List<AuditLog> logs = auditLogRepository.findByUserId("user_123");

// Find errors
List<AuditLog> errors = auditLogRepository.findErrorEvents();

// Conversation history
List<AuditLog> conversation = 
    auditLogRepository.findByConversationId("conv_abc");
```

**Benefits**:
- ✅ Complete audit trail
- ✅ Compliance & regulatory
- ✅ Security auditing
- ✅ Debug production issues

---

### 2. Real-time Analytics

```java
// Metrics are automatically tracked
analytics.sessions.started              // Counter
analytics.streams.completed             // Counter
analytics.stream.duration               // Timer
analytics.stream.words_per_second       // Gauge
analytics.message.length                // Histogram
analytics.errors.stream                 // Counter
```

**Benefits**:
- ✅ Performance monitoring
- ✅ User engagement
- ✅ Error tracking
- ✅ Capacity planning

---

### 3. Stream Replay

```java
// Debug specific session
List<Map<String, Object>> events = 
    replayService.replaySession("session_123");

// Replay from timestamp
replayService.replayFromTimestamp(
    "stream-events",
    Instant.now().minus(Duration.ofHours(1)),
    event -> processEvent(event)
);
```

**Benefits**:
- ✅ Debug production issues
- ✅ Rebuild corrupted data
- ✅ Test with production data
- ✅ Backfill new consumers

---

### 4. Async Processing

```java
// Add custom consumer
@Service
@KafkaListener(topics = "chat-events", groupId = "my-consumer")
public void processEvent(String eventJson) {
    // Your custom logic
    // - Search indexing
    // - ML training data
    # - Email notifications
    // - Analytics aggregation
}
```

**Benefits**:
- ✅ No impact on latency
- ✅ Scale independently
- ✅ Add features easily
- ✅ Parallel processing

---

## 📊 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│               Multi-Node Architecture                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Frontend                                                   │
│     │                                                        │
│     │ WebSocket                                              │
│     ▼                                                        │
│  Java WebSocket Servers (Multi-Node)                       │
│     │                                                        │
│     ├──▶ Redis PubSub (real-time, < 100ms)                 │
│     │    └──▶ WebSocket → Client ✅                         │
│     │                                                        │
│     └──▶ Kafka Topics (async, no latency impact)           │
│          │                                                   │
│          ├──▶ AuditTrailConsumer → Database                │
│          ├──▶ AnalyticsConsumer → Metrics                  │
│          ├──▶ SearchIndexer → Elasticsearch                │
│          ├──▶ MLTrainingData → S3                          │
│          └──▶ Notifications → Email/Slack                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 Components Implemented

### Core Services

| Component | Purpose | Status |
|-----------|---------|--------|
| `EventPublisher` | Publish events to Kafka | ✅ Done |
| `ChatOrchestrator` | Integrate Kafka publishing | ✅ Done |
| `RecoveryService` | Track recovery events | ✅ Done |

### Kafka Consumers

| Consumer | Purpose | Status |
|----------|---------|--------|
| `AuditTrailConsumer` | Save events to audit_logs | ✅ Done |
| `AnalyticsConsumer` | Track real-time metrics | ✅ Done |

### Domain Models

| Model | Purpose | Status |
|-------|---------|--------|
| `AuditLog` | Store audit trail | ✅ Done |
| `AuditLogRepository` | Query audit logs | ✅ Done |

### Services

| Service | Purpose | Status |
|---------|---------|--------|
| `StreamReplayService` | Replay historical events | ✅ Done |

---

## 📈 Metrics

### Kafka Metrics

```bash
# Check consumer lag
docker exec demo-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group audit-trail-consumer

# View topics
docker exec demo-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

### Application Metrics

```bash
# View in logs
docker logs demo-java-websocket | grep "\[METRIC\]"

# Expected output:
[METRIC] analytics.sessions.started: 42
[METRIC] analytics.streams.completed: 40
[METRIC] analytics.stream.duration: avg=2500ms
[METRIC] analytics.chunks.received: 500
```

---

## 🎓 Learning Path

### Beginner

1. Read [FIXES_SUMMARY.md](../FIXES_SUMMARY.md) - Hiểu những gì đã fix
2. Read [KAFKA_SUMMARY.md](./KAFKA_SUMMARY.md) - Overview toàn bộ system
3. Try [Quick Start](#-quick-start) - Thử nghiệm cơ bản

### Intermediate

4. Read [KAFKA_USAGE_GUIDE.md](./KAFKA_USAGE_GUIDE.md) - Học cách sử dụng
5. Query audit logs - Thực hành với database
6. Add custom consumer - Tạo consumer đầu tiên

### Advanced

7. Read [KAFKA_MULTI_NODE_ARCHITECTURE.md](./KAFKA_MULTI_NODE_ARCHITECTURE.md) - Hiểu sâu architecture
8. Implement stream replay - Debug production issues
9. Build analytics dashboard - Visualize metrics

---

## 🛠️ Common Tasks

### View Audit Logs

```sql
-- H2 Console: http://localhost:8080/h2-console

-- All events
SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100;

-- Errors only
SELECT * FROM audit_logs WHERE event_type LIKE '%ERROR%';

-- User activity
SELECT event_type, COUNT(*) 
FROM audit_logs 
WHERE user_id = 'demo_user' 
GROUP BY event_type;
```

### Debug Session

```java
@RestController
@RequestMapping("/api/debug")
public class DebugController {
    
    @Autowired
    private StreamReplayService replayService;
    
    @GetMapping("/session/{sessionId}")
    public List<Map<String, Object>> debugSession(@PathVariable String sessionId) {
        return replayService.replaySession(sessionId);
    }
}

// Call: GET http://localhost:8080/api/debug/session/test_session
```

### Monitor Kafka

```bash
# Kafka UI (visual)
http://localhost:8090

# Or CLI
docker exec -it demo-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic stream-events \
  --from-beginning
```

---

## 🔍 Troubleshooting

### Kafka not starting

```bash
# Check logs
docker logs demo-kafka

# Common fix: Remove volume and restart
docker-compose down
docker volume rm demo_kafka-data
docker-compose up -d
```

### Events not saved

```bash
# Check Kafka enabled
docker logs demo-java-websocket | grep "KAFKA_ENABLED"

# Should see: KAFKA_ENABLED=true

# Check consumers initialized
docker logs demo-java-websocket | grep "Consumer initialized"

# Should see:
# AuditTrailConsumer initialized
# AnalyticsConsumer initialized
```

### Consumer lag

```bash
# Check lag
docker exec demo-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --all-groups

# High lag = Consumer slower than producer
# Solution: Increase concurrency or add more consumers
```

---

## 📚 Additional Resources

### Configuration Files

- `docker-compose.yml` - Kafka & services config
- `application.yml` - Spring Boot Kafka settings
- `KafkaConfig.java` - Producer & consumer config

### Key Classes

```
java-websocket-server/src/main/java/com/demo/websocket/
├── config/
│   └── KafkaConfig.java                 # Kafka configuration
├── service/
│   ├── EventPublisher.java              # Publish events
│   └── StreamReplayService.java         # Replay streams
├── consumer/
│   ├── AuditTrailConsumer.java          # Audit logging
│   └── AnalyticsConsumer.java           # Metrics tracking
├── infrastructure/
│   ├── ChatOrchestrator.java            # Integrated publishing
│   └── RecoveryService.java             # Recovery events
├── domain/
│   └── AuditLog.java                    # Audit entity
└── repository/
    └── AuditLogRepository.java          # Query audit logs
```

---

## 🎯 Summary

**Kafka đã được tích hợp đầy đủ** vào Multi-Node Chat Stream Architecture:

✅ **Event Sourcing**: Complete audit trail  
✅ **Analytics**: Real-time metrics  
✅ **Stream Replay**: Debug & recovery  
✅ **Async Processing**: No latency impact  
✅ **Guaranteed Delivery**: At-least-once semantics  
✅ **Extensibility**: Easy to add consumers  

**Architecture**: Redis (real-time) + Kafka (reliability) = Perfect combo! 🚀

---

## 📞 Next Steps

1. ✅ Read documents theo learning path
2. ✅ Start services và test
3. ✅ Query audit logs
4. ✅ View metrics
5. ✅ Try stream replay
6. ✅ Add custom consumer
7. ✅ Build analytics dashboard

**Happy Coding!** 🎉
