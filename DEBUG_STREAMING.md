# Debug Guide - Streaming Issues

## Các thay đổi đã thực hiện

### 1. Tăng streaming delays
- `STREAM_DELAY`: 0.1s → **0.3s** (delay giữa các words)
- `CHUNK_DELAY`: 0.05s → **0.1s** (delay sau khi publish)

Bây giờ streaming sẽ rõ ràng hơn và dễ thấy hơn.

### 2. Thêm logging chi tiết

**Python AI Service**:
- Log khi bắt đầu streaming
- Log mỗi chunk được publish (với DEBUG level)
- Log số subscribers đang lắng nghe channel
- Log khi hoàn thành streaming

**Java WebSocket Server**:
- Log khi receive message từ Redis
- Log khi convert và send qua WebSocket
- Log session mapping

## Kiểm tra từng bước

### Bước 1: Kiểm tra Python có publish không?

```bash
# Xem logs của Python service
docker compose logs -f python-ai-service | grep -E "(Starting AI|Published|Completed)"

# Bạn sẽ thấy:
# Starting AI response streaming for session=...
# Published to chat:stream:...: role=assistant, is_complete=False, subscribers=X
# Completed AI response streaming: session=..., chunks=X
```

**Quan trọng**: Kiểm tra `subscribers=X`
- Nếu `subscribers=0` → Không có Java server nào subscribe channel!
- Nếu `subscribers=1` hoặc hơn → OK, Java đang lắng nghe

### Bước 2: Kiểm tra Java có receive không?

```bash
# Xem logs của Java service
docker compose logs -f java-websocket-server | grep -E "(ChatOrchestrator received|Handling legacy)"

# Bạn sẽ thấy:
# ChatOrchestrator received message from chat:stream:...: {...}
# Handling legacy message for session ...: role=assistant, isComplete=false
```

Nếu không thấy log này → Java KHÔNG receive messages từ Redis!

### Bước 3: Kiểm tra Java có send qua WebSocket không?

```bash
# Xem logs của Java service
docker compose logs -f java-websocket-server | grep -E "(Sending chunk|Calling callback)"

# Bạn sẽ thấy:
# Calling callback.onChunk for messageId: ..., index: X
# Sending chunk to WebSocket session ...: index=X, contentLength=X
```

### Bước 4: Kiểm tra trực tiếp Redis

Test script để kiểm tra Redis PubSub:

```bash
# Chạy test script
cd /workspace
python3 test_redis_pubsub.py
```

Hoặc manual test:

```bash
# Terminal 1: Subscribe
docker compose exec redis redis-cli
> SUBSCRIBE chat:stream:*

# Terminal 2: Publish test message
docker compose exec redis redis-cli
> PUBLISH chat:stream:test_session '{"message":"test"}'

# Terminal 1 sẽ nhận được message
```

## Các vấn đề có thể gặp

### Vấn đề 1: subscribers=0

**Nguyên nhân**: Java server chưa subscribe channel

**Giải pháp**:
1. Kiểm tra WebSocket có connect thành công không?
2. Kiểm tra `ChatOrchestrator.startStreamingSession()` có được gọi không?
3. Kiểm tra log: "Subscribed to legacy channel: chat:stream:..."

### Vấn đề 2: Java không receive messages

**Nguyên nhân**: 
- Redis connection có vấn đề
- Channel name không khớp
- MessageListener chưa được register

**Giải pháp**:
1. Kiểm tra Redis connection trong Java logs
2. Verify channel name: phải là `chat:stream:{session_id}`
3. Kiểm tra `RedisMessageListenerContainer` đang chạy

### Vấn đề 3: Java receive nhưng không send qua WebSocket

**Nguyên nhân**:
- WebSocket session đã đóng
- Callback không được gọi
- Serialization error

**Giải pháp**:
1. Kiểm tra WebSocket status: `wsSession.isOpen()`
2. Verify callback được pass đúng
3. Kiểm tra ObjectMapper có serialize được ChatMessage không

### Vấn đề 4: Frontend không nhận được messages

**Nguyên nhân**:
- WebSocket handler không đúng
- Message type không khớp
- Frontend đã disconnect

**Giải pháp**:
1. Mở Browser DevTools → Network → WS
2. Xem messages được gửi qua WebSocket
3. Check frontend console có errors không

## Testing Commands

### 1. Rebuild và restart tất cả services
```bash
docker compose down
docker compose up --build -d
```

### 2. Xem logs real-time
```bash
# Tất cả services
docker compose logs -f

# Chỉ Python
docker compose logs -f python-ai-service

# Chỉ Java
docker compose logs -f java-websocket-server

# Filter theo keyword
docker compose logs -f | grep -i "streaming\|publish\|chunk"
```

### 3. Test qua curl
```bash
# Send message
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test_session_123",
    "user_id": "test_user",
    "message": "Hello"
  }'
```

### 4. Monitor Redis
```bash
# Check active channels
docker compose exec redis redis-cli
> PUBSUB CHANNELS chat:*

# Monitor all Redis commands
> MONITOR
```

## Expected Flow

Khi gửi message "Hello", bạn sẽ thấy logs theo thứ tự:

```
1. [Python] Starting AI response streaming for session=test_session_123
2. [Python] Selected response text: Xin chào! Tôi là AI assistant...
3. [Python] Published to chat:stream:test_session_123: subscribers=1
4. [Java] ChatOrchestrator received message from chat:stream:test_session_123
5. [Java] Handling legacy message for session test_session_123
6. [Java] Calling callback.onChunk for messageId: xxx
7. [Java] Sending chunk to WebSocket session: index=0
... (lặp lại cho mỗi chunk)
8. [Python] Published final complete message
9. [Java] Sending chunk to WebSocket session: index=N (complete)
10. [Python] Completed AI response streaming: chunks=N
```

Nếu flow bị ngắt ở bước nào, bạn sẽ biết vấn đề nằm ở đâu!

## Next Steps

1. ✅ Đã tăng delays và thêm logging
2. ⏳ Rebuild services với changes mới
3. ⏳ Test và xem logs
4. ⏳ Report lại kết quả

Sau khi rebuild, gửi một message và share logs để tôi giúp debug tiếp!
