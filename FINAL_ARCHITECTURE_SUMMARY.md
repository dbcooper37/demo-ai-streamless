# ✅ HOÀN THÀNH - Multi-Node với Backend Proxy Architecture

## 🎯 Yêu cầu đã thực hiện

### ✅ Frontend gọi AI service THÔNG QUA backend
- Frontend **KHÔNG** gọi trực tiếp AI service
- **TẤT CẢ** requests đi qua Backend Java
- Backend làm API gateway

### ✅ AI service triển khai trên nhiều node
- 3 AI service nodes
- Backend load balance round-robin
- Automatic retry khi node fail

### ✅ Triển khai qua Docker
- Docker Compose orchestration
- Internal network isolation
- No exposed AI service ports

---

## 🏗️ Architecture Flow

```
┌──────────────────────────────────────────────────┐
│          Frontend (React) - localhost:3000       │
│          • User interface                        │
│          • WebSocket client                      │
└──────────────────┬───────────────────────────────┘
                   │
                   │ 100% traffic qua Nginx
                   ↓
┌──────────────────────────────────────────────────┐
│       Nginx Load Balancer - localhost:8080       │
│       • ip_hash (sticky sessions)                │
│       • /ws/* → backend                          │
│       • /api/* → backend                         │
└──────────────────┬───────────────────────────────┘
                   │
     ┌─────────────┼─────────────┐
     ↓             ↓             ↓
┌──────────┐  ┌──────────┐  ┌──────────┐
│Backend 1 │  │Backend 2 │  │Backend 3 │ (Java Spring Boot)
│          │  │          │  │          │
│ChatController + AiServiceLoadBalancer │
│• Proxy API requests                   │
│• Load balance to AI nodes              │
│• WebSocket handling                    │
│• Session management                    │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │            │            │
     └────────────┼────────────┘
                  │
        Round-robin load balancing
                  │
     ┌────────────┼────────────┐
     ↓            ↓            ↓
┌──────────┐ ┌──────────┐ ┌──────────┐
│AI Node 1 │ │AI Node 2 │ │AI Node 3 │ (Python FastAPI)
│:8000     │ │:8000     │ │:8000     │
│Internal  │ │Internal  │ │Internal  │
│only      │ │only      │ │only      │
└────┬─────┘ └────┬─────┘ └────┬─────┘
     │            │            │
     └────────────┼────────────┘
                  ↓
         ┌────────────────┐
         │     Redis      │
         │  Shared State  │
         │    PubSub      │
         └────────────────┘
```

---

## 📊 Request Flow Chi Tiết

### 1. User Gửi Chat Message

```
Frontend
  ↓ POST http://localhost:8080/api/chat
  ↓ { session_id, message, user_id }
Nginx Load Balancer (ip_hash)
  ↓ route to sticky backend
Backend Java - ChatController
  ↓ AiServiceLoadBalancer.post("/chat", request)
  ↓ round-robin selection
AI Service Node (e.g., AI-2)
  ↓ process chat
  ↓ publish chunks to Redis PubSub
Redis PubSub
  ↓ all backends receive chunks
Backend Java (sticky backend)
  ↓ WebSocket send to client
Frontend
  ✅ receives streaming response
```

### 2. Load Balancing Strategy

#### Level 1: Nginx → Backend
- **Algorithm**: ip_hash (sticky sessions)
- **Reason**: WebSocket needs persistence
- **Result**: Same client → same backend

#### Level 2: Backend → AI Service
- **Algorithm**: Round-robin với retry
- **Implementation**: AiServiceLoadBalancer
- **Logic**:
  ```
  Request 1 → AI-1
  Request 2 → AI-2
  Request 3 → AI-3
  Request 4 → AI-1 (wrap around)
  
  If AI-1 fails:
    Retry with AI-2
    If AI-2 fails:
      Retry with AI-3
  ```

---

## 🔧 Code Components

### 1. AiServiceLoadBalancer.java (NEW)
```java
@Service
public class AiServiceLoadBalancer {
    // Round-robin load balancing
    private final AtomicInteger currentIndex;
    private final List<String> aiServiceUrls;
    
    // Execute with retry
    public ResponseEntity<T> execute(path, method, body) {
        for (attempt in maxRetries) {
            url = getNextUrl();  // round-robin
            try {
                return restTemplate.exchange(url, ...);
            } catch (Exception e) {
                // retry with next node
            }
        }
    }
}
```

**Features**:
- Round-robin selection
- Automatic retry on failure
- Health check for all nodes
- Configurable via environment variables

### 2. ChatController.java (UPDATED)
```java
@RestController
@RequestMapping("/api")
public class ChatController {
    private final AiServiceLoadBalancer loadBalancer;
    
    @PostMapping("/chat")
    public ResponseEntity<?> sendMessage(request) {
        // Proxy to AI service via load balancer
        return loadBalancer.post("/chat", request);
    }
}
```

**Changes**:
- Sử dụng AiServiceLoadBalancer thay vì RestTemplate trực tiếp
- Load balancing tự động
- Retry logic built-in

### 3. nginx-sticky-session.conf (UPDATED)
```nginx
# API routes through backend (not directly to AI)
location /api/ {
    proxy_pass http://websocket_backend/api/;
    # Backend will load-balance to AI services
}
```

**Changes**:
- /api/* route to backend (không còn route trực tiếp đến AI)
- Backend xử lý load balancing

### 4. docker-compose.sticky-session.yml (UPDATED)
```yaml
java-websocket-1:
  environment:
    - AI_SERVICE_URLS=http://python-ai-1:8000,http://python-ai-2:8000,http://python-ai-3:8000
```

**Changes**:
- Configure multiple AI service URLs
- Backend có danh sách tất cả AI nodes

---

## 📈 Benefits

### 1. ✅ Centralized Control
- Frontend chỉ biết đến Nginx
- Backend là API gateway duy nhất
- Single point for monitoring, logging, security

### 2. ✅ Security
- AI services **KHÔNG** exposed ra ngoài
- Chỉ accessible qua internal network
- Backend có thể implement authentication, rate limiting

### 3. ✅ Flexibility
- Nginx: Sticky sessions for WebSocket
- Backend: Round-robin for AI services
- Different strategies for different needs

### 4. ✅ Fault Tolerance
- AI node fails → automatic retry với node khác
- Backend node fails → Nginx routes to healthy node
- Transparent to frontend

### 5. ✅ Easy Scaling
- Add AI nodes: Update AI_SERVICE_URLS, restart backend
- Add backend nodes: Update Nginx config, restart Nginx
- **KHÔNG** cần frontend changes

---

## 🚀 Deployment

### Quick Start
```bash
# 1. Deploy
./DEPLOY_STICKY_SESSION.sh

# 2. Test
./TEST_STICKY_SESSION.sh

# 3. Access
open http://localhost:3000
```

### Service URLs
```
Frontend:       http://localhost:3000
Load Balancer:  http://localhost:8080
WebSocket:      ws://localhost:8080/ws/chat
API:            http://localhost:8080/api/*
Nginx Stats:    http://localhost:8090/nginx-status
```

### Architecture Verification
```bash
# 1. Check AI service health (via backend proxy)
curl http://localhost:8080/api/ai-health

# Expected: All 3 AI nodes status
{
  "total_nodes": 3,
  "healthy_nodes": 3,
  "nodes": [
    {"url": "http://python-ai-1:8000", "status": "healthy"},
    {"url": "http://python-ai-2:8000", "status": "healthy"},
    {"url": "http://python-ai-3:8000", "status": "healthy"}
  ]
}

# 2. Send chat message (should load balance across AI nodes)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test","message":"Hello","user_id":"user1"}'

# 3. Check backend logs to see which AI node handled request
docker logs sticky-java-ws-1 | grep "AI service request successful"
```

---

## 📁 Files Changed

| File | Lines | Status | Description |
|------|-------|--------|-------------|
| `AiServiceLoadBalancer.java` | +183 | NEW | Load balancing logic |
| `RestTemplateConfig.java` | +26 | NEW | RestTemplate configuration |
| `ChatController.java` | ~50 | MODIFIED | Use load balancer |
| `application.yml` | ~5 | MODIFIED | Multi-node URLs |
| `docker-compose.sticky-session.yml` | ~15 | MODIFIED | AI_SERVICE_URLS config |
| `nginx-sticky-session.conf` | ~20 | MODIFIED | Route via backend |
| `ARCHITECTURE_FLOW.md` | +350 | NEW | Complete documentation |

**Total**: 649+ lines added/modified

---

## 🎯 Architecture Comparison

### Before (Direct Access)
```
Frontend → Nginx → AI Service (least_conn)
           ↓
      Backend (WebSocket only)
```
**Issues**:
- Frontend có quyền truy cập trực tiếp AI service
- Khó control, monitor, secure
- Load balancing limited to Nginx

### After (Backend Proxy)
```
Frontend → Nginx → Backend → AI Service (round-robin + retry)
                      ↓
                 Load Balancer
```
**Benefits**:
- Frontend chỉ biết đến Backend
- Backend full control over AI service access
- Flexible load balancing với retry logic
- Better security và monitoring

---

## 🔍 Testing

### Test 1: Verify Backend Proxy
```bash
# Frontend chỉ gọi backend (không trực tiếp AI)
curl http://localhost:8080/api/ai-health

# Should show all AI nodes via backend proxy
```

### Test 2: Verify Load Balancing
```bash
# Send multiple requests
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/chat \
    -H "Content-Type: application/json" \
    -d "{\"session_id\":\"test\",\"message\":\"$i\",\"user_id\":\"user1\"}"
done

# Check logs - requests should distribute across AI nodes
docker logs sticky-java-ws-1 | grep "Attempting request to AI service"
```

### Test 3: Verify Retry Logic
```bash
# Stop one AI node
docker stop sticky-python-ai-1

# Send request - should automatically retry with other nodes
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test","message":"test","user_id":"user1"}'

# Should succeed via ai-2 or ai-3
```

---

## 📝 Configuration

### Backend Configuration
```yaml
# application.yml
ai:
  service:
    urls: ${AI_SERVICE_URLS:http://python-ai-1:8000,http://python-ai-2:8000,http://python-ai-3:8000}
```

### Docker Compose
```yaml
java-websocket-1:
  environment:
    - AI_SERVICE_URLS=http://python-ai-1:8000,http://python-ai-2:8000,http://python-ai-3:8000
```

### Nginx
```nginx
location /api/ {
    proxy_pass http://websocket_backend/api/;
}
```

---

## 🎉 Summary

### ✅ Đã hoàn thành
- ✅ Frontend gọi AI service **THÔNG QUA** backend
- ✅ Backend load balance requests đến 3 AI nodes
- ✅ AI services chỉ accessible qua internal network
- ✅ Round-robin + retry logic
- ✅ Triển khai qua Docker Compose
- ✅ Full documentation

### 📊 Architecture Stats
- **Frontend**: 1 instance (exposed)
- **Nginx LB**: 1 instance (exposed)
- **Backend**: 3 instances (internal, via Nginx)
- **AI Service**: 3 instances (internal, via Backend)
- **Redis**: 1 instance (internal)
- **Kafka**: 1 instance (internal)

### 🔄 Request Path
```
Frontend → Nginx (sticky) → Backend (round-robin) → AI Service
```

### 🎯 Key Improvements
1. **Security**: AI không exposed
2. **Control**: Backend làm gateway
3. **Fault Tolerance**: Automatic retry
4. **Scalability**: Easy to add nodes
5. **Monitoring**: Centralized tại backend

---

**Branch**: `dev_sticky_session`  
**Status**: ✅ **READY FOR DEPLOYMENT**  
**Date**: 2025-11-11  
**Commits**: 6 commits  
**Files**: 13 files created/modified  

🚀 **Deploy với**: `./DEPLOY_STICKY_SESSION.sh`
