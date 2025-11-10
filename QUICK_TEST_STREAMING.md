# ⚡ Quick Test: Kiểm tra Streaming trong 2 phút

## 🎯 Vấn đề: Curl không hiển thị streaming

Khi bạn gọi:
```bash
curl -X POST http://localhost:8000/chat -H "Content-Type: application/json" -d '...'
```

Bạn chỉ nhận được:
```json
{
    "status": "streaming",
    "message_id": "...",
    "message": "AI is generating response..."
}
```

**➡️ ĐÂY LÀ ĐÚNG!** Curl không thể nhận streaming vì streaming qua WebSocket, không phải HTTP.

## ✅ Cách test nhanh

### Method 1: Dùng Python Script (Recommended)

```bash
# Cài dependencies
pip3 install websockets aiohttp

# Chạy test
python3 test_streaming_websocket.py
```

Script này sẽ:
1. ✅ Connect WebSocket
2. ✅ Gọi HTTP POST /chat
3. ✅ Nhận và hiển thị streaming messages
4. ✅ Báo kết quả: PASS/FAIL

### Method 2: Dùng wscat (nếu có Node.js)

```bash
# Cài wscat
npm install -g wscat

# Terminal 1: Connect WebSocket
./test_streaming_simple.sh

# Terminal 2: Gọi curl (theo hướng dẫn trong Terminal 1)
```

### Method 3: Dùng Frontend UI (Dễ nhất)

```bash
# Mở browser
open http://localhost:3000

# Gửi message "xin chào"
# ➡️ Sẽ thấy streaming ngay!
```

## 🔍 Kiểm tra logs

### Check Python streaming:

```bash
docker compose logs -f python-ai-service | grep -E "(Starting|Published|subscribers)"
```

**Mong đợi thấy:**
```
Starting AI response streaming for session=xxx
Published to chat:stream:xxx: subscribers=1  ← QUAN TRỌNG! Phải >= 1
Completed AI response streaming: chunks=15
```

Nếu `subscribers=0` → Không có Java server nào đang lắng nghe!

### Check Java forwarding:

```bash
docker compose logs -f java-websocket-server | grep -E "(ChatOrchestrator|sendChunk)"
```

**Mong đợi thấy:**
```
ChatOrchestrator received message from chat:stream:xxx
Sending chunk to WebSocket session xxx
```

## 📖 Documentation đầy đủ

Xem file chi tiết:
- [`TEST_STREAMING_WITH_CURL.md`](TEST_STREAMING_WITH_CURL.md) - Giải thích đầy đủ kiến trúc
- [`HOW_TO_TEST_STREAMING.md`](HOW_TO_TEST_STREAMING.md) - Hướng dẫn test toàn diện

## 🎓 Giải thích ngắn gọn

```
┌──────────┐
│  Curl    │  POST /chat
└────┬─────┘
     │
     ↓
┌─────────────────┐
│ Python Service  │  Return: {"status": "streaming"} ← CHỈ CÓ THẾ NÀY!
└────┬────────────┘
     │
     │ Publish to Redis PubSub
     ↓
┌─────────────────┐
│ Java WS Server  │  Subscribe Redis
└────┬────────────┘
     │
     │ Forward via WebSocket
     ↓
┌─────────────────┐
│  WebSocket      │  Nhận streaming messages ← CẦN WEBSOCKET!
│  Client         │
└─────────────────┘
```

**Kết luận:** Curl KHÔNG THỂ nhận streaming. Cần WebSocket client!

## 🚀 TL;DR

```bash
# Test ngay với 1 command:
python3 test_streaming_websocket.py

# Hoặc mở UI:
open http://localhost:3000
```

Nếu thấy message xuất hiện từng từ một ➡️ Streaming OK! ✅
