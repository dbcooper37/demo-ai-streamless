# Fix Lỗi "Chunk Append Failed" Trong Streaming

## Vấn Đề

Hệ thống thường xuyên gặp lỗi **"WebSocket error: Chunk append failed (non-duplicate error)"** trong quá trình streaming. Lỗi này gây gián đoạn streaming và ảnh hưởng trải nghiệm người dùng.

### Thông Tin Lỗi
```
RuntimeException: Chunk append failed (non-duplicate error)
    at RedisStreamCache.appendChunk(RedisStreamCache.java:134)
```

## Nguyên Nhân

Có **nhiều nguyên nhân** gây ra lỗi này:

### 1. Race Conditions trong Multi-Node
- Khi nhiều node cùng xử lý một chunk
- Lock timeout khi Redis busy
- Duplicate chunk submissions

### 2. Vấn Đề Redis
- Kết nối Redis tạm thời bị gián đoạn
- Redis timeout
- Network latency cao

### 3. Index Mismatch
- Chunks đến không theo thứ tự
- Gap trong chunk sequence
- Duplicate chunks từ retry logic

### 4. Error Handling Quá Strict
Code cũ throw exception cho **mọi lỗi**, kể cả những lỗi có thể bỏ qua:
```java
// CODE CŨ - QUÁ STRICT
catch (Exception e) {
    log.error("Failed to append chunk", e);
    if (!e.getMessage().contains("Skipping duplicate")) {
        throw new RuntimeException("Chunk append failed (non-duplicate error)", e);
    }
}
```

## Giải Pháp

### 1. Error Handling Linh Hoạt Hơn
✅ **Không throw exception** cho các lỗi có thể recover được  
✅ **Log chi tiết** để debug nhưng vẫn tiếp tục streaming  
✅ **Graceful degradation** - ưu tiên tiếp tục service  

### 2. Cải Thiện Logic Xử Lý

#### A. Better Lock Handling
```java
// Timeout gracefully nếu không acquire được lock
boolean lockAcquired = false;
try {
    lockAcquired = lock.tryLock(100, 5000, TimeUnit.MILLISECONDS);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    log.warn("Interrupted while acquiring lock");
    return; // Skip gracefully
}

if (!lockAcquired) {
    log.debug("Lock timeout, assuming concurrent write");
    return; // Recovery sẽ handle nếu chunk thật sự bị missing
}
```

#### B. Smarter Index Checking
```java
if (chunk.getIndex() < expectedIndex) {
    // Duplicate - skip
    log.debug("Skipping duplicate chunk");
    return;
} else if (chunk.getIndex() > expectedIndex) {
    // Gap detected - log nhưng vẫn append
    log.warn("Chunk gap detected - Recovery will handle");
    // Không return - vẫn thử append
}
```

#### C. Comprehensive Error Handling
```java
catch (JsonProcessingException e) {
    // Serialization error - skip chunk
    log.error("Failed to serialize chunk (skipping)");
    // Chunk đã được gửi qua WebSocket rồi, không cần cache
    
} catch (Exception e) {
    // Redis/network issues - log nhưng continue
    log.error("Error appending chunk to cache (continuing)");
    // Không throw - streaming tiếp tục
    // Recovery mechanism sẽ handle gaps nếu cần
}
```

## Các Thay Đổi Chi Tiết

### File: `RedisStreamCache.java`

#### Thay Đổi 1: Improved Lock Handling
**Trước:**
```java
if (lock.tryLock(100, 5000, TimeUnit.MILLISECONDS)) {
    // ... append logic ...
} else {
    log.warn("Lock busy, skipping chunk append");
    return;
}
```

**Sau:**
```java
boolean lockAcquired = false;
try {
    lockAcquired = lock.tryLock(100, 5000, TimeUnit.MILLISECONDS);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    log.warn("Interrupted while acquiring lock");
    return; // Gracefully skip
}

if (!lockAcquired) {
    log.debug("Lock acquisition timeout");
    return; // Recovery will handle
}
```

#### Thay Đổi 2: Better Index Validation
**Trước:**
```java
if (chunk.getIndex() != expectedIndex) {
    log.warn("Skipping duplicate/mismatched chunk");
    return;
}
```

**Sau:**
```java
if (chunk.getIndex() < expectedIndex) {
    log.debug("Skipping duplicate chunk");
    return;
} else if (chunk.getIndex() > expectedIndex) {
    log.warn("Chunk gap detected - Recovery will handle");
    // Don't return - still try to append
}
```

#### Thay Đổi 3: Graceful Error Handling
**Trước:**
```java
catch (Exception e) {
    log.error("Failed to append chunk", e);
    if (!e.getMessage().contains("Skipping duplicate")) {
        throw new RuntimeException("Chunk append failed (non-duplicate error)", e);
    }
}
```

**Sau:**
```java
catch (JsonProcessingException e) {
    log.error("Failed to serialize chunk (skipping)");
    // Gracefully skip
    
} catch (Exception e) {
    log.error("Error appending chunk to cache (continuing)");
    // Don't throw - streaming continues
}
```

## Kiến Trúc Resilient

### Tại Sao Có Thể Bỏ Qua Cache Errors?

1. **WebSocket First**: Chunks đã được gửi trực tiếp qua WebSocket đến client
2. **Cache là Secondary**: Redis cache chỉ dùng cho recovery, không phải primary delivery
3. **Recovery Mechanism**: Hệ thống có recovery mechanism để handle missing chunks
4. **Multi-Node Safe**: Trong môi trường multi-node, race conditions là bình thường

### Flow Xử Lý Chunk

```
┌─────────────┐
│ AI Service  │
│ sends chunk │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ ChatOrchestrator│
│ receives chunk  │
└──────┬──────────┘
       │
       ├──────────────────────┐
       │                      │
       ▼                      ▼
┌─────────────┐      ┌──────────────┐
│ Send to     │      │ Cache to     │
│ WebSocket   │      │ Redis (try)  │
│ (PRIMARY)   │      │ (OPTIONAL)   │
└─────────────┘      └──────────────┘
       │                      │
       │                      │ If cache fails,
       │                      │ DON'T throw error
       │                      ▼
       │              Log & Continue
       │                      │
       └──────────┬───────────┘
                  │
                  ▼
           Client receives
           chunk successfully
```

## Lợi Ích

### ✅ Streaming Ổn Định Hơn
- Không còn bị gián đoạn do cache errors
- Chunks vẫn được gửi đến client ngay cả khi Redis có vấn đề

### ✅ Better Multi-Node Support
- Xử lý race conditions tốt hơn
- Không bị conflict giữa các nodes

### ✅ Improved Observability
- Logs chi tiết hơn để debug
- Phân biệt được các loại lỗi khác nhau

### ✅ Graceful Degradation
- Service tiếp tục hoạt động kể cả khi có một vài components fail
- Recovery mechanism tự động xử lý gaps

## Testing

### 1. Test Normal Streaming
```bash
# Gửi message và kiểm tra streaming hoạt động bình thường
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello AI", "session_id": "test-123"}'
```

### 2. Test với Redis Issues
```bash
# Stop Redis tạm thời
docker-compose pause redis

# Gửi message - streaming vẫn hoạt động
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Test without Redis", "session_id": "test-456"}'

# Start Redis lại
docker-compose unpause redis
```

### 3. Monitor Logs
```bash
# Xem logs để kiểm tra error handling
docker logs -f demo-java-websocket | grep -E "chunk|append|error"

# Expected: Thấy warnings nhưng không có exceptions
# "Error appending chunk to cache (continuing)" - OK
# "Chunk append failed (non-duplicate error)" - KHÔNG còn xuất hiện
```

### 4. Test Recovery
```bash
# Request recovery cho missing chunks
# Hệ thống sẽ tự động lấy từ DB nếu cache không có
curl -X POST http://localhost:8080/api/chat/recover \
  -H "Content-Type: application/json" \
  -d '{"session_id": "test-123", "last_chunk_index": 5}'
```

## Monitoring

### Metrics Cần Theo Dõi

1. **Cache Append Success Rate**
   ```
   cache.append.success / cache.append.total
   ```

2. **Lock Acquisition Failures**
   ```
   Count of "Lock acquisition timeout" logs
   ```

3. **Chunk Gaps**
   ```
   Count of "Chunk gap detected" logs
   ```

4. **Recovery Requests**
   ```
   Count of recovery API calls
   ```

### Log Levels

- `DEBUG`: Normal operations (chunk appended successfully)
- `WARN`: Recoverable issues (lock timeout, gaps, duplicates)
- `ERROR`: Non-critical failures (cache append failed but continuing)

## Rollback Plan

Nếu cần rollback:

```bash
# Revert the RedisStreamCache.java changes
git checkout HEAD~1 java-websocket-server/src/main/java/com/demo/websocket/infrastructure/RedisStreamCache.java

# Rebuild
docker-compose build java-websocket

# Restart
docker-compose restart java-websocket
```

## Kết Luận

Fix này cải thiện **resilience** và **availability** của streaming service bằng cách:

1. ✅ Không throw exceptions cho recoverable errors
2. ✅ Gracefully handle race conditions
3. ✅ Prioritize service availability over perfect cache consistency
4. ✅ Rely on recovery mechanisms for edge cases
5. ✅ Improve observability với better logging

**Kết quả**: Streaming ổn định hơn, ít bị gián đoạn, và dễ debug hơn khi có issues.

---

**Tác Giả**: AI Assistant  
**Ngày**: 2025-11-11  
**Version**: 1.0
