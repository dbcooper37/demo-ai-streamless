# Architecture Flow - Multi-Node with Backend Proxy

## 🔄 Request Flow

### WebSocket Connections
```
Frontend (React)
    ↓ ws://localhost:8080/ws/chat
Nginx Load Balancer (ip_hash - sticky sessions)
    ↓
┌─────────────┬─────────────┬─────────────┐
│ WS Backend 1│ WS Backend 2│ WS Backend 3│ (Java Spring Boot)
└─────────────┴─────────────┴─────────────┘
    ↓ (WebSocket messages)
Redis PubSub (streaming chunks)
    ↑
┌─────────────┬─────────────┬─────────────┐
│ AI Service 1│ AI Service 2│ AI Service 3│ (Python FastAPI)
└─────────────┴─────────────┴─────────────┘
```

### REST API Calls
```
Frontend (React)
    ↓ http://localhost:8080/api/*
Nginx Load Balancer (ip_hash)
    ↓
┌─────────────┬─────────────┬─────────────┐
│ WS Backend 1│ WS Backend 2│ WS Backend 3│ (Java Spring Boot)
│ /api/chat   │ /api/chat   │ /api/chat   │ ChatController
│ /api/cancel │ /api/cancel │ /api/cancel │ + AiServiceLoadBalancer
│ /api/history│ /api/history│ /api/history│
└─────────────┴─────────────┴─────────────┘
    ↓ (round-robin load balancing)
┌─────────────┬─────────────┬─────────────┐
│ AI Service 1│ AI Service 2│ AI Service 3│ (Python FastAPI)
│ :8000       │ :8000       │ :8000       │
└─────────────┴─────────────┴─────────────┘
    ↓
Redis PubSub (publish streaming chunks)
```

---

## 📊 Component Responsibilities

### Frontend (React)
- **Responsibilities**:
  - User interface
  - WebSocket connection management
  - Message display
  - User input handling
- **Connects to**: Nginx Load Balancer only
- **Endpoints used**:
  - `ws://localhost:8080/ws/chat` - WebSocket
  - `http://localhost:8080/api/*` - REST API

### Nginx Load Balancer
- **Responsibilities**:
  - Sticky sessions (ip_hash)
  - Load distribution
  - WebSocket upgrade handling
  - Health checks
- **Routes to**: Java Backend only (not AI services directly)
- **Configuration**:
  - `/ws/*` → `websocket_backend` (sticky)
  - `/api/*` → `websocket_backend/api/` (sticky)

### Java Backend (WebSocket Server)
- **Responsibilities**:
  - WebSocket connection handling
  - REST API proxy to AI services
  - Load balancing AI service calls (round-robin)
  - Session management (distributed via Redis)
  - Stream orchestration
  - Recovery mechanism
- **Components**:
  - `ChatWebSocketHandler` - WebSocket messages
  - `ChatController` - REST API proxy
  - `AiServiceLoadBalancer` - AI service load balancing
  - `SessionManager` - Distributed session tracking
  - `RedisStreamCache` - Chunk caching
- **Connects to**:
  - All 3 AI service nodes (round-robin)
  - Redis (shared state)
  - Kafka (events)

### Python AI Service
- **Responsibilities**:
  - AI processing
  - Chat completion
  - Streaming chunks to Redis PubSub
  - History management
- **Nodes**: 3 instances (load balanced by Java backend)
- **Connects to**:
  - Redis (PubSub + history storage)

### Redis
- **Responsibilities**:
  - PubSub (streaming chunks)
  - Session registry (distributed)
  - Stream cache (TTL-based)
  - Distributed locks (Redisson)
- **Used by**: All Java backend nodes, all AI service nodes

---

## 🔀 Load Balancing Strategy

### Level 1: Nginx → Java Backend
- **Algorithm**: `ip_hash` (sticky sessions)
- **Reason**: WebSocket connections need persistence
- **Behavior**: Same client IP → same backend node

### Level 2: Java Backend → AI Service
- **Algorithm**: Round-robin with retry
- **Implementation**: `AiServiceLoadBalancer` class
- **Behavior**: 
  - Each request goes to next AI node in rotation
  - If node fails, retry with next node
  - Max retries = number of AI nodes

---

## 📡 Communication Patterns

### 1. User Sends Message
```
1. Frontend → POST /api/chat → Nginx
2. Nginx → (sticky) → Java Backend (e.g., ws-node-1)
3. Java Backend → (round-robin) → AI Service (e.g., ai-node-2)
4. AI Service processes, publishes chunks to Redis PubSub
5. All Java backends subscribe, receive chunks
6. Java Backend → WebSocket → Frontend (via sticky connection)
```

### 2. User Connects WebSocket
```
1. Frontend → ws://localhost:8080/ws/chat → Nginx
2. Nginx → (ip_hash) → Java Backend (e.g., ws-node-1)
3. Java Backend registers session in Redis
4. Java Backend subscribes to Redis PubSub for this session
5. Connection maintained (sticky session ensures same backend)
```

### 3. User Reconnects (Recovery)
```
1. Frontend → ws://localhost:8080/ws/chat → Nginx
2. Nginx → (ip_hash may route to different node if original down)
3. New Java Backend retrieves session from Redis
4. New Java Backend fetches missing chunks from Redis cache
5. New Java Backend sends missing chunks to Frontend
6. New Java Backend resubscribes to ongoing stream (if any)
```

---

## 🔐 Network Topology

### Exposed Ports (External)
- `3000` - Frontend (HTTP)
- `8080` - Nginx Load Balancer (HTTP/WebSocket)
- `8090` - Nginx Metrics (HTTP)
- `6379` - Redis (for debugging - should be internal in production)
- `9092` - Kafka (for debugging - should be internal in production)

### Internal Network (Docker)
```
Network: app-network (172.20.0.0/16)

Services:
- nginx-lb            (gateway)
- java-websocket-1    (internal only)
- java-websocket-2    (internal only)
- java-websocket-3    (internal only)
- python-ai-1         (internal only)
- python-ai-2         (internal only)
- python-ai-3         (internal only)
- redis               (internal)
- kafka               (internal)
- frontend            (exposed:3000)
```

---

## 📈 Scalability

### Adding More Backend Nodes
1. Add `java-websocket-4` to docker-compose
2. Update Nginx upstream config
3. Restart Nginx
4. No code changes needed

### Adding More AI Service Nodes
1. Add `python-ai-4` to docker-compose
2. Update `AI_SERVICE_URLS` env var in backend config
3. Restart backend services
4. No code changes needed

---

## 🛡️ Fault Tolerance

### Backend Node Failure
- Nginx detects failure (health checks)
- New connections routed to healthy nodes
- Existing connections on failed node are lost
- Clients reconnect, recovery mechanism restores state

### AI Service Node Failure
- Backend's `AiServiceLoadBalancer` retries with next node
- Transparent to frontend
- No message loss (request succeeds via another node)

### Redis Failure
- Backend and AI services lose shared state
- Sessions become isolated per backend node
- Streaming continues but recovery unavailable
- **Mitigation**: Redis Sentinel or Cluster (production)

---

## 🎯 Key Advantages

### 1. **Centralized Control**
- All external traffic goes through Nginx
- Backend acts as API gateway
- Single point for monitoring, rate limiting, security

### 2. **Flexible Load Balancing**
- Nginx: Sticky sessions for WebSocket
- Backend: Round-robin for AI services
- Different strategies for different needs

### 3. **Isolation**
- AI services not directly exposed
- Internal network communication only
- Better security posture

### 4. **Easy Scaling**
- Add nodes without frontend changes
- Backend dynamically discovers AI nodes
- Nginx automatically distributes load

### 5. **Graceful Degradation**
- Single AI node failure: automatic retry
- Single backend node failure: reconnection
- Redis failure: reduced functionality but still works

---

## 🔍 Monitoring Points

### 1. Nginx Level
- Active connections per backend
- Request distribution
- Error rates
- Response times

### 2. Backend Level
- Active WebSocket connections
- API request counts (by endpoint)
- AI service health (per node)
- Cache hit/miss rates
- Session counts

### 3. AI Service Level
- Request processing time
- Queue lengths
- Error rates
- Resource usage

### 4. Infrastructure Level
- Redis memory usage
- Redis PubSub throughput
- Kafka lag
- Network latency

---

## 📝 Configuration Files

| File | Purpose |
|------|---------|
| `nginx-sticky-session.conf` | Nginx routing config |
| `docker-compose.sticky-session.yml` | Multi-node orchestration |
| `application.yml` | Backend configuration |
| `AiServiceLoadBalancer.java` | AI load balancing logic |
| `ChatController.java` | API proxy endpoints |

---

**Last Updated**: 2025-11-11
**Architecture Version**: v2.0 (Backend Proxy with Load Balancing)
