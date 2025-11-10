# Tóm tắt sửa lỗi Streaming Message

## Vấn đề ban đầu
Khi gửi message từ frontend, message không được streaming về frontend mặc dù:
- Frontend gửi request thành công đến Python AI service
- Python AI service streaming messages đến Redis PubSub
- WebSocket connection đang hoạt động

## Nguyên nhân

### 1. **Duplicate Subscription**
Cả `RedisMessageListener` và `ChatOrchestrator` đều subscribe vào cùng channel `chat:stream:{session_id}`, gây ra:
- Confusion trong message routing
- Potential duplicate messages
- Không rõ listener nào đang xử lý messages

### 2. **Format Mismatch**
`ChatOrchestrator` chuyển đổi messages sang `StreamChunk` format nhưng:
- `StreamChunk` có fields: `messageId`, `index`, `content`, `type`, `timestamp` (Instant)
- Frontend expect `ChatMessage` format với: `message_id`, `session_id`, `user_id`, `role`, `content`, `timestamp` (number), `is_complete`, `chunk`
- Frontend không thể parse và hiển thị StreamChunk

### 3. **Content vs Chunk Confusion**
Python AI service gửi:
- `content`: accumulated content (text từ đầu đến giờ)
- `chunk`: current word

ChatOrchestrator đang dùng `chunk` thay vì `content`, làm cho:
- Chỉ hiển thị từng word riêng lẻ
- Không hiển thị accumulated content như mong đợi

## Giải pháp đã áp dụng

### 1. **Disable Duplicate Subscription**
```java
// ChatWebSocketHandler.afterConnectionEstablished()
// Commented out RedisMessageListener subscription
// redisMessageListener.subscribe(sessionId, this);

// Chỉ sử dụng ChatOrchestrator
chatOrchestrator.startStreamingSession(sessionId, userId,
        new WebSocketStreamCallback(wsSession));
```

**Lý do**: ChatOrchestrator có đầy đủ features hơn (caching, recovery, distributed coordination)

### 2. **Convert StreamChunk to ChatMessage Format**
```java
// ChatWebSocketHandler.sendChunk()
private void sendChunk(WebSocketSession wsSession, StreamChunk chunk) {
    // Convert StreamChunk -> ChatMessage format
    ChatMessage chatMessage = ChatMessage.builder()
            .messageId(chunk.getMessageId())
            .sessionId(sessionId)
            .userId(userId)
            .role("assistant")
            .content(chunk.getContent())
            .chunk(chunk.getContent())
            .timestamp(chunk.getTimestamp().toEpochMilli())
            .isComplete(false)
            .build();
    
    String payload = objectMapper.writeValueAsString(Map.of(
            "type", "message",
            "data", chatMessage
    ));
    
    wsSession.sendMessage(new TextMessage(payload));
}
```

Tương tự cho `sendCompleteMessage()`.

### 3. **Use Accumulated Content**
```java
// ChatOrchestrator.handleLegacyMessage()
StreamChunk chunk = StreamChunk.builder()
        .messageId(session.getMessageId())
        .index(context.chunkIndex.getAndIncrement())
        .content(chatMessage.getContent()) // Use accumulated content, not just chunk
        .type(StreamChunk.ChunkType.TEXT)
        .timestamp(Instant.now())
        .build();
```

### 4. **Enhanced Logging**
Thêm logging chi tiết ở tất cả các điểm quan trọng:
- RedisMessageListener.onMessage()
- ChatOrchestrator.subscribeToLegacyChannel()
- ChatOrchestrator.handleLegacyMessage()
- ChatWebSocketHandler.broadcastToSession()
- ChatWebSocketHandler.sendChunk()

## Luồng xử lý sau khi fix

```
┌─────────────┐
│  Frontend   │
└──────┬──────┘
       │ POST /api/chat
       ↓
┌─────────────────────┐
│ Python AI Service   │
└──────┬──────────────┘
       │ Publish to Redis
       │ channel: chat:stream:{session_id}
       │ format: ChatMessage (JSON)
       ↓
┌─────────────────────────────────────┐
│     Java WebSocket Server           │
│                                     │
│  ┌────────────────────────────┐   │
│  │   ChatOrchestrator         │   │
│  │   - Subscribe to channel   │   │
│  │   - Receive ChatMessage    │   │
│  │   - Convert to StreamChunk │   │
│  │   - Cache in Redis         │   │
│  │   - Call callback          │   │
│  └──────┬─────────────────────┘   │
│         │                           │
│  ┌──────▼──────────────────────┐  │
│  │ WebSocketStreamCallback     │  │
│  │   - onChunk()               │  │
│  │   - onComplete()            │  │
│  └──────┬──────────────────────┘  │
│         │                           │
│  ┌──────▼──────────────────────┐  │
│  │ ChatWebSocketHandler        │  │
│  │   - sendChunk()             │  │
│  │   - Convert StreamChunk     │  │
│  │     -> ChatMessage          │  │
│  │   - Send via WebSocket      │  │
│  └──────┬──────────────────────┘  │
└─────────┼──────────────────────────┘
          │ WebSocket message
          │ type: "message"
          │ data: ChatMessage
          ↓
    ┌─────────────┐
    │  Frontend   │
    │  - Receive  │
    │  - Display  │
    └─────────────┘
```

## File đã sửa đổi

1. **ChatWebSocketHandler.java**
   - Commented out RedisMessageListener subscription
   - Updated sendChunk() to convert StreamChunk -> ChatMessage
   - Updated sendCompleteMessage() to convert Message -> ChatMessage
   - Enhanced logging

2. **ChatOrchestrator.java**
   - Updated handleLegacyMessage() to use accumulated content
   - Enhanced logging for debugging

3. **RedisMessageListener.java**
   - Enhanced logging (still works for future use)

## Testing

Để test sau khi deploy:

1. **Start services**:
   ```bash
   docker compose up --build
   ```

2. **Open frontend**: http://localhost:3000

3. **Gửi message** và kiểm tra:
   - Message xuất hiện ngay lập tức
   - AI response được streaming word-by-word
   - Mỗi chunk update accumulated content
   - Complete message được hiển thị đúng

4. **Check logs**:
   ```bash
   # Java WebSocket Server
   docker compose logs -f java-websocket-server | grep -E "(ChatOrchestrator|sendChunk|Broadcasting)"
   
   # Python AI Service
   docker compose logs -f python-ai-service | grep -E "(publish|streaming)"
   ```

## Expected Behavior

1. User gửi message "Hello"
2. User message xuất hiện ngay
3. AI response bắt đầu streaming:
   - "Xin " (streaming indicator hiển thị)
   - "Xin chào! " (streaming indicator hiển thị)
   - "Xin chào! Tôi " (streaming indicator hiển thị)
   - ... (tiếp tục cho đến hết)
4. Final message hoàn chỉnh (streaming indicator biến mất)

## Troubleshooting

Nếu vẫn không streaming:

1. **Check logs** để xem:
   - Python có publish messages không?
   - ChatOrchestrator có receive messages không?
   - sendChunk() có được gọi không?
   - WebSocket có send messages không?

2. **Check Redis**:
   ```bash
   docker compose exec redis redis-cli
   > SUBSCRIBE chat:stream:session_*
   ```
   Gửi message và xem có messages trong Redis không

3. **Check WebSocket connection**:
   - Open browser DevTools -> Network -> WS
   - Xem messages được gửi/nhận qua WebSocket

4. **Check frontend console**:
   - Có errors không?
   - Messages được receive đúng format không?

## Notes

- RedisMessageListener vẫn có thể được sử dụng trong tương lai nếu cần legacy support
- ChatOrchestrator handle cả legacy format (ChatMessage) và enhanced format (StreamChunk)
- Frontend hiện tại chỉ cần handle type "message" với ChatMessage format
- Hệ thống hiện tại support cả single-node và multi-node deployment
