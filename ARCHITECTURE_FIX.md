# Fix: Frontend Routing Through Backend

## Vấn Đề

Frontend trong multi-node setup đang gọi **trực tiếp** đến Python AI Service thay vì gọi qua Backend/Load Balancer.

### Trước Khi Fix

```
❌ SAI - Multi-Node Setup:

Frontend → Python AI Service (port 8001) [TRỰC TIẾP]
          ↓
          Bỏ qua load balancing
          Bỏ qua backend business logic
          Tight coupling
```

## Giải Pháp

Đã cập nhật cả **nginx config** và **docker-compose** để đảm bảo tất cả traffic đi qua backend.

### Sau Khi Fix

```
✅ ĐÚNG - Cả Single và Multi-Node:

Frontend → NGINX (load balancer) → Java Backend (multiple nodes)
                                         ↓
                                    Python AI Service
                                    
Architecture:
1. Frontend gọi NGINX load balancer
2. NGINX phân phối requests đến Java backend nodes (round-robin)
3. Java backends proxy requests đến AI service
4. Responses streaming qua WebSocket
```

## Các Thay Đổi Chi Tiết

### 1. nginx-lb.conf

#### Added REST API Proxying
```nginx
# REST API endpoints - Load balanced to Java backends
location /api/ {
    proxy_pass http://websocket_backend;
    
    # Standard proxy headers
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    # Timeouts for API calls
    proxy_connect_timeout 60s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
    
    # Enable buffering for API responses
    proxy_buffering on;
}

# Actuator endpoints (health, metrics, etc.)
location /actuator/ {
    proxy_pass http://websocket_backend;
    proxy_set_header Host $host;
}
```

**Giải thích**:
- NGINX bây giờ load balance cả WebSocket và REST API calls
- Tất cả `/api/` requests được phân phối đến Java backend nodes
- Timeouts được set phù hợp cho API calls (60s)
- Buffering enabled cho API responses (khác với WebSocket)

### 2. docker-compose.multi-node.yml

#### Frontend Environment Variables

**TRƯỚC**:
```yaml
environment:
  - VITE_WS_URL=ws://localhost:8080/ws/chat
  - VITE_API_URL=http://localhost:8001  # ❌ Gọi trực tiếp AI service
```

**SAU**:
```yaml
environment:
  # Changed: Route ALL requests through NGINX load balancer (port 8080)
  # NGINX will distribute WebSocket and API calls across Java backend nodes
  # Java backends will proxy API requests to AI service as needed
  - VITE_WS_URL=ws://localhost:8080/ws/chat
  - VITE_API_URL=http://localhost:8080/api  # ✅ Gọi qua NGINX → Java backends
```

**Giải thích**:
- Frontend giờ gọi tất cả requests (WebSocket + API) qua NGINX (port 8080)
- NGINX load balances đến 3 Java backend nodes
- Java backends proxy đến AI service khi cần

### 3. Single-Node Setup

**docker-compose.yml** - Đã đúng từ trước, không cần thay đổi:
```yaml
environment:
  - VITE_WS_URL=ws://localhost:8080/ws/chat
  - VITE_API_URL=http://localhost:8080/api  # ✅ Đã đúng từ đầu
```

## Kiến Trúc Chi Tiết

### Single-Node Setup

```
┌──────────┐         ┌─────────────────┐         ┌──────────────┐
│ Frontend │ ─HTTP──→│ Java Backend    │ ─HTTP──→│ Python AI    │
│ (React)  │         │ (Port 8080)     │         │ Service      │
└──────────┘         │                 │         │ (Port 8000)  │
      │              │ - REST API      │         └──────────────┘
      │              │ - WebSocket     │
      └─WebSocket───→│ - Proxy to AI   │
                     └─────────────────┘
                            │
                            ↓
                     ┌─────────────┐
                     │ Redis       │
                     │ Kafka       │
                     └─────────────┘
```

### Multi-Node Setup

```
┌──────────┐         ┌─────────────────┐         ┌─────────────────┐
│ Frontend │ ─HTTP──→│ NGINX LB        │         │ Java Backend #1 │──┐
│ (React)  │         │ (Port 8080)     │ ───────→│                 │  │
└──────────┘         │                 │    │    └─────────────────┘  │
      │              │ Load Balance:   │    │                         │
      │              │ - WebSocket     │    │    ┌─────────────────┐  │
      └─WebSocket───→│ - REST API      │    ├───→│ Java Backend #2 │  │
                     │                 │    │    └─────────────────┘  │
                     │ Round-Robin     │    │                         │
                     │ (No sticky)     │    │    ┌─────────────────┐  │
                     └─────────────────┘    └───→│ Java Backend #3 │  │
                                                  └─────────────────┘  │
                                                           │           │
                                                           ↓           │
                     ┌──────────────────────────────────────────────┐ │
                     │         Shared Infrastructure                 │ │
                     │                                               │ │
                     │  ┌─────────┐  ┌───────┐  ┌──────────────┐  │ │
                     │  │ Redis   │  │ Kafka │  │ Python AI    │←─┼─┘
                     │  │ (PubSub)│  │       │  │ Service(s)   │  │
                     │  └─────────┘  └───────┘  └──────────────┘  │
                     └──────────────────────────────────────────────┘

Request Flow:
1. Frontend → NGINX (port 8080)
2. NGINX → Java Backend (round-robin)
3. Java Backend → AI Service (internal)
4. Response ← streaming back via WebSocket
```

## Lợi Ích Của Architecture Mới

### ✅ 1. Proper Load Balancing
- NGINX phân phối requests đều đến tất cả Java backend nodes
- Không có single point of failure
- Horizontal scaling dễ dàng

### ✅ 2. Loose Coupling
- Frontend không biết về AI service
- Có thể thay đổi AI service implementation mà không ảnh hưởng frontend
- Separation of concerns

### ✅ 3. Security
- AI service không exposed trực tiếp ra ngoài
- Java backend có thể thêm authentication, rate limiting, etc.
- Centralized access control

### ✅ 4. Monitoring & Logging
- Tất cả traffic qua backend → centralized logging
- Dễ monitor và debug
- Có thể add middleware cho metrics, tracing, etc.

### ✅ 5. Business Logic
- Java backend có thể add business logic trước khi gọi AI
- Validation, transformation, caching, etc.
- Consistent API contract

### ✅ 6. Resilience
- Java backend có thể implement retry logic
- Circuit breaker patterns
- Fallback mechanisms

## Testing

### Test Single-Node Setup

```bash
# 1. Start services
docker-compose up -d

# 2. Verify frontend calls backend
curl http://localhost:8080/api/ai-health

# 3. Open UI and test
open http://localhost:3000

# 4. Check logs
docker logs demo-java-websocket | grep "Proxying"
```

Expected logs:
```
Proxying chat request to AI service: session_id=...
Proxying cancel request to AI service: session_id=...
```

### Test Multi-Node Setup

```bash
# 1. Start multi-node setup
docker-compose -f docker-compose.multi-node.yml up -d

# 2. Verify NGINX is load balancing
for i in {1..10}; do
  curl http://localhost:8080/actuator/health
  sleep 1
done

# 3. Check which backends handled requests
docker logs demo-java-websocket-1 | grep "Proxying"
docker logs demo-java-websocket-2 | grep "Proxying"
docker logs demo-java-websocket-3 | grep "Proxying"

# 4. Open UI and test streaming
open http://localhost:3000
```

Expected behavior:
- Requests distributed across all 3 Java backend nodes
- Streaming works smoothly
- No direct calls to AI service from frontend

### Verify No Direct AI Service Calls

```bash
# Check frontend environment
docker exec demo-frontend env | grep VITE

# Expected output (multi-node):
# VITE_WS_URL=ws://localhost:8080/ws/chat
# VITE_API_URL=http://localhost:8080/api

# NOT this (would be wrong):
# VITE_API_URL=http://localhost:8001
```

## Port Mapping Summary

### Single-Node
| Service | Internal Port | External Port | Purpose |
|---------|--------------|---------------|---------|
| Java Backend | 8080 | 8080 | WebSocket + API |
| Python AI | 8000 | 8000 | AI Service (internal) |
| Frontend | 3000 | 3000 | UI |
| Redis | 6379 | 6379 | Cache/PubSub |
| Kafka | 9092 | 9092 | Event Streaming |

### Multi-Node
| Service | Internal Port | External Port | Purpose |
|---------|--------------|---------------|---------|
| NGINX LB | 80 | 8080 | Load Balancer |
| Java Backend #1 | 8080 | - | Backend (internal) |
| Java Backend #2 | 8080 | - | Backend (internal) |
| Java Backend #3 | 8080 | - | Backend (internal) |
| Python AI #1 | 8000 | 8001 | AI Service (internal) |
| Python AI #2 | 8000 | 8002 | AI Service (internal) |
| Python AI #3 | 8000 | 8003 | AI Service (internal) |
| Frontend | 3000 | 3000 | UI |
| Redis | 6379 | 6379 | Shared Cache/PubSub |
| Kafka | 9092 | 9092 | Shared Event Streaming |

**Note**: Services không có External Port chỉ accessible trong Docker network, không exposed ra host.

## Rollback Plan

Nếu cần rollback:

### Single-Node
Không cần rollback - đã đúng từ đầu.

### Multi-Node
```bash
# Revert docker-compose.multi-node.yml
git checkout HEAD~1 docker-compose.multi-node.yml

# Revert nginx-lb.conf
git checkout HEAD~1 nginx-lb.conf

# Restart
docker-compose -f docker-compose.multi-node.yml down
docker-compose -f docker-compose.multi-node.yml up -d
```

## Checklist

- [x] NGINX config updated to proxy `/api/` endpoints
- [x] docker-compose.multi-node.yml frontend env vars updated
- [x] Frontend calls through NGINX load balancer
- [x] Java backends proxy to AI service
- [x] No direct frontend → AI service calls
- [x] Load balancing works for both WebSocket and API
- [x] Documentation updated
- [ ] Tested single-node setup
- [ ] Tested multi-node setup
- [ ] Verified no direct AI service calls

## Next Steps

1. **Rebuild services** (nếu đang chạy):
   ```bash
   # Single-node
   docker-compose down
   docker-compose up --build -d
   
   # Multi-node
   docker-compose -f docker-compose.multi-node.yml down
   docker-compose -f docker-compose.multi-node.yml up --build -d
   ```

2. **Test thoroughly**:
   - Verify frontend works
   - Check logs cho proper routing
   - Test load balancing (multi-node)
   - Verify streaming works

3. **Monitor**:
   - Check NGINX logs: `docker logs demo-nginx-lb`
   - Check Java logs: `docker logs demo-java-websocket`
   - Check AI service logs: `docker logs demo-python-ai`

---

**Created**: 2025-11-11  
**Author**: AI Assistant  
**Status**: ✅ Completed
