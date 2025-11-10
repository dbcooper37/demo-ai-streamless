# 🚀 Demo AI Streaming Chat with Persistent History

Demo về hệ thống chat AI streaming với khả năng lưu trữ lịch sử khi user reload trang.

**Demo of AI streaming chat system with persistent history when user reloads the page.**

---

## 📋 Mô tả | Description

### Tiếng Việt

Hệ thống này giải quyết bài toán: **User đang nhận streaming response từ AI, nhưng khi reload trang, làm sao để vừa xem được lịch sử chat cũ, vừa tiếp tục nhận streaming mới?**

**Kiến trúc:**
```
AI Response → Redis PubSub → WebSocket Server → Client
                    ↓
              Redis Storage (Chat History)
```

**Các module:**
1. **Python AI Service** - Mô phỏng AI, publish streaming chunks to Redis PubSub
2. **Java WebSocket Server** - Subscribe Redis PubSub, persist history, forward to clients
3. **React Frontend** - WebSocket client with reconnection & history loading

### English

This system solves the problem: **User is receiving streaming response from AI, but when reloading the page, how to both see old chat history and continue receiving new streaming?**

**Architecture:**
```
AI Response → Redis PubSub → WebSocket Server → Client
                    ↓
              Redis Storage (Chat History)
```

**Modules:**
1. **Python AI Service** - Simulates AI, publishes streaming chunks to Redis PubSub
2. **Java WebSocket Server** - Subscribes Redis PubSub, persists history, forwards to clients
3. **React Frontend** - WebSocket client with reconnection & history loading

---

## 🎯 Tính năng chính | Key Features

✅ **Streaming real-time** - AI response được stream theo từng chunk
✅ **Persistent History** - Lịch sử chat được lưu trong Redis
✅ **Auto Reconnection** - WebSocket tự động kết nối lại khi mất kết nối
✅ **Resume on Reload** - Reload trang vẫn thấy toàn bộ lịch sử + tiếp tục nhận streaming
✅ **Session Management** - Mỗi session có lịch sử riêng biệt

---

## 🛠️ Tech Stack

| Module | Technology |
|--------|-----------|
| AI Service | Python 3.11, FastAPI, Redis |
| WebSocket Server | Java 17, Spring Boot, WebSocket, Redis PubSub |
| Frontend | React 18, Vite, WebSocket API |
| Message Broker & Storage | Redis 7 |
| Orchestration | Docker Compose |

---

## 🚀 Hướng dẫn chạy | How to Run

### Prerequisites

- Docker & Docker Compose
- Ports 3000, 8000, 8080, 6379 phải trống

### 1. Clone repository

```bash
git clone <repository-url>
cd demo-ai-streamless
```

### 2. Chạy tất cả services với Docker Compose

```bash
docker-compose up --build
```

Đợi khoảng 2-3 phút để build xong. Bạn sẽ thấy:
- ✅ Redis running on port 6379
- ✅ Python AI Service on port 8000
- ✅ Java WebSocket Server on port 8080
- ✅ React Frontend on port 3000

### 3. Truy cập ứng dụng

Mở trình duyệt: **http://localhost:3000**

---

## 🎮 Cách test tính năng | How to Test

### Test 1: Streaming cơ bản

1. Mở http://localhost:3000
2. Gửi tin nhắn: "Xin chào"
3. Xem AI response streaming từng chữ một

### Test 2: Reload trong khi streaming (QUAN TRỌNG!)

1. Gửi một tin nhắn dài: "Hãy nói về streaming và reload"
2. **Trong khi AI đang trả lời**, reload trang (F5 hoặc Ctrl+R)
3. ✅ Kết quả: Bạn sẽ thấy:
   - Toàn bộ lịch sử chat cũ
   - Tin nhắn AI đang streaming tiếp tục hiển thị real-time

### Test 3: Multiple sessions

1. Mở tab mới với cùng URL
2. Session ID sẽ khác nhau (được lưu trong localStorage)
3. Mỗi session có lịch sử riêng biệt

### Test 4: Reconnection

1. Tắt container `demo-java-websocket`:
   ```bash
   docker stop demo-java-websocket
   ```
2. Trên UI sẽ hiện "Đang kết nối lại..."
3. Bật lại:
   ```bash
   docker start demo-java-websocket
   ```
4. ✅ WebSocket tự động kết nối lại và load history

---

## 📡 API Endpoints

### Python AI Service (Port 8000)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| POST | `/chat` | Send message and trigger AI streaming |
| GET | `/history/{session_id}` | Get chat history |

**Example:**
```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test123",
    "message": "Xin chào",
    "user_id": "user1"
  }'
```

### Java WebSocket Server (Port 8080)

| Endpoint | Protocol | Description |
|----------|----------|-------------|
| `/ws/chat?session_id=xxx` | WebSocket | WebSocket connection |
| `/api/health` | HTTP GET | Health check |

**WebSocket Message Format:**

History (on connect):
```json
{
  "type": "history",
  "messages": [...]
}
```

Streaming Message:
```json
{
  "type": "message",
  "data": {
    "message_id": "uuid",
    "role": "assistant",
    "content": "Hello, how...",
    "is_complete": false
  }
}
```

---

## 🏗️ Kiến trúc chi tiết | Detailed Architecture

### Component Responsibilities:

**1. Python AI Service (python-ai-service/app.py):**
- Nhận request từ user qua REST API
- Mô phỏng AI generating response (streaming word by word)
- Publish mỗi chunk vào Redis PubSub: `chat:stream:{session_id}`
- Lưu message hoàn chỉnh vào Redis List: `chat:history:{session_id}`

**2. Java WebSocket Server:**
- Subscribe Redis PubSub channels theo session
- Forward streaming messages đến WebSocket clients
- Khi client connect: gửi chat history từ Redis
- Quản lý multiple WebSocket connections per session

**3. React Frontend (frontend/src/App.jsx):**
- Kết nối WebSocket với session_id (lưu trong localStorage)
- Nhận history ngay khi connect
- Hiển thị streaming messages real-time
- Auto-reconnect khi mất kết nối

**4. Redis:**
- **PubSub**: Channel `chat:stream:{session_id}` cho streaming
- **List**: Key `chat:history:{session_id}` cho persistent storage
- **TTL**: 24 hours (có thể config)

---

## 📦 Project Structure

```
demo-ai-streamless/
├── python-ai-service/          # Python FastAPI service
│   ├── app.py                  # Main application
│   ├── requirements.txt        # Python dependencies
│   └── Dockerfile
│
├── java-websocket-server/      # Java Spring Boot WebSocket
│   ├── src/main/java/com/demo/websocket/
│   │   ├── config/             # WebSocket & Redis config
│   │   ├── handler/            # ChatWebSocketHandler
│   │   ├── service/            # RedisMessageListener, ChatHistoryService
│   │   ├── model/              # ChatMessage
│   │   └── WebSocketServerApplication.java
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                   # React frontend
│   ├── src/
│   │   ├── App.jsx            # Main component with WebSocket
│   │   ├── main.jsx
│   │   └── index.css
│   ├── package.json
│   └── Dockerfile
│
├── docker-compose.yml
└── README.md
```

---

## 🎓 Học được gì từ demo này | What You Learn

1. **Redis PubSub** - Real-time messaging between services
2. **WebSocket** - Implement WebSocket với reconnection logic
3. **Streaming Architecture** - Design hệ thống streaming với persistence
4. **Session Management** - Quản lý sessions với Redis
5. **Multi-language Integration** - Python + Java + React
6. **Docker Orchestration** - Multi-container application

---

## 📝 License

MIT License - Free to use for learning and commercial projects.

---

**Happy Coding! 🚀**