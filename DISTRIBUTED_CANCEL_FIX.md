# Distributed Cancellation Fix

## Vấn đề

Khi triển khai hệ thống phân tán với nhiều instances của AI service và round-robin load balancing, việc cancel streaming gặp phải vấn đề:

### Triệu chứng
- Khi nhấn nút "Hủy" trong quá trình streaming, thường không hoạt động ngay lần đầu
- Phải click nhiều lần mới hủy được
- Message lỗi: "Message already completed: No active streaming task found - the message may have already completed"

### Nguyên nhân
```
┌─────────────┐     Round Robin      ┌──────────────┐
│   Frontend  │─────────────────────▶│ Load Balancer│
└─────────────┘                      └──────────────┘
                                            │
                         ┌──────────────────┼──────────────────┐
                         ▼                  ▼                  ▼
                   ┌──────────┐      ┌──────────┐      ┌──────────┐
                   │ AI Svc 1 │      │ AI Svc 2 │      │ AI Svc 3 │
                   └──────────┘      └──────────┘      └──────────┘
                   Memory:            Memory:            Memory:
                   active_tasks       active_tasks       active_tasks
                   {session: msg1}    {empty}            {empty}
```

**Vấn đề:**
1. Request streaming đến AI Service Instance #1 → Instance #1 lưu `active_tasks` trong memory
2. Request cancel đến AI Service Instance #2 (do round robin) → Instance #2 không tìm thấy task trong memory của nó
3. Phải click nhiều lần cho đến khi round robin đưa request đến đúng instance đang xử lý

## Giải pháp

### Sử dụng Redis làm distributed state store

Thay vì lưu `active_tasks` trong memory của từng instance, chúng ta lưu state vào Redis:

```
┌─────────────┐     Round Robin      ┌──────────────┐
│   Frontend  │─────────────────────▶│ Load Balancer│
└─────────────┘                      └──────────────┘
                                            │
                         ┌──────────────────┼──────────────────┐
                         ▼                  ▼                  ▼
                   ┌──────────┐      ┌──────────┐      ┌──────────┐
                   │ AI Svc 1 │      │ AI Svc 2 │      │ AI Svc 3 │
                   └─────┬────┘      └─────┬────┘      └─────┬────┘
                         │                 │                  │
                         └────────┬────────┴──────────────────┘
                                  ▼
                           ┌─────────────┐
                           │    REDIS    │
                           │             │
                           │ Active:     │
                           │ session→msg │
                           │             │
                           │ Cancel:     │
                           │ session:msg │
                           └─────────────┘
```

### Implementation Details

#### 1. Redis Keys Structure

```python
# Track active streaming tasks
Key: chat:active:{session_id}
Value: {message_id}
TTL: 300 seconds

# Track cancel flags
Key: chat:cancel:{session_id}:{message_id}
Value: "1"
TTL: 60 seconds
```

#### 2. New Redis Methods

**redis_client.py:**
```python
# Register active streaming task (called when streaming starts)
def register_active_stream(session_id: str, message_id: str, ttl: int = 300)

# Get active streaming task
def get_active_stream(session_id: str) -> str

# Clear active streaming task (called when streaming ends)
def clear_active_stream(session_id: str)

# Set cancel flag (called when user clicks cancel)
def set_cancel_flag(session_id: str, message_id: str, ttl: int = 60)

# Check cancel flag (called in streaming loop)
def check_cancel_flag(session_id: str, message_id: str) -> bool

# Clear cancel flag (cleanup)
def clear_cancel_flag(session_id: str, message_id: str)
```

#### 3. Updated ChatService

**Trước:**
```python
class ChatService:
    def __init__(self):
        self.active_tasks = {}  # In-memory, per instance ❌
        self.completed_messages = {}
```

**Sau:**
```python
class ChatService:
    def __init__(self):
        # Removed in-memory state ✅
        # Now using Redis for distributed state management
```

**Streaming loop:**
```python
async def stream_ai_response(self, session_id, user_id, user_message):
    message_id = str(uuid.uuid4())
    
    # Register in Redis (visible to all instances)
    redis_client.register_active_stream(session_id, message_id)
    
    try:
        async for chunk in AIService.generate_streaming_response(text):
            # Check Redis for cancel flag (works across all instances)
            if redis_client.check_cancel_flag(session_id, message_id):
                cancelled = True
                break
            # ... publish chunk
    finally:
        # Cleanup Redis state
        redis_client.clear_active_stream(session_id)
        redis_client.clear_cancel_flag(session_id, message_id)
```

**Cancel method:**
```python
def cancel_streaming(self, session_id, message_id):
    # Check Redis for active task (visible to all instances)
    active_message_id = redis_client.get_active_stream(session_id)
    
    if active_message_id == message_id:
        # Set cancel flag in Redis (visible to all instances)
        redis_client.set_cancel_flag(session_id, message_id)
        return True
    
    # Even if not found, set flag as precaution (race condition)
    redis_client.set_cancel_flag(session_id, message_id, ttl=10)
    return True
```

## Lợi ích

### ✅ Cancel hoạt động ngay lần đầu
- Không cần click nhiều lần
- Request cancel có thể đến bất kỳ instance nào đều work

### ✅ Xử lý race conditions
- Nếu message vừa complete ngay khi user click cancel, vẫn handle gracefully
- Set cancel flag với TTL ngắn để handle edge cases

### ✅ Scalability
- Có thể scale lên nhiều AI service instances
- Load balancer có thể dùng bất kỳ strategy nào (round robin, least connections, etc.)
- State được sync tự động qua Redis

### ✅ Better user experience
- Responsive cancellation
- Clear status messages
- No confusing error messages

## Testing

### Test trong môi trường multi-node

```bash
# Run test script
./test_distributed_cancel.sh
```

Test script sẽ:
1. ✅ Kiểm tra health của services
2. ✅ Test cancel ngay lập tức
3. ✅ Test rapid cancel clicks (multiple clicks)
4. ✅ Test cancel message đã complete
5. ✅ Test cancel trong quá trình streaming dài

### Manual testing

1. **Start multi-node environment:**
```bash
docker-compose -f docker-compose.multi-node.yml up -d
```

2. **Open frontend và test:**
- Gửi message dài để AI streaming
- Click "Hủy" ngay lập tức
- Quan sát: Cancel phải work ngay lần đầu

3. **Verify logs:**
```bash
# Instance 1 logs
docker logs -f workspace-python-ai-1

# Instance 2 logs  
docker logs -f workspace-python-ai-2

# Instance 3 logs
docker logs -f workspace-python-ai-3
```

Logs sẽ cho thấy:
- "Set cancel flag in Redis" khi nhận cancel request
- "Streaming cancelled (via Redis)" khi streaming task phát hiện cancel flag
- Không quan trọng instance nào nhận request nào

## Migration Notes

### Changes Required

**Files modified:**
- `python-ai-service/redis_client.py` - Added distributed state methods
- `python-ai-service/ai_service.py` - Removed in-memory state, use Redis
- `python-ai-service/app.py` - Updated cancel endpoint response

**No database migration needed** - All state is in Redis with TTL

**No breaking changes** - API contracts remain the same

### Rollback Plan

Nếu có issues, rollback về version cũ:
```bash
git checkout HEAD~1 -- python-ai-service/
docker-compose restart python-ai
```

## Performance Impact

### Redis Operations per Request

**Streaming request:**
- 1 SETEX (register active stream)
- N GET calls (check cancel flag in loop)
- 2 DEL calls (cleanup)

**Cancel request:**
- 1 GET (check active stream)
- 1 SETEX (set cancel flag)

### Expected overhead
- ~1-2ms per Redis operation
- Negligible compared to AI generation time
- Redis is co-located in same network

### Monitoring
Monitor Redis performance:
```bash
# Redis slow log
redis-cli slowlog get 10

# Memory usage
redis-cli info memory
```

## Future Improvements

### Possible enhancements:
1. **Pub/Sub for instant cancel notification** (instead of polling)
2. **Redis Streams** for better message tracking
3. **TTL optimization** based on average streaming duration
4. **Batch cancel** for multiple messages at once

## References

- Original issue: Multiple clicks needed to cancel in distributed environment
- Related docs: `docs/KAFKA_MULTI_NODE_ARCHITECTURE.md`
- Redis TTL docs: https://redis.io/commands/expire
- Distributed state management patterns: https://microservices.io/patterns/data/shared-database.html
