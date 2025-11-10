# Hướng dẫn Test Streaming - Step by Step

## 🎯 Mục tiêu
Sau khi apply tất cả fixes, streaming phải hoạt động:
- User gửi message → AI response streaming từng từ một
- Frontend hiển thị streaming indicator (3 dots)
- Mỗi chunk update accumulated text
- Complete message xuất hiện cuối cùng

## 📋 Các sửa đổi đã thực hiện

### Java WebSocket Server
1. ✅ Disable duplicate subscription (chỉ dùng ChatOrchestrator)
2. ✅ Convert StreamChunk → ChatMessage format
3. ✅ Use accumulated content thay vì chỉ chunk
4. ✅ Enhanced logging

### Python AI Service  
1. ✅ Tăng delays (0.3s giữa words, 0.1s sau publish)
2. ✅ Thêm logging chi tiết
3. ✅ Track subscribers count

### Files đã sửa
- `ChatWebSocketHandler.java`
- `ChatOrchestrator.java`
- `RedisMessageListener.java`
- `config.py`
- `ai_service.py`
- `redis_client.py`

## 🚀 Cách test

### Bước 1: Rebuild và khởi động services

```bash
cd /workspace

# Stop tất cả services
docker compose down

# Rebuild và start lại
docker compose up --build -d

# Đợi services khởi động (khoảng 10-20 giây)
docker compose ps
```

Kiểm tra tất cả services đang chạy:
```
NAME                    STATUS
frontend                Up
java-websocket-server   Up
postgres                Up
python-ai-service       Up
redis                   Up
```

### Bước 2: Mở terminal logs (Terminal riêng)

Mở 2 terminal để xem logs real-time:

**Terminal 1 - Python logs:**
```bash
docker compose logs -f python-ai-service | grep -E "(Starting|Published|Completed|subscribers)"
```

**Terminal 2 - Java logs:**
```bash
docker compose logs -f java-websocket-server | grep -E "(ChatOrchestrator|sendChunk|Broadcasting|Calling callback)"
```

### Bước 3: Mở frontend và test

1. Mở browser: http://localhost:3000
2. Kiểm tra WebSocket connection:
   - Mở DevTools (F12)
   - Tab Network → Filter "WS"
   - Sẽ thấy connection đến `ws://localhost:8080/ws/chat`
   - Status: "101 Switching Protocols" (connected)

3. Gửi một message, ví dụ: "xin chào"

### Bước 4: Quan sát kết quả

#### A. Trên Frontend (Browser)

**Ngay lập tức:**
- User message "xin chào" xuất hiện

**Sau ~1 giây:**
- AI response bắt đầu xuất hiện
- Thấy streaming indicator (3 dots animation)
- Text xuất hiện từng từ một:
  - "Xin "
  - "Xin chào! "
  - "Xin chào! Tôi "
  - "Xin chào! Tôi là "
  - ... (tiếp tục)

**Cuối cùng:**
- Complete message hiển thị đầy đủ
- Streaming indicator biến mất (no dots)

#### B. Trong Python logs (Terminal 1)

Bạn sẽ thấy:
```
Starting AI response streaming for session=session_xxx, msg_id=yyy
Selected response text (length=78): Xin chào! Tôi là AI assistant. Tôi có thể giúp gì ch...
Published to chat:stream:session_xxx: role=assistant, is_complete=False, content_len=4, subscribers=1
Published to chat:stream:session_xxx: role=assistant, is_complete=False, content_len=10, subscribers=1
Published to chat:stream:session_xxx: role=assistant, is_complete=False, content_len=14, subscribers=1
...
Published to chat:stream:session_xxx: role=assistant, is_complete=True, content_len=78, subscribers=1
Completed AI response streaming: session=session_xxx, msg_id=yyy, chunks=15, total_length=78
```

**Quan trọng**: Kiểm tra `subscribers=1` (hoặc hơn). Nếu `subscribers=0` → có vấn đề!

#### C. Trong Java logs (Terminal 2)

Bạn sẽ thấy:
```
ChatOrchestrator received message from chat:stream:session_xxx: {"message_id":"yyy",...}
Handling legacy message for session session_xxx: role=assistant, isComplete=false, contentLength=4
Calling callback.onChunk for messageId: yyy, index: 0
Sending chunk to WebSocket session xxx: index=0, contentLength=4
...
Handling legacy message for session session_xxx: role=assistant, isComplete=true, contentLength=78
Sending chunk to WebSocket session xxx: index=14, contentLength=78
```

#### D. Trong Browser DevTools

**Console tab**: Không có errors

**Network → WS tab**: Click vào WebSocket connection → Messages
Bạn sẽ thấy messages:
```json
{"type":"welcome","sessionId":"session_xxx","timestamp":"..."}
{"type":"message","data":{"message_id":"yyy","role":"assistant","content":"Xin ","is_complete":false,...}}
{"type":"message","data":{"message_id":"yyy","role":"assistant","content":"Xin chào! ","is_complete":false,...}}
...
{"type":"message","data":{"message_id":"yyy","role":"assistant","content":"Xin chào! Tôi là AI assistant...","is_complete":true,...}}
```

## ✅ Checklist - Streaming hoạt động đúng

Đánh dấu vào các mục sau:

### Python Service
- [ ] Log "Starting AI response streaming" xuất hiện
- [ ] Log "Published to chat:stream" cho mỗi chunk
- [ ] **subscribers=1 hoặc hơn** (không phải 0!)
- [ ] Log "Completed AI response streaming" với số chunks đúng
- [ ] Không có error logs

### Java Service
- [ ] Log "ChatOrchestrator received message" xuất hiện
- [ ] Log "Handling legacy message" cho mỗi chunk
- [ ] Log "Calling callback.onChunk"
- [ ] Log "Sending chunk to WebSocket"
- [ ] Không có error logs

### Frontend
- [ ] WebSocket status = "connected" (xanh)
- [ ] User message xuất hiện ngay
- [ ] AI response streaming từng từ một
- [ ] Streaming indicator (3 dots) hiển thị
- [ ] Complete message cuối cùng (no indicator)
- [ ] Không có console errors

### Browser DevTools
- [ ] WebSocket connection status 101
- [ ] Nhận được messages type="message"
- [ ] Message data có đúng format ChatMessage
- [ ] is_complete=false cho streaming chunks
- [ ] is_complete=true cho final message

## ❌ Troubleshooting

### Vấn đề 1: subscribers=0 trong Python logs

**Nghĩa là**: Java server không subscribe Redis channel

**Kiểm tra**:
```bash
docker compose logs java-websocket-server | grep "Subscribed to legacy channel"
```

Phải thấy: `Subscribed to legacy channel: chat:stream:xxx with listener`

**Nếu không thấy**:
- WebSocket chưa connect → Check frontend connection
- `ChatOrchestrator.startStreamingSession()` không được gọi → Check Java logs khi WebSocket connect

### Vấn đề 2: Python publish nhưng Java không receive

**Kiểm tra Redis**:
```bash
# Test Redis PubSub trực tiếp
python3 /workspace/test_redis_pubsub.py
```

**Hoặc manual test**:
```bash
# Terminal 1
docker compose exec redis redis-cli
> SUBSCRIBE chat:stream:test_session

# Terminal 2
docker compose exec redis redis-cli
> PUBLISH chat:stream:test_session "test message"

# Terminal 1 phải nhận được message
```

### Vấn đề 3: Java receive nhưng không send qua WebSocket

**Kiểm tra**:
```bash
docker compose logs java-websocket-server | grep -E "(WebSocket session.*is not open|Failed to send)"
```

Có thể WebSocket đã disconnect. Refresh browser và thử lại.

### Vấn đề 4: Frontend không hiển thị streaming

**Kiểm tra Browser DevTools**:
- Network → WS → Messages: Có nhận được messages không?
- Console: Có errors không?

**Kiểm tra code**:
```javascript
// useChat.js phải handle cả streaming và complete messages
if (message.is_complete) {
  // Final message
} else {
  // Streaming chunk
}
```

### Vấn đề 5: Streaming quá nhanh/chậm

**Adjust delays trong environment**:
```bash
# Tạo file .env hoặc edit docker-compose.yml
STREAM_DELAY=0.5  # Chậm hơn, dễ thấy
CHUNK_DELAY=0.2

# Hoặc nhanh hơn
STREAM_DELAY=0.1
CHUNK_DELAY=0.05
```

Rebuild sau khi thay đổi:
```bash
docker compose down
docker compose up --build -d
```

## 🔧 Advanced Testing

### Test với nhiều messages liên tiếp

Gửi nhiều messages nhanh để test:
1. "hello"
2. "how are you"  
3. "tell me about redis"

Mỗi message phải streaming riêng biệt, không bị overlap.

### Test với long message

Gửi message dài để thấy rõ streaming:
```
"Hãy giải thích chi tiết về kiến trúc của hệ thống chat streaming này"
```

### Test reconnection

1. Gửi message và đợi streaming
2. Tắt WiFi giữa chừng
3. Bật lại WiFi
4. Kiểm tra recovery

### Load test

Sử dụng curl để test:
```bash
for i in {1..10}; do
  curl -X POST http://localhost:8000/chat \
    -H "Content-Type: application/json" \
    -d "{\"session_id\":\"test_$i\",\"user_id\":\"user_$i\",\"message\":\"test $i\"}" &
done
wait
```

## 📊 Performance Metrics

Với default settings (STREAM_DELAY=0.3s):
- Message ~15 words ≈ 4-5 giây streaming
- Message ~30 words ≈ 9-10 giây streaming

Với production settings (STREAM_DELAY=0.1s):
- Message ~15 words ≈ 1.5-2 giây streaming
- Message ~30 words ≈ 3-4 giây streaming

## 📝 Summary

Tất cả phải hoạt động theo flow:

```
User sends message
    ↓
Frontend → POST /api/chat → Python AI Service
    ↓
Python streaming response
    ↓
Publish từng chunk lên Redis (chat:stream:{session_id})
    ↓
Java ChatOrchestrator receive messages
    ↓
Convert StreamChunk → ChatMessage
    ↓
Send qua WebSocket
    ↓
Frontend receive và display streaming
    ↓
Complete! ✅
```

Nếu bất kỳ bước nào fail, check logs để xác định vị trí chính xác.

## 🎉 Success Criteria

Khi streaming hoạt động đúng:
1. ✅ User message xuất hiện ngay lập tức
2. ✅ AI response streaming từng từ một với delay ~0.3s
3. ✅ Streaming indicator hiển thị và biến mất đúng lúc
4. ✅ Logs đầy đủ ở cả Python và Java
5. ✅ subscribers >= 1 trong Python logs
6. ✅ Không có errors trong bất kỳ logs nào

Nếu tất cả các điều trên đều OK → Streaming đã hoạt động hoàn hảo! 🎊
