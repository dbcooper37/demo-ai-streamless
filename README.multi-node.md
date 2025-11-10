# AI Streaming Chat - Multi-Node Deployment

Hệ thống AI Streaming Chat với khả năng phục hồi kết nối và hỗ trợ triển khai trên nhiều node.

## 🏗️ Kiến trúc hệ thống

### Tổng quan
```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend                              │
│                     (WebSocket Client)                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    NGINX Load Balancer                       │
│              (ip_hash for sticky sessions)                   │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  WS Node 1       │  │  WS Node 2       │  │  WS Node 3       │
│  :8081           │  │  :8082           │  │  :8083           │
└──────────────────┘  └──────────────────┘  └──────────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Redis (PubSub + Cache)                   │
│  - Distributed Session Management                            │
│  - Stream Chunk Caching                                      │
│  - Inter-node Communication                                  │
│  - Distributed Locking (Redisson)                            │
└─────────────────────────────────────────────────────────────┘
                              ↑
        ┌─────────────────────┴─────────────────────┐
        │                                           │
┌──────────────────┐                      ┌──────────────────┐
│  AI Service 1    │                      │  AI Service 2    │
│  :8001           │                      │  :8002           │
└──────────────────┘                      └──────────────────┘
```

### Các thành phần chính

1. **Frontend**
   - WebSocket client kết nối tới NGINX
   - Hỗ trợ reconnection với recovery mechanism

2. **NGINX Load Balancer**
   - Load balance cho 3 WebSocket servers
   - Sử dụng ip_hash để đảm bảo sticky session
   - WebSocket upgrade support

3. **Java WebSocket Servers (3 nodes)**
   - Nhận streaming chunks từ AI qua Redis PubSub
   - Broadcast tới clients qua WebSocket
   - Distributed session management
   - Recovery service cho reconnection

4. **Python AI Services (2 nodes)**
   - Xử lý chat requests
   - Streaming response qua Redis PubSub
   - Có thể scale thêm nodes

5. **Redis**
   - PubSub cho inter-node communication
   - Caching stream chunks
   - Distributed session registry
   - Distributed locks (Redisson)

## 🚀 Deployment

### 1. Single Node (Development)

```bash
# Chạy với single instance
docker-compose up -d

# Các services:
# - Frontend: http://localhost:3000
# - WebSocket: ws://localhost:8080/ws/chat
# - AI Service: http://localhost:8000
# - Redis: localhost:6379
```

### 2. Multi-Node (Production)

```bash
# Chạy với multiple instances
docker-compose -f docker-compose.multi-node.yml up -d

# Các services:
# - Frontend: http://localhost:3000
# - NGINX LB: http://localhost:8080
# - WebSocket Node 1: localhost:8081
# - WebSocket Node 2: localhost:8082
# - WebSocket Node 3: localhost:8083
# - AI Service 1: http://localhost:8001
# - AI Service 2: http://localhost:8002
# - Redis: localhost:6379
```

### 3. Kiểm tra services

```bash
# Kiểm tra health của các services
curl http://localhost:8081/health  # WS Node 1
curl http://localhost:8082/health  # WS Node 2
curl http://localhost:8083/health  # WS Node 3
curl http://localhost:8001/health  # AI Service 1
curl http://localhost:8002/health  # AI Service 2

# Kiểm tra NGINX load balancer
curl http://localhost:8080/health
```

## 🔄 Recovery Mechanism

### Kịch bản 1: Mất kết nối WebSocket

```javascript
// Client tự động reconnect và phục hồi:
{
  "type": "reconnect",
  "messageId": "abc-123",
  "lastChunkIndex": 42
}

// Server trả về missing chunks:
{
  "type": "recovery_status",
  "status": "recovered",
  "chunksRecovered": 15
}
```

### Kịch bản 2: Switch giữa các nodes

Khi client reconnect tới một node khác:
1. Node mới kiểm tra Redis cache
2. Lấy session state từ distributed registry
3. Trả về missing chunks từ cache
4. Subscribe lại PubSub channel nếu streaming đang tiếp tục

### Kịch bản 3: Node failure

1. Client bị disconnect khi node fail
2. Client reconnect qua NGINX (được route tới node khác)
3. Node mới recover stream từ Redis cache
4. Streaming tiếp tục bình thường

## 📊 Distributed Architecture

### Session Management

```java
// Distributed session registry trong Redis
sessions:active -> Map<sessionId, userId>
sessions:user:{userId} -> Set<sessionId>

// Local session map trên mỗi node
ConcurrentHashMap<sessionId, WebSocketSessionWrapper>
```

### Stream Caching

```java
// Stream chunks trong Redis List
stream:chunks:{messageId} -> List<StreamChunk>

// Stream session metadata
stream:session:{sessionId} -> Hash {
    sessionId, messageId, userId, status, totalChunks, ...
}

// TTL: 5 phút sau khi complete
```

### Inter-node Communication

```java
// PubSub channels cho streaming
stream:channel:{sessionId}:chunk    -> StreamChunk events
stream:channel:{sessionId}:complete -> Completion events
stream:channel:{sessionId}:error    -> Error events
```

### Distributed Locking

```java
// Redisson distributed locks
stream:lock:{messageId} -> RLock

// Đảm bảo ordering khi append chunks từ nhiều nodes
```

## 🔧 Configuration

### Application Properties

```properties
# Redis connection
spring.data.redis.host=redis
spring.data.redis.port=6379

# Redisson configuration
# Cấu hình trong RedisConfig.java:
# - Connection pool: 64
# - Minimum idle: 10
# - Timeout: 3000ms
# - Retry: 3 attempts with 1500ms interval
```

### NGINX Configuration

```nginx
upstream websocket_backend {
    ip_hash;  # Sticky sessions
    server java-websocket-1:8080;
    server java-websocket-2:8080;
    server java-websocket-3:8080;
}
```

## 🧪 Testing Multi-Node

### Test 1: Load Balancing

```bash
# Kết nối 10 clients đồng thời
for i in {1..10}; do
  wscat -c ws://localhost:8080/ws/chat/v2?session_id=test-$i &
done

# Kiểm tra distribution trên các nodes
docker logs demo-java-websocket-1 | grep "WebSocket connected" | wc -l
docker logs demo-java-websocket-2 | grep "WebSocket connected" | wc -l
docker logs demo-java-websocket-3 | grep "WebSocket connected" | wc -l
```

### Test 2: Recovery Mechanism

```bash
# 1. Kết nối và bắt đầu streaming
wscat -c ws://localhost:8080/ws/chat/v2?session_id=test-recovery

# 2. Disconnect (Ctrl+C)

# 3. Reconnect và gửi recovery request
wscat -c ws://localhost:8080/ws/chat/v2?session_id=test-recovery
> {"type":"reconnect","messageId":"xxx","lastChunkIndex":10}

# Server sẽ trả về missing chunks
```

### Test 3: Node Failure

```bash
# 1. Kết nối tới specific node
wscat -c ws://localhost:8081/ws/chat/v2?session_id=test-failure

# 2. Kill node đang kết nối
docker stop demo-java-websocket-1

# 3. Reconnect qua load balancer (sẽ được route tới node khác)
wscat -c ws://localhost:8080/ws/chat/v2?session_id=test-failure
> {"type":"reconnect","messageId":"xxx","lastChunkIndex":10}

# Streaming sẽ tiếp tục từ node mới
```

## 📈 Monitoring

### Metrics

```bash
# Session count trên mỗi node
curl http://localhost:8081/actuator/metrics/websocket.active_sessions
curl http://localhost:8082/actuator/metrics/websocket.active_sessions
curl http://localhost:8083/actuator/metrics/websocket.active_sessions

# Redis connection pool
redis-cli INFO clients
```

### Logs

```bash
# Theo dõi logs real-time
docker-compose -f docker-compose.multi-node.yml logs -f

# Specific service
docker logs -f demo-java-websocket-1
docker logs -f demo-python-ai-1
```

## 🔐 Security Notes

**Production checklist:**

1. ✅ Cấu hình CORS properly (thay vì `*`)
2. ✅ Enable SSL/TLS cho WebSocket (wss://)
3. ✅ Add authentication/authorization
4. ✅ Rate limiting trên NGINX
5. ✅ Network isolation (internal network cho inter-service communication)
6. ✅ Redis authentication (requirepass)
7. ✅ Monitoring và alerting

## 🎯 Key Features

### ✅ Multi-node Support
- Horizontal scaling cho WebSocket servers và AI services
- Load balancing với sticky sessions
- Automatic failover

### ✅ Recovery Mechanism
- Client có thể reconnect bất cứ lúc nào
- Không mất chunks khi reconnect
- Hỗ trợ switch giữa các nodes

### ✅ Distributed Coordination
- Session registry trên Redis
- Distributed locks cho chunk ordering
- Inter-node communication qua PubSub

### ✅ High Availability
- Multiple instances của mỗi service
- Auto-restart khi container fail
- Redis persistence (AOF)

## 📝 API Endpoints

### WebSocket (Enhanced Handler)

**Endpoint:** `ws://localhost:8080/ws/chat/v2?session_id={sessionId}&user_id={userId}`

**Messages:**

```javascript
// Reconnect
{
  "type": "reconnect",
  "messageId": "message-id",
  "lastChunkIndex": 10
}

// Heartbeat
{
  "type": "heartbeat"
}

// Ping
{
  "type": "ping"
}
```

### AI Service

```bash
# Start chat (triggers streaming)
POST http://localhost:8001/chat
{
  "session_id": "test-session",
  "message": "Hello AI",
  "user_id": "user-123"
}

# Get history
GET http://localhost:8001/history/{session_id}
```

## 🛠️ Development

### Build locally

```bash
# Build Java service
cd java-websocket-server
./mvnw clean package

# Build Python service
cd python-ai-service
pip install -r requirements.txt

# Build frontend
cd frontend
npm install
npm run build
```

### Run tests

```bash
# Java tests
cd java-websocket-server
./mvnw test

# Python tests
cd python-ai-service
pytest
```

## 📚 Architecture Details

Chi tiết về kiến trúc được mô tả trong file `IMPL.md`.

Các thành phần chính đã triển khai:
- ✅ Domain Models (ChatSession, StreamChunk, Message, etc.)
- ✅ RedisStreamCache với distributed locking
- ✅ RedisPubSubPublisher cho inter-node communication
- ✅ SessionManager với distributed coordination
- ✅ ChatOrchestrator cho streaming coordination
- ✅ RecoveryService cho reconnection handling
- ✅ EnhancedChatWebSocketHandler với recovery mechanism
- ✅ Redisson integration cho distributed locks

## 🤝 Contributing

Contributions are welcome! Please read the architecture document (`IMPL.md`) first.

## 📄 License

MIT License
