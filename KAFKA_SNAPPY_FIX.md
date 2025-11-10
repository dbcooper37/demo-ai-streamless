# Kafka Snappy Compression Error Fix

## Vấn đề (Problem)

```
org.apache.kafka.common.KafkaException: java.lang.NoClassDefFoundError: Could not initialize class org.xerial.snappy.Snappy
Caused by: java.lang.UnsatisfiedLinkError: Error loading shared library ld-linux-x86-64.so.2: No such file or directory
```

### Nguyên nhân (Root Cause)

1. **Docker base image**: `eclipse-temurin:17-jre-alpine` sử dụng Alpine Linux
2. **Alpine Linux** sử dụng `musl libc` thay vì `glibc`
3. **Snappy compression** yêu cầu native libraries dựa trên `glibc`
4. → Không tương thích giữa Alpine và Snappy native libraries

## ✅ Giải pháp 1: Đổi Compression Type (ĐÃ ÁP DỤNG)

**Ưu điểm:**
- ✅ Đơn giản nhất, chỉ cần sửa 1 dòng
- ✅ Không cần rebuild Docker image
- ✅ Vẫn có compression (gzip)
- ✅ Tương thích với Alpine Linux

**File đã sửa:** `/workspace/java-websocket-server/src/main/resources/application.yml`

```yaml
spring:
  kafka:
    producer:
      compression-type: gzip  # Changed from snappy to gzip
```

### So sánh Compression Types:

| Type   | Compression Ratio | Speed    | CPU Usage | Alpine Support |
|--------|------------------|----------|-----------|----------------|
| none   | 0%               | Fastest  | Lowest    | ✅ Yes         |
| gzip   | ~50-60%          | Medium   | Medium    | ✅ Yes         |
| snappy | ~40-50%          | Fast     | Low       | ❌ No (Alpine) |
| lz4    | ~40-50%          | Fastest  | Low       | ✅ Yes         |
| zstd   | ~60-70%          | Medium   | Medium    | ✅ Yes         |

**Recommended:** `gzip` (good balance) or `lz4` (fastest)

## 🔄 Giải pháp 2: Đổi Base Image sang Debian

**Ưu điểm:**
- ✅ Hỗ trợ đầy đủ native libraries (glibc)
- ✅ Có thể dùng snappy compression
- ✅ Ít vấn đề compatibility hơn

**Nhược điểm:**
- ❌ Docker image lớn hơn (~50-100MB)
- ❌ Cần rebuild Docker image

**File mới:** `/workspace/java-websocket-server/Dockerfile.debian`

```dockerfile
FROM eclipse-temurin:17-jre-jammy  # Debian-based instead of alpine
```

### Cách sử dụng:

**Option A: Thay thế Dockerfile hiện tại**
```bash
cd /workspace/java-websocket-server
mv Dockerfile Dockerfile.alpine.backup
mv Dockerfile.debian Dockerfile
```

**Option B: Sử dụng Dockerfile.debian trực tiếp**
```bash
docker build -f Dockerfile.debian -t java-websocket-server:debian .
```

**Option C: Cập nhật docker-compose.yml**
```yaml
java-websocket-server:
  build:
    context: ./java-websocket-server
    dockerfile: Dockerfile.debian  # Specify debian version
```

## 🔧 Giải pháp 3: Install glibc trong Alpine (Không khuyến nghị)

**Nhược điểm:**
- ❌ Phức tạp, dễ gây conflict
- ❌ Tăng kích thước image
- ❌ Có thể gây lỗi khác

```dockerfile
# Trong Dockerfile (NOT RECOMMENDED)
FROM eclipse-temurin:17-jre-alpine

# Install glibc compatibility
RUN apk --no-cache add ca-certificates wget && \
    wget -q -O /etc/apk/keys/sgerrand.rsa.pub https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub && \
    wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.35-r1/glibc-2.35-r1.apk && \
    apk add --force-overwrite glibc-2.35-r1.apk
```

## ✅ Khuyến nghị (Recommendation)

### Cho môi trường Production:
**Sử dụng Giải pháp 1 (gzip compression)** vì:
- Đơn giản, ổn định
- Alpine image nhỏ gọn (~150MB vs ~250MB)
- gzip compression đủ tốt (50-60% compression)
- Không có dependency issues

### Nếu cần Snappy:
**Sử dụng Giải pháp 2 (Debian image)** nếu:
- Cần performance tối đa của Snappy
- Không quan tâm image size
- Có nhiều native dependencies khác

## 🚀 Deployment Instructions

### Với Giải pháp 1 (hiện tại):
```bash
# Chỉ cần restart service
docker compose restart java-websocket-server

# Hoặc rebuild nếu cần
docker compose up -d --build java-websocket-server
```

### Với Giải pháp 2:
```bash
# Rebuild với Debian base image
cd /workspace
docker compose down java-websocket-server
docker compose build --no-cache java-websocket-server
docker compose up -d java-websocket-server
```

## 📊 Image Size Comparison

| Base Image                       | Size     | glibc | Snappy |
|----------------------------------|----------|-------|--------|
| eclipse-temurin:17-jre-alpine    | ~150 MB  | ❌    | ❌     |
| eclipse-temurin:17-jre-jammy     | ~250 MB  | ✅    | ✅     |
| eclipse-temurin:17-jre (debian)  | ~280 MB  | ✅    | ✅     |

## 🧪 Testing

### Test Kafka Producer:
```bash
# Check logs for Kafka errors
docker compose logs java-websocket-server | grep -i kafka

# Should see no more Snappy errors
docker compose logs java-websocket-server | grep -i snappy

# Test sending messages
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id": "test", "message": "Hello", "user_id": "test"}'
```

### Verify Compression:
```bash
# Connect to Kafka container
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic chat-events \
  --from-beginning

# Check producer config in logs
docker compose logs java-websocket-server | grep "compression"
```

## 🔍 Related Files

- ✅ Fixed: `java-websocket-server/src/main/resources/application.yml`
- 📝 Created: `java-websocket-server/Dockerfile.debian`
- 📄 Original: `java-websocket-server/Dockerfile` (Alpine-based)

## 📝 Notes

- Kafka is **OPTIONAL** in this application (`KAFKA_ENABLED:false` by default)
- The app works fine without Kafka using only Redis PubSub
- If Kafka is disabled, this error won't affect functionality
- EventPublisher is marked as `@Autowired(required=false)`

## 🎯 Status

- ✅ **Giải pháp 1 ĐÃ ÁP DỤNG**: Changed compression from `snappy` to `gzip`
- ⚙️ **Giải pháp 2 SẴN SÀNG**: Debian Dockerfile created as alternative
- ✅ **Testing**: Ready for deployment

## Tóm tắt (Summary)

| Item | Status |
|------|--------|
| Root cause identified | ✅ Done |
| Fix applied | ✅ Done |
| Alternative provided | ✅ Done |
| Documentation | ✅ Done |
| Ready to deploy | ✅ Yes |
