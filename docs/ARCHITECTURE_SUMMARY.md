# 📚 Tài Liệu Kiến Trúc - Navigation Guide

## 🎯 Đọc tài liệu nào?

### Cho người mới bắt đầu

**1. Start here:** [README.md](../README.md)
- Quick start guide
- Cách chạy project (single-node hoặc multi-node)
- Tính năng chính
- API endpoints

**2. Hiểu kiến trúc tổng quan:** [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md)
- 🎨 Mermaid diagrams đầy đủ
- 🔄 Flow diagrams chi tiết
- 💻 Code implementation details
- ⭐ **RECOMMENDED - Tài liệu chính thức và đầy đủ nhất**

### Cho developers

**3. Implementation details:** [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md)
- Components chi tiết (Frontend, Backend, AI Service)
- Redis data structures
- Kafka event sourcing
- Best practices & lessons learned
- Security considerations
- Deployment guide

**4. Kafka specifics:**
- [KAFKA_SUMMARY.md](./KAFKA_SUMMARY.md) - Overview về Kafka integration
- [KAFKA_USAGE_GUIDE.md](./KAFKA_USAGE_GUIDE.md) - Practical usage guide
- [KAFKA_MULTI_NODE_ARCHITECTURE.md](./KAFKA_MULTI_NODE_ARCHITECTURE.md) - Kafka trong multi-node

### Cho architects

**5. Architecture decisions:** [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md)
- Why no sticky sessions?
- Distributed session ownership pattern
- Performance analysis
- Scalability characteristics
- Future enhancements

---

## 🗺️ Document Map

```
/workspace
├── README.md                           ⭐ START HERE - Quick start
├── COMPREHENSIVE_ARCHITECTURE.md       ⭐⭐⭐ MAIN DOCUMENT - Chi tiết đầy đủ
├── ARCHITECTURE.md                     📋 Reference (superseded)
│
└── docs/
    ├── ARCHITECTURE_SUMMARY.md         📍 YOU ARE HERE
    ├── KAFKA_SUMMARY.md                📊 Kafka overview
    ├── KAFKA_USAGE_GUIDE.md            🔧 Kafka practical guide
    └── KAFKA_MULTI_NODE_ARCHITECTURE.md 🏗️ Kafka multi-node details
```

---

## 🔑 Key Concepts (Quick Reference)

### No Sticky Sessions
- NGINX dùng **round-robin** (không phải ip_hash)
- Session ownership qua **Redis SETNX** (distributed lock)
- Bất kỳ node nào cũng có thể serve request
- Perfect load distribution

### Server-Side Accumulation
- Python AI accumulate content trên server
- Client chỉ cần display (không accumulate)
- Tránh duplicate text
- Reliable khi miss chunks

### Async Kafka
- Kafka publish **không block** real-time path
- Events published async sau khi send to WebSocket
- Zero impact on user-perceived latency
- Event sourcing cho audit trail

### Redis Roles
1. **PubSub**: Real-time streaming (< 100ms)
2. **History**: Persistent storage (TTL 24h)
3. **Ownership**: Distributed locks (TTL 10min)
4. **State**: Cancellation flags, active streams

---

## 📊 Architecture Highlights

### Multi-Node Deployment

```
Client → NGINX (Round-Robin)
           ↓
    Java Nodes (3x)
    ├─ Claim ownership via Redis
    ├─ Subscribe PubSub if owner
    └─ Forward to WebSocket clients
           ↓
    Python AI (3x)
    └─ Generate & stream response
           ↓
    Redis (Shared)
    ├─ PubSub channels
    ├─ History storage
    └─ Session ownership
           ↓
    Kafka (Optional)
    └─ Event sourcing & analytics
```

### Key Flows

**Normal Flow:**
1. User send message
2. NGINX → Java node (round-robin)
3. Java claim ownership (Redis SETNX)
4. Java → Python AI (load balanced)
5. Python stream via Redis PubSub
6. Java forward to WebSocket
7. Kafka events (async)

**Reload Flow:**
1. User reload → disconnect
2. AI continues streaming
3. Chunks saved to history
4. User reconnect → any node
5. Load history (partial message included)
6. Continue receiving new chunks
7. Zero data loss ✅

---

## 🎨 Mermaid Diagrams Available

Xem trong [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md):

1. **Architecture Overview** - Full system diagram
2. **Normal Streaming Flow** - Sequence diagram
3. **Reload During Streaming** - Sequence diagram
4. **Distributed Session Ownership** - Sequence diagram
5. **Multi-Node Load Distribution** - Graph diagram
6. **Redis Data Structures** - Data model diagram
7. **Kafka Event Flow** - Event sourcing diagram
8. **Performance Timeline** - Gantt chart
9. **Scalability Characteristics** - Comparison diagram

---

## 🚀 Quick Commands

### Start Single Node
```bash
docker-compose up --build

# Access:
# - App: http://localhost:3000
# - API: http://localhost:8080
# - H2 Console: http://localhost:8080/h2-console
```

### Start Multi-Node (3 nodes each)
```bash
docker-compose -f docker-compose.multi-node.yml up --build

# Access:
# - App: http://localhost:3000
# - NGINX LB: http://localhost:8080
# - Java Nodes: 8081, 8082, 8083
# - Python Nodes: 8001, 8002, 8003
```

### With Kafka UI (Debug)
```bash
docker-compose --profile debug up
# Kafka UI: http://localhost:8090
```

---

## 📞 Need Help?

### Tôi muốn...

**...hiểu kiến trúc tổng quan:**
→ Đọc [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md) - Section "Kiến Trúc Tổng Quan"

**...xem flow chi tiết:**
→ Đọc [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md) - Section "Flow Chi Tiết"

**...implement tính năng mới:**
→ Đọc [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md) - Section "Chi Tiết Implementation"

**...hiểu về Kafka:**
→ Đọc [KAFKA_SUMMARY.md](./KAFKA_SUMMARY.md) hoặc [KAFKA_USAGE_GUIDE.md](./KAFKA_USAGE_GUIDE.md)

**...deploy production:**
→ Đọc [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md) - Section "Deployment Guide"

**...optimize performance:**
→ Đọc [COMPREHENSIVE_ARCHITECTURE.md](../COMPREHENSIVE_ARCHITECTURE.md) - Section "Performance Analysis"

---

## ✅ What's Different from Other Docs?

### vs ARCHITECTURE.md (Old)
- ❌ ARCHITECTURE.md: Đề cập sticky session (sai)
- ✅ COMPREHENSIVE_ARCHITECTURE.md: Giải thích distributed ownership (đúng)
- ❌ ARCHITECTURE.md: Ít diagrams
- ✅ COMPREHENSIVE_ARCHITECTURE.md: Nhiều Mermaid diagrams
- 📋 ARCHITECTURE.md giờ chỉ là reference, point to COMPREHENSIVE_ARCHITECTURE.md

### vs README.md
- README.md: Quick start, high-level overview
- COMPREHENSIVE_ARCHITECTURE.md: Deep dive, implementation details
- README.md: Cho users
- COMPREHENSIVE_ARCHITECTURE.md: Cho developers & architects

### vs Kafka docs
- KAFKA_*.md: Focus on Kafka specifically
- COMPREHENSIVE_ARCHITECTURE.md: Full system architecture
- Use both: Kafka docs for Kafka details, COMPREHENSIVE for overall picture

---

## 🎓 Learning Path

### Level 1: Beginner
1. Read [README.md](../README.md) - Quick start
2. Run single-node setup
3. Test basic chat functionality
4. Read "Kiến Trúc Tổng Quan" in COMPREHENSIVE_ARCHITECTURE.md

### Level 2: Intermediate
1. Read "Flow Chi Tiết" section
2. Run multi-node setup
3. Test reload during streaming
4. Understand session ownership pattern
5. Read Kafka docs

### Level 3: Advanced
1. Read "Chi Tiết Implementation" section
2. Study code với tài liệu
3. Understand all flows and edge cases
4. Read "Performance Analysis"
5. Implement custom features

---

## 📈 Document Version History

- **v1.0** (Old): ARCHITECTURE.md - Mentioned sticky sessions (incorrect)
- **v2.0** (Current): COMPREHENSIVE_ARCHITECTURE.md - Distributed ownership pattern
- **Status**: COMPREHENSIVE_ARCHITECTURE.md is the official document

---

**Last Updated:** 2024-01-11  
**Maintained By:** Architecture Team  
**Questions?** Read COMPREHENSIVE_ARCHITECTURE.md first! 📚
