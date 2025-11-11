# 🚀 Tài Liệu Kỹ Thuật: Hệ Thống Chat AI với Kiến Trúc Phân Tán và Streaming Real-time

Hệ thống chat AI sử dụng kiến trúc microservices phân tán với Spring Boot WebSocket, Python FastAPI, Redis PubSub, Apache Kafka, và React, hỗ trợ streaming real-time, session persistence, và khả năng phục hồi hoàn toàn khi người dùng reload trang trong quá trình streaming.

## 🎯 Giải Quyết Vấn Đề Gì?

**Khi người dùng đang nhận streaming response từ AI và reload trang:**
- ✅ Vẫn thấy toàn bộ lịch sử chat cũ
- ✅ Tiếp tục nhận streaming mới (nếu AI vẫn đang trả lời)
- ✅ Không mất dữ liệu
- ✅ Trải nghiệm mượt mà như chưa hề reload

## 🏗️ Kiến Trúc

```
React Client (WebSocket)
         ↓
    NGINX Load Balancer
         ↓
Java WebSocket Servers (Multi-Node)
    ↙        ↓        ↘
Redis PubSub   Kafka    Python AI Services
(Real-time)   (Audit)   (Streaming)
```

**Điểm nổi bật:**
- **Không cần Sticky Session**: NGINX dùng round-robin thuần túy, session ownership quản lý qua Redis
- **Accumulation trên Server**: Client chỉ cần hiển thị, tránh duplicate text
- **Kafka Async**: Không ảnh hưởng đến độ trễ real-time
- **Auto-Reconnection**: WebSocket tự động kết nối lại

## 🛠️ Tech Stack

| Thành Phần | Công Nghệ |
|------------|-----------|
| Frontend | React 18, Vite, WebSocket |
| Backend | Java 17, Spring Boot, WebSocket |
| AI Service | Python 3.11, FastAPI |
| Message Broker | Redis 7 (PubSub), Apache Kafka (KRaft) |
| Load Balancer | NGINX |
| Deployment | Docker Compose |

## 🚀 Chạy Nhanh (Quick Start)

### Yêu Cầu
- Docker & Docker Compose
- Ports: 3000, 6379, 8080, 9092

### 1. Single Instance (Phát Triển/Test)

```bash
# Clone repository
git clone <repository-url>
cd demo-ai-streamless

# Khởi động (1 instance mỗi service)
docker-compose up --build

# Truy cập ứng dụng
open http://localhost:3000
```

**Services:**
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
- Python AI: http://localhost:8000

### 2. Multi-Node (Production/Load Testing)

```bash
# Khởi động (3 nodes mỗi service)
docker-compose -f docker-compose.multi-node.yml up --build

# Truy cập ứng dụng
open http://localhost:3000
```

**Services:**
- Frontend: http://localhost:3000
- NGINX LB: http://localhost:8080 (→ 3 Java nodes)
- Java Nodes: 8081, 8082, 8083
- Python Nodes: 8001, 8002, 8003

### 3. Với Kafka UI (Debug Mode)

```bash
docker-compose --profile debug up

# Kafka UI
open http://localhost:8090
```

⏱️ **Thời gian build**: ~2-3 phút lần đầu

## 🎮 Test Tính Năng

### Test 1: Streaming Cơ Bản
1. Mở http://localhost:3000
2. Gửi tin nhắn: "Xin chào"
3. Xem AI response streaming từng chữ

### Test 2: Reload Trong Khi Streaming ⭐
1. Gửi tin nhắn dài: "Hãy nói về streaming và reload"
2. **Trong khi AI đang trả lời**, nhấn F5 hoặc Ctrl+R
3. ✅ Kết quả: Thấy lịch sử chat cũ + tin nhắn AI tiếp tục streaming

### Test 3: Multiple Sessions
1. Mở tab mới với cùng URL
2. Session ID khác nhau (lưu trong localStorage)
3. Mỗi session có lịch sử riêng

### Test 4: Auto-Reconnection
```bash
# Tắt backend
docker stop demo-java-websocket

# → UI hiện "Đang kết nối lại..."

# Bật lại
docker start demo-java-websocket

# → WebSocket tự động kết nối và load lịch sử
```

## 📡 API Endpoints

### Python AI Service (Port 8000)
```bash
# Gửi tin nhắn
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test123",
    "message": "Xin chào",
    "user_id": "user1"
  }'

# Lấy lịch sử
curl http://localhost:8000/history/test123
```

### Java WebSocket Server (Port 8080)
```javascript
// WebSocket connection
const ws = new WebSocket('ws://localhost:8080/ws/chat?session_id=xxx&user_id=yyy');

// Message format
{
  "type": "message",
  "data": {
    "message_id": "uuid",
    "role": "assistant",
    "content": "Hello...",
    "is_complete": false
  }
}
```

## 📚 Tài Liệu Chi Tiết

| Tài Liệu | Nội Dung |
|----------|----------|
| [DOCUMENTATION.md](DOCUMENTATION.md) | Tài liệu kiến trúc kỹ thuật chi tiết, sequence diagrams, implementation details |

## 🎯 Tính Năng Chính

✅ **Streaming Real-time**: AI response được stream theo từng chunk  
✅ **Persistent History**: Lịch sử chat lưu trong Redis  
✅ **Auto Reconnection**: WebSocket tự động kết nối lại  
✅ **Resume on Reload**: Reload trang vẫn thấy lịch sử + tiếp tục streaming  
✅ **Session Management**: Mỗi session có lịch sử riêng  
✅ **Multi-Node Support**: Hỗ trợ triển khai multi-node với load balancing  
✅ **Event Sourcing**: Kafka lưu trữ audit trail và analytics  
✅ **No Sticky Session**: Distributed session ownership qua Redis  

## 🎓 Học Được Gì

1. **Redis PubSub**: Real-time messaging giữa các services
2. **WebSocket**: Implement WebSocket với auto-reconnection
3. **Streaming Architecture**: Thiết kế hệ thống streaming với persistence
4. **Session Management**: Quản lý sessions với Redis distributed locks
5. **Event Sourcing**: Kafka cho audit trail và analytics
6. **Multi-Node Coordination**: Không cần sticky session
7. **Docker Orchestration**: Multi-container application deployment

## 📊 Giám Sát & Debug

### Xem Logs
```bash
# Backend logs
docker logs demo-java-websocket -f

# AI service logs
docker logs demo-python-ai -f

# Metrics
docker logs demo-java-websocket | grep "\[METRIC\]"
```

### H2 Console (Audit Logs)
```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:websocketdb
Username: sa
Password: (để trống)

Query:
SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100;
```

### Kafka UI (Debug Mode)
```
URL: http://localhost:8090
Topics: chat-events, stream-events
```

## 🔧 Cấu Hình

### Environment Variables

**Java WebSocket Server:**
```yaml
SPRING_DATA_REDIS_HOST: redis
SPRING_KAFKA_ENABLED: true
NODE_ID: ws-node-1
CACHE_L1_MAX_SIZE: 10000
```

**Python AI Service:**
```yaml
REDIS_HOST: redis
NODE_ID: ai-node-1
LOG_LEVEL: INFO
```

**Frontend:**
```yaml
VITE_WS_URL: ws://localhost:8080/ws/chat
VITE_API_URL: http://localhost:8080/api
```

## 🏗️ Cấu Trúc Project

```
demo-ai-streamless/
├── frontend/                  # React frontend
│   ├── src/
│   │   ├── hooks/            # useWebSocket, useChat
│   │   └── components/       # UI components
│   └── Dockerfile
│
├── java-websocket-server/    # Java Spring Boot WebSocket
│   ├── src/main/java/com/demo/websocket/
│   │   ├── config/           # WebSocket, Redis, Kafka config
│   │   ├── handler/          # WebSocket handler
│   │   ├── service/          # Business logic
│   │   ├── consumer/         # Kafka consumers
│   │   └── infrastructure/   # Orchestration & recovery
│   └── pom.xml
│
├── python-ai-service/        # Python FastAPI AI service
│   ├── app.py               # REST API
│   ├── ai_service.py        # AI streaming logic
│   └── redis_client.py      # Redis operations
│
├── docs/                     # Chi tiết tài liệu
│   ├── ARCHITECTURE_SUMMARY.md
│   ├── KAFKA_SUMMARY.md
│   └── KAFKA_USAGE_GUIDE.md
│
├── docker-compose.yml        # Single-node setup
└── docker-compose.multi-node.yml  # Multi-node setup
```

## 🔐 Bảo Mật (Production)

> ⚠️ **Lưu ý**: Đây là demo/PoC. Khi triển khai production, cần:

- [ ] HTTPS/WSS cho tất cả connections
- [ ] JWT authentication đầy đủ
- [ ] Rate limiting
- [ ] Input validation và sanitization
- [ ] CORS configuration phù hợp
- [ ] Redis password authentication
- [ ] Kafka ACLs và encryption

## 🚨 Troubleshooting

### Port đã được sử dụng
```bash
# Kiểm tra ports
lsof -i :3000
lsof -i :8080

# Dừng containers cũ
docker-compose down
```

### Services không start
```bash
# Kiểm tra logs
docker-compose logs

# Build lại
docker-compose down
docker-compose up --build --force-recreate
```

### Kafka không hoạt động
```bash
# Xóa volume và restart
docker-compose down
docker volume rm demo_kafka-data
docker-compose up -d
```

## 📈 Performance

### Single Node Capacity
- ~1000 concurrent users
- ~5000 WebSocket connections
- TTFB < 120ms
- Streaming latency < 50ms/chunk

### Multi-Node Cluster (3 nodes)
- ~3000 concurrent users
- ~15000 WebSocket connections
- Linear scaling
