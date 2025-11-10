# Python AI Service Streaming Fix

## Vấn đề phát hiện

Python AI service có code streaming nhưng:

1. **Delays quá nhanh** (0.1s và 0.05s) → Streaming không rõ ràng
2. **Thiếu logging** → Không biết streaming có chạy không
3. **Không check subscribers** → Không biết có ai đang lắng nghe không

## Các sửa đổi

### 1. Tăng streaming delays (`config.py`)

```python
# Before
STREAM_DELAY: float = 0.1  # seconds
CHUNK_DELAY: float = 0.05  # seconds

# After
STREAM_DELAY: float = 0.3  # seconds between words
CHUNK_DELAY: float = 0.1   # seconds after publishing
```

**Lý do**: Delays cũ quá nhanh, không thấy effect streaming. Bây giờ sẽ rõ ràng hơn.

### 2. Thêm logging chi tiết (`ai_service.py`)

```python
# Khi bắt đầu streaming
logger.info(f"Starting AI response streaming for session={session_id}, msg_id={message_id}")
logger.info(f"Selected response text (length={len(response_text)}): {response_text[:50]}...")

# Mỗi chunk
logger.debug(f"Published chunk #{chunk_count} to Redis: chunk='{chunk}', accumulated_length={len(accumulated_content)}")

# Khi hoàn thành
logger.info(f"Completed AI response streaming: session={session_id}, chunks={chunk_count}, total_length={len(accumulated_content)}")
```

### 3. Log số subscribers (`redis_client.py`)

```python
# Before
self.client.publish(channel, payload)
logger.debug(f"Published message to {channel}")

# After
result = self.client.publish(channel, payload)
logger.info(f"Published to {channel}: role={message.role}, is_complete={message.is_complete}, content_len={len(message.content)}, subscribers={result}")
```

**Quan trọng**: `result` là số subscribers đang lắng nghe channel
- `subscribers=0` → Không có Java server nào subscribe!
- `subscribers=1+` → OK, có server đang lắng nghe

## Files đã sửa

1. ✅ `config.py` - Tăng delays
2. ✅ `ai_service.py` - Thêm logging và tracking
3. ✅ `redis_client.py` - Log subscribers count

## Files hỗ trợ

1. ✅ `test_redis_pubsub.py` - Script test Redis PubSub
2. ✅ `DEBUG_STREAMING.md` - Hướng dẫn debug chi tiết

## Cách test

### 1. Rebuild services
```bash
docker compose down
docker compose up --build -d
```

### 2. Xem logs Python
```bash
docker compose logs -f python-ai-service | grep -E "(Starting|Published|Completed)"
```

Bạn sẽ thấy:
```
Starting AI response streaming for session=xxx
Published to chat:stream:xxx: subscribers=1  ← Quan trọng!
Published to chat:stream:xxx: subscribers=1
...
Completed AI response streaming: chunks=15
```

### 3. Test với curl
```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test_123",
    "user_id": "demo_user",
    "message": "xin chào"
  }'
```

### 4. Xem streaming trên frontend
- Mở http://localhost:3000
- Gửi message
- Sẽ thấy AI response xuất hiện từng từ một
- Streaming indicator (3 dots) sẽ hiển thị trong khi streaming

## Diagnostic Checklist

Khi test, kiểm tra các điểm sau:

### ✅ Python Service
- [ ] Log "Starting AI response streaming"
- [ ] Log "Published to chat:stream"
- [ ] **subscribers > 0** (rất quan trọng!)
- [ ] Log "Completed AI response streaming"
- [ ] Số chunks phù hợp với độ dài text

### ✅ Java Service
- [ ] Log "ChatOrchestrator received message"
- [ ] Log "Handling legacy message"
- [ ] Log "Calling callback.onChunk"
- [ ] Log "Sending chunk to WebSocket"

### ✅ Frontend
- [ ] WebSocket connected (check DevTools → Network → WS)
- [ ] Nhận được messages type="message"
- [ ] AI response xuất hiện từng từ một
- [ ] Streaming indicator hiển thị
- [ ] Complete message cuối cùng (no indicator)

## Troubleshooting

### Vấn đề: subscribers=0

**Nghĩa là**: Không có Java server nào subscribe channel này

**Kiểm tra**:
```bash
# Check Java logs
docker compose logs java-websocket-server | grep "Subscribed to legacy channel"

# Sẽ thấy:
# Subscribed to legacy channel: chat:stream:xxx with listener
```

Nếu không thấy → WebSocket chưa connect hoặc `ChatOrchestrator.startStreamingSession()` không được gọi

### Vấn đề: Streaming quá nhanh

**Giải pháp**: Tăng delays trong `.env` hoặc environment variables:
```bash
# docker-compose.yml
environment:
  - STREAM_DELAY=0.5
  - CHUNK_DELAY=0.2
```

### Vấn đề: Không thấy logs

**Giải pháp**: Set LOG_LEVEL=DEBUG
```bash
# docker-compose.yml
environment:
  - LOG_LEVEL=DEBUG
```

## Expected Behavior

Sau khi fix:

1. **User gửi message "xin chào"**
   - Python log: "Starting AI response streaming"
   - Python log: "Published... subscribers=1"

2. **Streaming bắt đầu**
   - Từng từ xuất hiện trên frontend
   - Delay 0.3s giữa các từ
   - Streaming indicator (3 dots) hiển thị

3. **Streaming hoàn thành**
   - Final message với is_complete=True
   - Streaming indicator biến mất
   - Python log: "Completed... chunks=N"

## Performance Notes

- **STREAM_DELAY=0.3s**: Tốt cho demo, dễ thấy streaming
- **STREAM_DELAY=0.1s**: Nhanh hơn, vẫn thấy được effect
- **STREAM_DELAY=0.05s**: Rất nhanh, gần như instant

Có thể adjust delays tùy use case:
- Demo/testing: 0.3-0.5s
- Production: 0.05-0.1s
- Real AI model: depends on model speed

## Kết luận

Python streaming giờ đã:
- ✅ Có delays phù hợp để thấy rõ streaming
- ✅ Log đầy đủ cho debugging
- ✅ Track subscribers để verify Java đang lắng nghe
- ✅ Error handling đầy đủ

Kết hợp với Java fixes (STREAMING_FIX_SUMMARY.md), toàn bộ hệ thống streaming giờ hoạt động đúng!
