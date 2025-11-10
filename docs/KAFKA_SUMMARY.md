# Tóm Tắt: Kafka trong Multi-Node Chat Stream Architecture

## 🎯 Tại Sao Cần Kafka?

### Vấn Đề Khi Không Có Kafka

```
┌────────────────────────────────────────────────────────────┐
│  Without Kafka (Redis PubSub Only)                        │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ❌ No audit trail → Cannot debug production issues       │
│  ❌ No analytics → Cannot measure performance              │
│  ❌ Cannot replay → Data lost after TTL                    │
│  ❌ No async processing → Heavy tasks block real-time     │
│  ❌ No guaranteed delivery → Messages can be lost          │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### Giải Pháp Với Kafka

```
┌────────────────────────────────────────────────────────────┐
│  With Kafka (Redis + Kafka)                               │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ✅ Complete audit trail → Debug any issue                 │
│  ✅ Real-time analytics → Monitor performance              │
│  ✅ Stream replay → Rebuild data anytime                   │
│  ✅ Async processing → No impact on latency                │
│  ✅ Guaranteed delivery → At-least-once semantics          │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 🏗️ Kiến Trúc Tổng Quan

```
┌─────────────────────────────────────────────────────────────────┐
│              Multi-Node Chat Stream Architecture                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Frontend (React)                                               │
│       │                                                          │
│       │ WebSocket                                                │
│       ▼                                                          │
│  ┌──────────────┐                                               │
│  │ Java WS Node │ ◀──┐                                          │
│  └──────┬───────┘    │                                          │
│         │            │ Load                                      │
│         │ Publish    │ Balance                                  │
│         ▼            │                                          │
│  ┌──────────────┐    │                                          │
│  │    Redis     │    │                                          │
│  │   PubSub     │────┤ Real-time (< 100ms)                     │
│  └──────────────┘    │                                          │
│         │            │                                          │
│         └────────────┘                                          │
│                                                                  │
│         │                                                        │
│         │ Also publish to Kafka (async, no latency impact)     │
│         ▼                                                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    Kafka Cluster                          │  │
│  │  ┌────────────────┐  ┌────────────────┐                 │  │
│  │  │  chat-events   │  │ stream-events  │                 │  │
│  │  │  Topic         │  │ Topic          │                 │  │
│  │  └───────┬────────┘  └───────┬────────┘                 │  │
│  └──────────┼───────────────────┼──────────────────────────┘  │
│             │                   │                              │
│             │ Fan-out to multiple consumers                    │
│             │                   │                              │
│      ┌──────┼──────┬────────────┼────────┬───────────────┐   │
│      │      │      │            │        │               │    │
│      ▼      ▼      ▼            ▼        ▼               ▼    │
│  ┌──────┐┌──────┐┌──────┐  ┌──────┐┌──────┐       ┌──────┐  │
│  │Audit ││Analyt││Search│  │Email ││ML    │  ...  │Custom│  │
│  │Trail ││ics   ││Index │  │Alert ││Train │       │Worker│  │
│  └──┬───┘└──┬───┘└──┬───┘  └──────┘└──────┘       └──────┘  │
│     │       │       │                                         │
│     ▼       ▼       ▼                                         │
│  ┌──────┐┌──────┐┌──────┐                                    │
│  │  DB  ││Metric││ ES   │                                     │
│  │ Audit││ DB   ││Search│                                     │
│  └──────┘└──────┘└──────┘                                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 4 Use Cases Chi Tiết

### 1️⃣ Event Sourcing & Audit Trail

**Mục đích**: Lưu tất cả events để có thể truy vết và debug

**Implementation**:
- ✅ `AuditTrailConsumer` - Listen Kafka events
- ✅ `AuditLog` entity - Store in database
- ✅ `AuditLogRepository` - Query audit logs

**Use Cases**:
```sql
-- Tìm tất cả hoạt động của user
SELECT * FROM audit_logs WHERE user_id = 'user_123';

-- Tìm errors trong 24h qua
SELECT * FROM audit_logs 
WHERE event_type LIKE '%ERROR%' 
AND timestamp >= NOW() - INTERVAL '24 hours';

-- Trace một conversation
SELECT * FROM audit_logs 
WHERE conversation_id = 'conv_abc' 
ORDER BY timestamp;
```

**Benefits**:
- Compliance: Meet regulatory requirements
- Security: Track all user actions
- Debug: Reproduce production issues
- Analytics: User behavior analysis

---

### 2️⃣ Async Background Processing

**Mục đích**: Xử lý heavy tasks không ảnh hưởng real-time latency

**Architecture**:
```
Real-time path:  WebSocket → Redis → Client (< 100ms)
                                      ✅ User sees response
                                      
Background path: Kafka → Workers → Storage (seconds/minutes)
                 ├─▶ Search indexing
                 ├─▶ Analytics aggregation  
                 ├─▶ ML training data
                 └─▶ Email notifications
```

**Implementation**:
- ✅ `AnalyticsConsumer` - Real-time metrics
- Custom consumers - Your specific needs

**Example Workers**:

```java
// 1. Search Indexer
@KafkaListener(topics = "chat-events")
public void indexMessage(String event) {
    // Index in Elasticsearch for search
    elasticsearchClient.index(message);
}

// 2. ML Training Data
@KafkaListener(topics = "chat-events")
public void collectTrainingData(String event) {
    // Save to S3 for ML training
    s3Client.putObject(trainingData);
}

// 3. Email Alerts
@KafkaListener(topics = "stream-events")
public void sendAlerts(String event) {
    if (isError(event)) {
        emailService.send("ops-team@example.com", alert);
    }
}
```

**Benefits**:
- Performance: No impact on real-time latency
- Scalability: Scale workers independently
- Reliability: Retry on failure
- Flexibility: Add new workers anytime

---

### 3️⃣ Guaranteed Message Delivery

**Mục đích**: Không mất data khi node crash

**How It Works**:

```
Producer Side:
1. Publish event to Kafka
2. Wait for ACK from all replicas (acks=all)
3. Retry if failed (retries=∞)
4. Idempotency prevents duplicates

Consumer Side:
1. Read event from Kafka
2. Process business logic
3. Save to database
4. Commit offset (manual ack)
   
   If step 2-3 fail:
   → Don't commit
   → Kafka will redeliver
   → Auto-retry with exponential backoff
```

**Configuration**:
```java
// Producer (already configured)
acks=all                    // Wait for all replicas
retries=Integer.MAX_VALUE   // Retry forever
enable.idempotence=true     // Prevent duplicates

// Consumer (already configured)
enable.auto.commit=false    // Manual commit
auto.offset.reset=earliest  // Start from beginning
```

**Delivery Guarantees**:
- ✅ At-Least-Once (current implementation)
- Message guaranteed to be delivered
- May have duplicates → Need idempotency in consumer

**Benefits**:
- Reliability: No data loss
- Durability: Survives node crashes
- Consistency: Same data across all nodes

---

### 4️⃣ Stream Replay & Recovery

**Mục đích**: Time travel để debug, rebuild, hoặc test

**Use Cases**:

#### A. Debug Production Issue

```java
// Replay specific session to see what happened
List<Map<String, Object>> events = 
    replayService.replaySession("session_123");

// Timeline of events:
// 10:30:00 - SESSION_STARTED
// 10:30:02 - CHUNK_RECEIVED (index=0)
// 10:30:03 - STREAM_ERROR (timeout)
// 10:30:05 - RECOVERY_ATTEMPT (success)
// 10:30:10 - STREAM_COMPLETED
```

#### B. Rebuild Index

```java
// Rebuild search index from all historical data
replayService.replayFromTimestamp(
    "chat-events",
    Instant.now().minus(Duration.ofDays(7)),
    event -> {
        if ("CHAT_MESSAGE".equals(event.get("eventType"))) {
            elasticsearchClient.index(event);
        }
    }
);
```

#### C. Backfill New Consumer

```java
// New feature needs historical data
@KafkaListener(
    topics = "chat-events",
    groupId = "new-feature-consumer"  // New group starts from earliest
)
public void processHistoricalData(String event) {
    // Will process all historical events first
    // Then continue with real-time events
}
```

#### D. Test with Production Data

```java
// Replay to staging environment for testing
replayService.replayFromOffset(
    "chat-events",
    partition=0,
    fromOffset=1000,
    toOffset=2000,
    event -> {
        // Test new code with production data
        newFeature.process(event);
    }
);
```

**Benefits**:
- Debug: See exact sequence of events
- Recovery: Rebuild corrupted data
- Testing: Use production data safely
- Backfill: Populate new consumers

---

## 🔄 Message Flow

### Scenario: User gửi message "Hello"

```
Step 1: User Input
├─▶ Frontend sends "Hello" via WebSocket
│
Step 2: Java Server Receives
├─▶ ChatOrchestrator processes request
│   ├─▶ Publish to Redis PubSub (real-time)
│   │   └─▶ WebSocket delivers to client (50ms)
│   │       └─▶ ✅ User sees "Hello" instantly
│   │
│   └─▶ Publish to Kafka (async, no waiting)
│       ├─▶ Event: SESSION_STARTED
│       ├─▶ Event: CHUNK_RECEIVED (x10)
│       └─▶ Event: STREAM_COMPLETED
│
Step 3: Kafka Distributes (background, parallel)
├─▶ AuditTrailConsumer
│   └─▶ Save to audit_logs table
│
├─▶ AnalyticsConsumer
│   └─▶ Update metrics (latency, chunks, etc.)
│
├─▶ SearchIndexerConsumer (custom)
│   └─▶ Index in Elasticsearch
│
├─▶ MLTrainingConsumer (custom)
│   └─▶ Save to S3 for training
│
└─▶ NotificationConsumer (custom)
    └─▶ Send alerts if error

Total User Latency: 50ms (Kafka processing happens in background)
```

---

## 📈 Metrics & Monitoring

### Kafka Metrics

```
Producer Metrics:
├─ kafka.producer.record_send_rate    // Events/second
├─ kafka.producer.batch_size_avg      // Batch efficiency
└─ kafka.producer.compression_rate    // Compression ratio

Consumer Metrics:
├─ kafka.consumer.records_consumed_rate  // Processing rate
├─ kafka.consumer.lag                    // Events behind
└─ kafka.consumer.commit_latency         // Ack speed

Application Metrics:
├─ analytics.sessions.started
├─ analytics.streams.completed
├─ analytics.stream.duration
├─ analytics.chunks.received
├─ analytics.errors.stream
└─ analytics.recovery.success
```

### Monitoring Dashboard

```bash
# Kafka UI (visual monitoring)
http://localhost:8090

# View:
├─ Topics (chat-events, stream-events)
├─ Messages in real-time
├─ Consumer groups and lag
├─ Partitions and offsets
└─ Broker health
```

---

## ⚡ Performance

### Latency Impact

```
┌────────────────────────────────────────────────────────────┐
│  Without Kafka                                             │
├────────────────────────────────────────────────────────────┤
│  WebSocket → Process → Send to client                     │
│            └─▶ 50ms ◀─┘                                    │
│                                                            │
│  Total: 50ms ✅                                            │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│  With Kafka (Async Publishing)                            │
├────────────────────────────────────────────────────────────┤
│  WebSocket → Process → Send to client                     │
│            └─▶ 50ms ◀─┘                                    │
│                 │                                           │
│                 └─▶ Kafka.send() (fire and forget, < 1ms) │
│                     └─▶ Background workers process later   │
│                                                            │
│  Total: 51ms ✅ (negligible impact)                        │
└────────────────────────────────────────────────────────────┘
```

### Throughput

```
Kafka Performance (single broker, 3 partitions):
├─ Write: 10,000 events/second
├─ Read:  30,000 events/second (3 consumers)
└─ Storage: 1GB = ~500,000 events

Our Usage (POC):
├─ Write: ~100 events/second (low volume)
├─ Read:  ~300 events/second (3 consumers x 100)
└─ Headroom: 100x capacity available ✅
```

---

## 🛠️ Configuration

### Current Setup (docker-compose.yml)

```yaml
kafka:
  environment:
    # Retention
    KAFKA_LOG_RETENTION_HOURS: 168        # 7 days
    KAFKA_LOG_RETENTION_BYTES: 1073741824 # 1GB
    
    # Replication (single node = 1)
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    
    # Auto-create topics
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

java-websocket:
  environment:
    KAFKA_ENABLED: true
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

### Production Recommendations

```yaml
kafka:
  environment:
    # Longer retention for event sourcing
    KAFKA_LOG_RETENTION_HOURS: 8760  # 1 year
    
    # Higher replication for reliability
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
    
    # More partitions for scalability
    KAFKA_NUM_PARTITIONS: 6
```

---

## 🎓 Best Practices

### Do's ✅

1. **Use Manual Acknowledgment**
   - Control exactly when message is committed
   - Prevent data loss on processing failure

2. **Implement Idempotency**
   ```java
   if (!isAlreadyProcessed(messageId)) {
       processEvent(event);
       markAsProcessed(messageId);
   }
   ```

3. **Monitor Consumer Lag**
   - Alert if lag > threshold
   - Indicates consumer slower than producer

4. **Use Dead Letter Queue**
   ```java
   catch (Exception e) {
       if (isUnrecoverable(e)) {
           sendToDeadLetterQueue(event);
       }
   }
   ```

5. **Version Your Events**
   ```json
   {
     "eventType": "STREAM_COMPLETED",
     "version": "1.0",
     "data": {...}
   }
   ```

### Don'ts ❌

1. **Don't Auto-commit**
   - Lose control of delivery guarantees
   - May lose messages on failure

2. **Don't Block in Listener**
   ```java
   // ❌ Bad
   Thread.sleep(10000);  // Causes rebalancing
   
   // ✅ Good
   executorService.submit(() -> heavyWork());
   ```

3. **Don't Ignore Errors**
   ```java
   // ❌ Bad
   catch (Exception e) {
       log.error("Error", e);
       ack.acknowledge();  // Message lost!
   }
   
   // ✅ Good
   catch (Exception e) {
       log.error("Error", e);
       throw e;  // Kafka will retry
   }
   ```

4. **Don't Store Large Payloads**
   ```java
   // ❌ Bad
   event.put("fullContent", largeString);  // > 1MB
   
   // ✅ Good
   event.put("contentRef", s3Key);  // Reference pattern
   ```

---

## 📚 Summary

| Aspect | Redis PubSub | Kafka | Combined (Best) |
|--------|--------------|-------|-----------------|
| Real-time Delivery | ✅ < 100ms | ❌ Slower | ✅ Redis for real-time |
| Guaranteed Delivery | ❌ Can lose | ✅ At-least-once | ✅ Kafka for guarantees |
| Audit Trail | ❌ No history | ✅ Event log | ✅ Kafka for audit |
| Replay Events | ❌ No replay | ✅ Time travel | ✅ Kafka for replay |
| Scalability | ⚠️ Limited | ✅ Partitions | ✅ Best of both |
| Latency Impact | ✅ None | ⚠️ Adds latency | ✅ Async = no impact |

### The Perfect Combo 🎯

```
Redis PubSub:
└─▶ Real-time delivery (< 100ms latency)
    └─▶ User experience ✅

Kafka:
├─▶ Event sourcing (audit trail)
├─▶ Analytics (metrics)
├─▶ Async processing (no latency impact)
├─▶ Guaranteed delivery (no data loss)
└─▶ Stream replay (debug & recovery)
    └─▶ Reliability & observability ✅
```

**Result**: Fast real-time experience + Complete observability + Zero data loss! 🚀
