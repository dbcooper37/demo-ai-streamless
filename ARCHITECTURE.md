# 🏗️ Kiến Trúc PoC: AI Streaming Chat với Persistent History

> ⚠️ **Note**: Tài liệu này là phiên bản cũ. Để xem tài liệu kiến trúc chi tiết và cập nhật nhất với Mermaid diagrams, vui lòng xem **[COMPREHENSIVE_ARCHITECTURE.md](./COMPREHENSIVE_ARCHITECTURE.md)**

---

## 📋 Tổng quan dự án

### Vấn đề giải quyết (Problem Statement)

**Bài toán:** Làm thế nào để xây dựng hệ thống chat AI streaming có khả năng:
1. ✅ Stream real-time response từ AI đến nhiều clients đồng thời
2. ✅ Lưu trữ và khôi phục lịch sử chat khi user reload trang
3. ✅ Xử lý reconnection và recovery khi mất kết nối
4. ✅ Scale horizontal với multi-node deployment
5. ✅ Đảm bảo message ordering và consistency

### Giải pháp (Solution)

PoC này triển khai một **Event-Driven Microservices Architecture** với:
- **Real-time Messaging**: Redis PubSub cho streaming communication
- **Persistent Storage**: Redis + H2 Database cho history
- **Event Sourcing**: Kafka cho audit trail và analytics
- **WebSocket**: Bidirectional communication với auto-reconnection
- **Load Balancing**: NGINX round-robin (NO sticky sessions)
- **Distributed Coordination**: Redis SETNX cho session ownership

---

## 🎯 Mục tiêu PoC

### Chứng minh (Proof of Concept)

1. **Streaming Architecture**
   - AI response được stream real-time qua WebSocket
   - Chunk-based transmission với low latency
   - Support concurrent users và sessions

2. **Persistence & Recovery**
   - Chat history được lưu trữ persistent
   - Auto-recovery khi reload page
   - Reconnection handling với resume capability

3. **Distributed System**
   - Multi-node deployment (3 Java nodes + 3 Python nodes)
   - Load balancing với NGINX round-robin
   - **Session ownership qua Redis distributed locks** (KHÔNG dùng sticky sessions)

4. **Scalability**
   - Horizontal scaling của từng component
   - Stateless services với shared state trong Redis
   - Message queue để decouple services

---

## 🏛️ Kiến trúc tổng quan

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            CLIENT LAYER                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │   React Frontend (Port 3000)                                      │  │
│  │   - WebSocket Client với Auto-Reconnection                       │  │
│  │   - State Management (useState, useEffect)                       │  │
│  │   - History Loading & Display                                    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ WebSocket (ws://)
                                    │ REST API (http://)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         LOAD BALANCER LAYER                              │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │   NGINX Load Balancer (Port 8080)                                │  │
│  │   - Round-Robin (NO sticky sessions)                             │  │
│  │   - Health Checks                                                │  │
│  │   - WebSocket Upgrade Support                                    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      BACKEND SERVICE LAYER                               │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐             │
│  │ Java WS Node1│    │ Java WS Node2│    │ Java WS Node3│             │
│  │  Port 8081   │    │  Port 8082   │    │  Port 8083   │             │
│  │              │    │              │    │              │             │
│  │ - WebSocket  │    │ - WebSocket  │    │ - WebSocket  │             │
│  │ - Ownership  │    │ - Ownership  │    │ - Ownership  │             │
│  │ - Streaming  │    │ - Streaming  │    │ - Streaming  │             │
│  │ - Recovery   │    │ - Recovery   │    │ - Recovery   │             │
│  └──────────────┘    └──────────────┘    └──────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       AI SERVICE LAYER                                   │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐             │
│  │ Python AI #1 │    │ Python AI #2 │    │ Python AI #3 │             │
│  │  Port 8001   │    │  Port 8002   │    │  Port 8003   │             │
│  │              │    │              │    │              │             │
│  │ - FastAPI    │    │ - FastAPI    │    │ - FastAPI    │             │
│  │ - AI Logic   │    │ - AI Logic   │    │ - AI Logic   │             │
│  │ - Streaming  │    │ - Streaming  │    │ - Streaming  │             │
│  └──────────────┘    └──────────────┘    └──────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      INFRASTRUCTURE LAYER                                │
│  ┌────────────────────┐  ┌────────────────────┐  ┌─────────────────┐  │
│  │    Redis (6379)    │  │   Kafka (9092)     │  │ H2 Database     │  │
│  │                    │  │                    │  │                 │  │
│  │ - PubSub Channel   │  │ - Event Sourcing   │  │ - Message Store │  │
│  │ - History Storage  │  │ - Audit Trail      │  │ - Session Store │  │
│  │ - Session Owner    │  │ - Analytics Events │  │ - Metadata      │  │
│  │ - Distributed Lock │  │ - KRaft Mode       │  │                 │  │
│  └────────────────────┘  └────────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🔑 Key Design Decisions

### 1. No Sticky Sessions

**Why not sticky sessions?**
- ❌ Uneven load distribution
- ❌ Problematic khi node failure
- ❌ Không flexible cho auto-scaling
- ❌ Client affinity issues

**Our approach: Distributed Session Ownership**
```java
// Claim ownership atomically
String ownerKey = "session:owner:" + sessionId;
Boolean claimed = redisTemplate.opsForValue()
    .setIfAbsent(ownerKey, nodeId, Duration.ofMinutes(10));

if (claimed) {
    // This node owns the session, process it
    subscribeToChannel(sessionId);
} else {
    // Another node owns it, skip processing
    log.info("Session already owned by another node");
}
```

**Benefits:**
- ✅ Perfect load distribution (NGINX round-robin)
- ✅ Automatic failover (TTL expires, another node can claim)
- ✅ No duplicate processing
- ✅ Easy to scale

---

### 2. Server-Side Content Accumulation

**Client nhận accumulated content từ server:**
```python
# Python AI Service
accumulated_content = ""
for chunk in words:
    accumulated_content += chunk
    
    # Send accumulated content, not just chunk
    message = {
        "content": accumulated_content,  # Full text so far
        "chunk": chunk,                   # Current word only
        "is_complete": False
    }
    redis_client.publish_message(session_id, message)
```

**Client chỉ cần display:**
```javascript
// Frontend - NO accumulation needed
setMessages(prev => {
    updated[index] = message;  // Use message.content directly
    return updated;
});
```

**Why?**
- ✅ Simpler client code
- ✅ No risk of text duplication
- ✅ Reliable trong trường hợp miss chunks
- ✅ Consistent across reconnections

---

### 3. Async Kafka Publishing

**Kafka KHÔNG block real-time path:**
```java
// Publish chunk to WebSocket first (real-time)
context.callback.onChunk(chunk);

// Then publish to Kafka asynchronously (no waiting)
if (eventPublisher != null) {
    CompletableFuture.runAsync(() -> {
        eventPublisher.publishChunkReceived(sessionId, chunk);
    });
}
```

**Performance:**
- Real-time path: ~50ms
- Kafka publish: Async, không impact latency
- Total user-perceived latency: ~50ms ✅

---

## 📊 Chi Tiết Components

> **Để xem chi tiết đầy đủ về implementation, flow diagrams, và best practices, vui lòng xem:**
> 
> **👉 [COMPREHENSIVE_ARCHITECTURE.md](./COMPREHENSIVE_ARCHITECTURE.md)**
>
> Tài liệu đó bao gồm:
> - 🎨 Mermaid diagrams chi tiết
> - 🔄 Sequence diagrams cho các flows
> - 💻 Code examples với explanations
> - 📈 Performance analysis
> - 🔐 Security recommendations
> - 🚀 Deployment guides
> - 📚 Best practices & lessons learned

---

## 🔄 Quick Flow Overview

### Normal Streaming Flow

1. User gửi message qua WebSocket
2. NGINX route đến Java node (round-robin)
3. Java node claim ownership qua Redis SETNX
4. Java forward request đến Python AI (load balanced)
5. Python generate và publish chunks đến Redis PubSub
6. Java node (owner) receive chunks và forward to client
7. Kafka events published async (không block)
8. Final message saved to history + database
9. Ownership released

### Reload During Streaming Flow

1. User reload page → WebSocket disconnect
2. AI vẫn tiếp tục stream (doesn't know about disconnect)
3. Chunks vẫn được saved to Redis history
4. User reconnect → NGINX route đến bất kỳ node nào
5. Node check ownership (another node owns it → passive mode)
6. Load toàn bộ history (including partial message)
7. Subscribe to PubSub for new chunks
8. Continue receiving real-time updates

**Result**: Zero data loss, seamless experience ✅

---

## 🎯 Kết luận

### Điểm mạnh của giải pháp

1. **No Sticky Sessions**
   - ✅ Pure round-robin load balancing
   - ✅ Better load distribution
   - ✅ Easier scaling
   - ✅ Automatic failover

2. **Real-time + Reliability**
   - ✅ Low latency (< 100ms TTFB)
   - ✅ No data loss on reload
   - ✅ Auto-reconnection
   - ✅ Event sourcing với Kafka

3. **Developer Experience**
   - ✅ Clean architecture
   - ✅ Easy to understand
   - ✅ Well-documented
   - ✅ Testable components

### Production-ready Checklist

- ✅ Core functionality
- ✅ Multi-node deployment
- ✅ Session ownership
- ✅ Event sourcing
- ✅ Monitoring hooks
- 🔄 HTTPS/WSS support (TODO)
- 🔄 Production auth (TODO)
- 🔄 Comprehensive tests (TODO)

---

## 📖 Related Documentation

- **[COMPREHENSIVE_ARCHITECTURE.md](./COMPREHENSIVE_ARCHITECTURE.md)** - Chi tiết đầy đủ với Mermaid diagrams
- **[README.md](./README.md)** - Quick start guide
- **[docs/KAFKA_SUMMARY.md](./docs/KAFKA_SUMMARY.md)** - Kafka usage summary
- **[docs/KAFKA_USAGE_GUIDE.md](./docs/KAFKA_USAGE_GUIDE.md)** - Kafka practical guide

---

**Document Version:** 2.0 (Superseded)  
**See Latest:** [COMPREHENSIVE_ARCHITECTURE.md](./COMPREHENSIVE_ARCHITECTURE.md)  
**Last Updated:** 2024-01-11  
**Status:** Reference Only - Use COMPREHENSIVE_ARCHITECTURE.md for current architecture
