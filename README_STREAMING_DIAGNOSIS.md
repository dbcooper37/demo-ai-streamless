# 🎯 Hướng dẫn Chẩn đoán và Sửa lỗi Streaming

## 📋 Bạn đang gặp vấn đề gì?

Khi gọi:
```bash
curl http://localhost:8000/chat -H "Content-Type: application/json" \
  -d '{"session_id":"session_1762763489283","message":"123","user_id":"demo_user"}'
```

Bạn chỉ nhận được:
```json
{
    "status": "streaming",
    "message_id": "f282b268-06ab-4123-844c-31a91745dbd8",
    "session_id": "session_1762763489283",
    "message": "AI is generating response..."
}
```

Và **không có streaming messages** sau đó.

## ✅ Tin tốt: Đây KHÔNG PHẢI lỗi!

Response này là **bình thường**. Curl không thể nhận streaming vì streaming qua WebSocket, không phải HTTP.

## 🔍 Vấn đề thực sự

Sau khi kiểm tra kỹ, tôi phát hiện:

1. ✅ **Channels khớp nhau:** Python và Java đều dùng `chat:stream:{session_id}`
2. ✅ **Code đúng:** Cả publish và subscribe đều đúng
3. ⚠️  **Vấn đề tiềm ẩn:** Timing và synchronization

### Vấn đề chính: `subscribers=0`

Khi Python publish messages, nếu không có Java server nào đang subscribe channel, thì:
- Python logs: `subscribers=0`
- Messages bị mất
- Frontend không nhận gì cả

**Nguyên nhân:**
1. **WebSocket chưa connect** → Java chưa subscribe
2. **Session ID không khớp** giữa WebSocket và HTTP request
3. **Timing issue** → HTTP request gửi quá nhanh trước khi Java subscribe xong

## 🛠️ Giải pháp

### Option 1: Dùng Automated Test (Recommended)

```bash
# Cài dependencies
pip3 install websockets aiohttp

# Chạy test tự động
python3 test_streaming_websocket.py
```

Script này sẽ:
- ✅ Connect WebSocket trước
- ✅ Đợi connection ổn định
- ✅ Gọi HTTP /chat với session ID đúng
- ✅ Nhận và hiển thị streaming messages
- ✅ Báo kết quả PASS/FAIL

### Option 2: Dùng Frontend UI (Dễ nhất)

```bash
# Mở browser
open http://localhost:3000

# Gửi message bất kỳ
# ➡️ Sẽ thấy streaming ngay!
```

Frontend tự động:
- Connect WebSocket trước
- Generate session ID
- Dùng cùng session ID cho cả WebSocket và HTTP
- Handle timing đúng

### Option 3: Manual Test với wscat

```bash
# Terminal 1: Connect WebSocket
wscat -c "ws://localhost:8080/ws?session_id=test123&user_id=demo_user&token=dev-token"

# Đợi thấy welcome message: {"type":"welcome","sessionId":"test123"}

# Terminal 2: Gọi curl (dùng CÙNG session ID)
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test123","user_id":"demo_user","message":"xin chào"}'

# Terminal 1 sẽ hiển thị streaming messages!
```

**Quan trọng:** Session ID phải giống nhau!

## 📊 Kiểm tra xem streaming có hoạt động không

### 1. Check Python logs - Subscribers count

```bash
docker compose logs -f python-ai-service | grep "subscribers"
```

**Mong đợi:**
```
Published to chat:stream:session_xxx: subscribers=1 ✅
```

**Nếu thấy:**
```
Published to chat:stream:session_xxx: subscribers=0 ❌
```

→ **Java không subscribe!** WebSocket chưa connect hoặc session ID không khớp.

### 2. Check Java logs - Subscription

```bash
docker compose logs java-websocket-server | grep "Subscribed to legacy channel"
```

**Mong đợi:**
```
Subscribed to legacy channel: chat:stream:session_xxx with listener ✅
```

Nếu không thấy → WebSocket chưa connect.

### 3. Check Java logs - Receiving messages

```bash
docker compose logs -f java-websocket-server | grep "ChatOrchestrator received"
```

**Mong đợi:**
```
ChatOrchestrator received message from chat:stream:session_xxx ✅
Calling callback.onChunk ✅
Sending chunk to WebSocket ✅
```

## 🧪 Diagnostic Tools

Tôi đã tạo các tools để giúp bạn debug:

### 1. Test Streaming Websocket
```bash
python3 test_streaming_websocket.py
```
→ Test tự động toàn bộ flow

### 2. Diagnose Redis PubSub
```bash
./diagnose_redis_pubsub.sh
```
→ Check Redis connection, subscribers count, và logs

### 3. Channel Verification Report
```bash
cat CHANNEL_VERIFICATION_REPORT.md
```
→ Báo cáo chi tiết về channels và troubleshooting

## 📖 Đọc thêm

- **QUICK_TEST_STREAMING.md** - Hướng dẫn test nhanh
- **TEST_STREAMING_WITH_CURL.md** - Giải thích chi tiết về kiến trúc
- **CHANNEL_VERIFICATION_REPORT.md** - Báo cáo kiểm tra channels
- **HOW_TO_TEST_STREAMING.md** - Hướng dẫn test toàn diện

## 🎯 Quick Decision Tree

```
Bạn muốn gì?
│
├─ Test nhanh xem streaming có hoạt động không?
│  └─> python3 test_streaming_websocket.py
│
├─ Debug tại sao không có streaming?
│  └─> ./diagnose_redis_pubsub.sh
│
├─ Xem messages thực tế trong Redis?
│  └─> docker compose exec redis redis-cli
│      > PSUBSCRIBE chat:stream:*
│
└─ Dùng UI để test?
   └─> open http://localhost:3000
```

## 🔧 Common Issues & Fixes

### Issue 1: `subscribers=0` trong Python logs

**Cause:** Java không subscribe vì WebSocket chưa connect

**Fix:**
1. Connect WebSocket TRƯỚC
2. Đợi vài giây
3. Sau đó gọi HTTP /chat

**Test:**
```bash
python3 test_streaming_websocket.py
```

### Issue 2: Session ID không khớp

**Cause:** WebSocket dùng session ID khác với HTTP request

**Fix:** Dùng cùng một session ID

**Verify:**
```bash
# Check WebSocket session
docker compose logs java-websocket-server | grep "sessionId="

# So sánh với session_id trong curl request
```

### Issue 3: WebSocket không connect được

**Cause:** Java service chưa chạy hoặc có lỗi

**Fix:**
```bash
# Check services
docker compose ps

# Restart if needed
docker compose restart java-websocket-server

# Check logs
docker compose logs java-websocket-server
```

### Issue 4: Redis không hoạt động

**Cause:** Redis service có vấn đề

**Fix:**
```bash
# Test Redis
docker compose exec redis redis-cli ping
# Expect: PONG

# Restart if needed
docker compose restart redis
```

## ✅ Expected Success Scenario

Khi mọi thứ hoạt động đúng:

**1. Python logs:**
```
Starting AI response streaming for session=session_xxx
Published to chat:stream:session_xxx: subscribers=1 ✅
Published to chat:stream:session_xxx: subscribers=1 ✅
...
Completed AI response streaming: chunks=15 ✅
```

**2. Java logs:**
```
WebSocket connected: sessionId=session_xxx ✅
Subscribed to legacy channel: chat:stream:session_xxx ✅
ChatOrchestrator received message ✅
Calling callback.onChunk ✅
Sending chunk to WebSocket ✅
```

**3. Frontend/Client:**
- Nhận được messages type="message" ✅
- Content xuất hiện từng từ một ✅
- Streaming indicator hiển thị ✅
- Complete message cuối cùng ✅

## 🎓 Tóm tắt

1. **Curl không thể thấy streaming** - Đây là bình thường vì streaming qua WebSocket
2. **Channels đều đúng** - Python và Java dùng cùng format `chat:stream:{session_id}`
3. **Vấn đề chính** - Timing và session ID synchronization
4. **Giải pháp** - Dùng test script hoặc UI, đảm bảo WebSocket connect trước

**Test ngay:**
```bash
python3 test_streaming_websocket.py
```

Nếu test PASS → Streaming hoạt động! ✅
Nếu test FAIL → Check logs theo hướng dẫn trên ❌

## 🆘 Cần thêm trợ giúp?

Chạy diagnostic script:
```bash
./diagnose_redis_pubsub.sh
```

Script sẽ check:
- ✅ Redis connection
- ✅ Publish với subscribers count
- ✅ Java subscription logs
- ✅ Real-time monitoring commands

Sau đó check các logs cụ thể theo hướng dẫn trong output!
