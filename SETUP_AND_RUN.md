# 🚀 Setup và Chạy Test Streaming

## 📊 Kết quả Test trong Remote Environment

Test đã được chạy nhưng **FAILED** vì:
- ❌ Docker không có trong remote environment
- ❌ Services (Redis, Python AI, Java WebSocket) không chạy
- ✅ Dependencies đã được cài đặt thành công
- ✅ Channel verification: Channels ĐÚNG và KHỚP NHAU!

## ✅ Điều này có nghĩa gì?

### **Tin tốt:**
1. ✅ **Code ĐÚNG** - Channels match: `chat:stream:{session_id}`
2. ✅ **Dependencies OK** - websockets và aiohttp đã được cài
3. ✅ **Test scripts ready** - Tất cả scripts đã sẵn sàng

### **Cần làm:**
⚠️  **Chạy test trên máy của BẠN** (nơi có Docker và services đang chạy)

## 🎯 Hướng dẫn Chạy trên Máy của Bạn

### **Bước 1: Đảm bảo Services đang chạy**

```bash
# Kiểm tra services
docker compose ps

# Nếu chưa chạy, start services
docker compose up -d

# Đợi ~10 giây để services khởi động đầy đủ
sleep 10

# Verify các services critical
docker compose ps redis python-ai-service java-websocket-server
```

Mong đợi thấy tất cả services **Up**:
```
NAME                    STATUS
redis                   Up
python-ai-service       Up
java-websocket-server   Up
frontend                Up
```

### **Bước 2: Cài đặt Dependencies (nếu chưa có)**

```bash
pip3 install websockets aiohttp
```

### **Bước 3: Chạy Full Test**

```bash
# Chạy test tự động đầy đủ
./run_full_test.sh
```

Script này sẽ:
1. ✅ Check prerequisites (Python, Docker, packages)
2. ✅ Verify services đang chạy
3. ✅ Test Redis connection
4. ✅ Verify channel names
5. ✅ Run automated WebSocket + HTTP streaming test
6. ✅ Analyze logs nếu fail
7. ✅ Báo cáo kết quả chi tiết

### **Bước 4: Xem Kết quả**

**Nếu TEST PASS:**
```
╔════════════════════════════════════════════════════════════════╗
║                    ✓ TEST PASSED                              ║
║           Streaming is working correctly!                     ║
╚════════════════════════════════════════════════════════════════╝
```
→ **Streaming hoạt động!** ✅

**Nếu TEST FAIL:**
Script sẽ hiển thị:
- ❌ Lỗi cụ thể
- 📊 Log analysis
- 💡 Recommendations
- 🔧 Fix commands

## 🧪 Alternative: Test Từng Bước

Nếu muốn test từng bước thay vì dùng script tổng:

### **Test 1: Check Services**
```bash
docker compose ps
docker compose logs redis --tail=5
docker compose logs python-ai-service --tail=5
docker compose logs java-websocket-server --tail=5
```

### **Test 2: Verify Channels**
```bash
# Check Python channel
grep "chat:stream:" python-ai-service/redis_client.py

# Check Java channel  
grep "chat:stream:" java-websocket-server/src/main/java/com/demo/websocket/infrastructure/ChatOrchestrator.java
```

Cả 2 phải đều là: `chat:stream:{session_id}` ✅

### **Test 3: Test Redis PubSub**
```bash
./diagnose_redis_pubsub.sh
```

### **Test 4: Automated Streaming Test**
```bash
python3 test_streaming_websocket.py
```

### **Test 5: Check Subscribers**
```bash
# Trong terminal khác, gọi curl với session ID bất kỳ
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test_manual","user_id":"demo_user","message":"test"}'

# Ngay lập tức check Python logs
docker compose logs python-ai-service | grep "subscribers"
```

**Mong đợi:** `subscribers=1` (hoặc hơn)
**Nếu:** `subscribers=0` → Java chưa subscribe (WebSocket chưa connect)

## 🎨 Test với Frontend UI (Dễ nhất!)

```bash
# Mở browser
http://localhost:3000

# Gửi bất kỳ message nào
# Ví dụ: "xin chào"

# Quan sát:
# - Message xuất hiện ngay lập tức ✅
# - AI response streaming từng từ một ✅
# - Streaming indicator (3 dots) hiển thị ✅
# - Complete message cuối cùng ✅
```

Nếu thấy streaming trong UI → **HỆ THỐNG HOẠT ĐỘNG!** ✅

## 📊 Monitoring Real-time

Trong khi test, mở 3 terminals để monitor:

**Terminal 1: Python logs**
```bash
docker compose logs -f python-ai-service | grep -E "(Starting|Published|subscribers|Completed)"
```

**Terminal 2: Java logs**
```bash
docker compose logs -f java-websocket-server | grep -E "(ChatOrchestrator|sendChunk|Subscribed)"
```

**Terminal 3: Redis monitor**
```bash
docker compose exec redis redis-cli
> PSUBSCRIBE chat:stream:*
```

Sau đó gửi message từ UI hoặc curl, bạn sẽ thấy messages flow qua cả 3 terminals!

## 🔍 Debug Checklist

Nếu test fail, check theo thứ tự:

### ✅ Step 1: Services Running?
```bash
docker compose ps
# Tất cả phải Up
```

### ✅ Step 2: Redis Working?
```bash
docker compose exec redis redis-cli ping
# Expect: PONG
```

### ✅ Step 3: Channels Correct?
```bash
grep "chat:stream:" python-ai-service/redis_client.py
grep "chat:stream:" java-websocket-server/src/main/java/com/demo/websocket/infrastructure/ChatOrchestrator.java
# Both should be: chat:stream:{session_id}
```

### ✅ Step 4: WebSocket Connecting?
```bash
docker compose logs java-websocket-server | grep "WebSocket connected"
# Should see connections
```

### ✅ Step 5: Java Subscribing?
```bash
docker compose logs java-websocket-server | grep "Subscribed to legacy channel"
# Should see subscriptions
```

### ✅ Step 6: Python Publishing?
```bash
docker compose logs python-ai-service | grep "Published to chat:stream"
# Should see publishes
```

### ✅ Step 7: Subscribers > 0?
```bash
docker compose logs python-ai-service | grep "subscribers"
# Should see subscribers=1 (or more)
```

## 🎯 Expected Success Output

Khi test thành công, bạn sẽ thấy:

**Python logs:**
```
Starting AI response streaming for session=test_session_xxx
Published to chat:stream:test_session_xxx: subscribers=1 ✅
Published to chat:stream:test_session_xxx: subscribers=1 ✅
...
Completed AI response streaming: chunks=15
```

**Java logs:**
```
WebSocket connected: sessionId=test_session_xxx
Subscribed to legacy channel: chat:stream:test_session_xxx with listener ✅
ChatOrchestrator received message from chat:stream:test_session_xxx ✅
Calling callback.onChunk ✅
Sending chunk to WebSocket ✅
```

**Test script output:**
```
✅ Connected to WebSocket
📨 Welcome message received
📨 User message: test
🔄 AI streaming started...
⏩ Chunk: Xin ...
⏩ Chunk: Xin chào! ...
✅ Streaming complete: Xin chào! Tôi là AI assistant...

╔════════════════════════════════════════════════════════════════╗
║                    ✓ TEST PASSED                              ║
║           Streaming is working correctly!                     ║
╚════════════════════════════════════════════════════════════════╝
```

## 🛠️ Troubleshooting Common Issues

### Issue 1: Connection Refused (port 8080 or 8000)

**Cause:** Services không chạy

**Fix:**
```bash
docker compose up -d
docker compose ps
```

### Issue 2: subscribers=0 trong Python logs

**Cause:** Java chưa subscribe vì WebSocket chưa connect

**Fix:**
1. Connect WebSocket client TRƯỚC
2. Đợi vài giây
3. Sau đó gửi HTTP request

Script `test_streaming_websocket.py` tự động handle timing này.

### Issue 3: No subscription logs trong Java

**Cause:** WebSocket chưa connect hoặc `startStreamingSession()` không được gọi

**Fix:**
```bash
# Check Java logs khi WebSocket connects
docker compose logs java-websocket-server | grep -A 5 "WebSocket connected"

# Should see "Subscribed to legacy channel" sau đó
```

### Issue 4: Services keep restarting

**Cause:** Configuration issues hoặc dependencies missing

**Fix:**
```bash
# Check logs
docker compose logs <service-name>

# Rebuild nếu cần
docker compose down
docker compose up --build -d
```

## 📦 Files Created for You

Tôi đã tạo các files sau để hỗ trợ:

### **Test Scripts:**
1. `run_full_test.sh` - Comprehensive automated test ⭐
2. `test_streaming_websocket.py` - WebSocket + HTTP test
3. `check_subscribers.py` - Check subscribers count
4. `diagnose_redis_pubsub.sh` - Redis diagnostic tool

### **Documentation:**
1. `CHANNELS_SUMMARY.md` - Quick overview
2. `CHANNEL_ARCHITECTURE_EXPLAINED.md` - Detailed architecture
3. `README_STREAMING_DIAGNOSIS.md` - Full diagnostic guide
4. `QUICK_TEST_STREAMING.md` - Quick test guide
5. `TEST_STREAMING_WITH_CURL.md` - Curl explanation
6. `SETUP_AND_RUN.md` - This file!

### **Helper Scripts:**
1. `test_streaming_simple.sh` - Manual test với wscat
2. `test_redis_pubsub.py` - Redis PubSub tester

## 🎓 Summary

**Những gì đã verify trong remote environment:**
- ✅ Code ĐÚNG - Channels match perfectly
- ✅ Dependencies installed
- ✅ Scripts ready to run

**Những gì cần làm trên máy của bạn:**
1. ✅ Start services: `docker compose up -d`
2. ✅ Run test: `./run_full_test.sh`
3. ✅ Or use UI: `http://localhost:3000`

**Quick Commands:**
```bash
# Full automated test
./run_full_test.sh

# Or simple UI test
open http://localhost:3000
# Send a message and watch streaming!
```

## 🆘 Need Help?

Nếu test vẫn fail sau khi chạy trên máy của bạn:

1. Copy output của `./run_full_test.sh`
2. Copy relevant logs:
   ```bash
   docker compose logs python-ai-service --tail=50 > python_logs.txt
   docker compose logs java-websocket-server --tail=50 > java_logs.txt
   ```
3. Share logs để debug chi tiết hơn

## 🎉 Expected Result

Khi mọi thứ hoạt động:
- ✅ Test script báo PASS
- ✅ Python logs có `subscribers=1`
- ✅ Java logs có subscription và receive messages
- ✅ Frontend UI streaming từng từ một
- ✅ No errors trong any logs

**Good luck! 🚀**
