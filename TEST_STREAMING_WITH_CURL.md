# Hướng dẫn Test Streaming với Curl

## ⚠️ Vấn đề bạn đang gặp

Khi gọi endpoint `/chat` bằng curl, bạn chỉ nhận được:

```json
{
    "status": "streaming",
    "message_id": "f282b268-06ab-4123-844c-31a91745dbd8",
    "session_id": "session_1762763489283",
    "message": "AI is generating response..."
}
```

**ĐÂY LÀ ĐÚNG!** Endpoint `/chat` không stream qua HTTP response.

## 🏗️ Kiến trúc hệ thống

```
┌─────────────────────────────────────────────────────────────┐
│  1. HTTP POST /chat                                         │
│     └─> Python AI Service                                   │
│         └─> Return immediately: "streaming..."              │
│         └─> Start async task to generate response           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  2. Python streams to Redis PubSub                          │
│     Channel: chat:stream:{session_id}                       │
│     Format: ChatMessage JSON                                │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  3. Java WebSocket Server subscribes to Redis               │
│     ChatOrchestrator receives messages                      │
│     Converts to WebSocket format                            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  4. Sends via WebSocket to Frontend                         │
│     ws://localhost:8080/ws?session_id=xxx&user_id=xxx       │
└─────────────────────────────────────────────────────────────┘
```

## 🔍 Cách kiểm tra Streaming đang hoạt động

### Bước 1: Kiểm tra Python logs

```bash
docker compose logs -f python-ai-service | grep -E "(Starting|Published|Completed|subscribers)"
```

**Mong đợi thấy:**
```
Starting AI response streaming for session=session_1762763489283, msg_id=xxx
Published to chat:stream:session_1762763489283: role=assistant, is_complete=False, content_len=4, subscribers=1
Published to chat:stream:session_1762763489283: role=assistant, is_complete=False, content_len=10, subscribers=1
...
Published to chat:stream:session_1762763489283: role=assistant, is_complete=True, content_len=78, subscribers=1
Completed AI response streaming: session=session_1762763489283, chunks=15
```

**QUAN TRỌNG:** Kiểm tra `subscribers=1` (hoặc hơn). Nếu `subscribers=0` → có vấn đề!

### Bước 2: Kiểm tra Java logs

```bash
docker compose logs -f java-websocket-server | grep -E "(ChatOrchestrator|sendChunk|Broadcasting)"
```

**Mong đợi thấy:**
```
ChatOrchestrator received message from chat:stream:session_1762763489283
Handling legacy message for session session_1762763489283: role=assistant, isComplete=false
Calling callback.onChunk for messageId: xxx
Sending chunk to WebSocket session xxx
...
```

### Bước 3: Test với WebSocket Client

Để thấy streaming, bạn cần kết nối WebSocket TRƯỚC KHI gọi `/chat`:

#### Option 1: Dùng wscat (WebSocket client CLI)

```bash
# Install wscat nếu chưa có
npm install -g wscat

# Connect to WebSocket
wscat -c "ws://localhost:8080/ws?session_id=session_1762763489283&user_id=demo_user&token=dev-token"

# Trong terminal khác, gọi curl
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "session_1762763489283",
    "user_id": "demo_user",
    "message": "123"
  }'
```

Trong terminal wscat, bạn sẽ thấy streaming messages:

```json
{"type":"welcome","sessionId":"session_1762763489283"}
{"type":"message","data":{"message_id":"xxx","role":"user","content":"123",...}}
{"type":"message","data":{"message_id":"yyy","role":"assistant","content":"Xin ","is_complete":false,...}}
{"type":"message","data":{"message_id":"yyy","role":"assistant","content":"Xin chào! ","is_complete":false,...}}
{"type":"message","data":{"message_id":"yyy","role":"assistant","content":"Xin chào! Tôi ","is_complete":false,...}}
...
{"type":"message","data":{"message_id":"yyy","role":"assistant","content":"Xin chào! Tôi là AI assistant. Tôi có thể giúp gì cho bạn hôm nay?","is_complete":true,...}}
```

#### Option 2: Dùng websocat

```bash
# Install websocat
# Linux: wget https://github.com/vi/websocat/releases/download/v1.12.0/websocat.x86_64-unknown-linux-musl -O websocat
# Mac: brew install websocat

# Connect
websocat "ws://localhost:8080/ws?session_id=session_1762763489283&user_id=demo_user&token=dev-token"
```

#### Option 3: Dùng Python script

```python
#!/usr/bin/env python3
import asyncio
import websockets
import json

async def test_streaming():
    uri = "ws://localhost:8080/ws?session_id=session_1762763489283&user_id=demo_user&token=dev-token"
    
    async with websockets.connect(uri) as websocket:
        print("✅ Connected to WebSocket")
        
        # Listen for messages
        async def receive_messages():
            while True:
                try:
                    message = await websocket.recv()
                    data = json.loads(message)
                    print(f"📨 Received: {data}")
                except Exception as e:
                    print(f"❌ Error: {e}")
                    break
        
        # Start listening
        asyncio.create_task(receive_messages())
        
        # Keep connection alive
        await asyncio.sleep(60)

if __name__ == "__main__":
    asyncio.run(test_streaming())
```

Chạy script này TRƯỚC KHI gọi curl.

### Bước 4: Hoặc test với Frontend (Recommended)

```bash
# Mở browser
open http://localhost:3000

# Gửi message "123" trong UI
# Bạn sẽ thấy streaming trực tiếp
```

## 🔧 Debug Checklist

Nếu không thấy streaming:

### ✅ Python Service
- [ ] Python logs có "Starting AI response streaming"?
- [ ] Python logs có "Published to chat:stream"?
- [ ] `subscribers >= 1` (không phải 0)?
- [ ] Python logs có "Completed AI response streaming"?

### ✅ Java Service
- [ ] Java logs có "ChatOrchestrator received message"?
- [ ] Java logs có "Handling legacy message"?
- [ ] Java logs có "Sending chunk to WebSocket"?
- [ ] Java service đã subscribe channel chưa?

### ✅ Redis
- [ ] Redis service đang chạy?
- [ ] Test Redis PubSub:
```bash
# Terminal 1
docker compose exec redis redis-cli
SUBSCRIBE chat:stream:session_1762763489283

# Terminal 2
# Gọi curl ở terminal này

# Terminal 1 phải nhận được messages
```

### ✅ WebSocket Connection
- [ ] WebSocket client đã connect TRƯỚC KHI gọi curl?
- [ ] Session ID trong WebSocket URL khớp với session_id trong curl?
- [ ] User ID khớp?

## 💡 Giải pháp nhanh

Để test ngay:

```bash
# Terminal 1: WebSocket client
wscat -c "ws://localhost:8080/ws?session_id=test123&user_id=demo_user&token=dev-token"

# Terminal 2: Curl request
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test123",
    "user_id": "demo_user",
    "message": "xin chào"
  }'

# Terminal 1 sẽ hiển thị streaming messages!
```

## 📝 Kết luận

**Curl KHÔNG THỂ nhận streaming messages** vì:
- Streaming qua WebSocket, không phải HTTP
- HTTP response chỉ trả về status ban đầu
- Cần WebSocket client để nhận streaming

**Để test đầy đủ:**
1. Mở WebSocket connection trước
2. Gọi HTTP POST /chat
3. Nhận streaming qua WebSocket

**Cách dễ nhất:** Dùng frontend UI tại http://localhost:3000
