# Frontend-Backend API Connection Fix

## Vấn đề (Problem)
Frontend không thể call được backend API khi chạy trong Docker containers.

## Nguyên nhân (Root Cause)
1. **Hardcoded URLs trong App.jsx**: 
   - WebSocket URL và API URL được hardcode thay vì sử dụng environment variables
   - Không thể override khi chạy trong Docker environment

2. **Environment Variables không được sử dụng**:
   - Docker Compose đã định nghĩa `VITE_WS_URL` và `VITE_API_URL` nhưng code không dùng chúng
   - Code luôn dùng giá trị hardcoded `ws://localhost:8080/ws/chat` và `/api`

## Giải pháp (Solution)

### 1. Cập nhật App.jsx
**File**: `frontend/src/App.jsx`

```javascript
// Trước (Before)
const WEBSOCKET_URL = 'ws://localhost:8080/ws/chat';
const AI_SERVICE_URL = '/api';

// Sau (After)
const WEBSOCKET_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws/chat';
const AI_SERVICE_URL = import.meta.env.VITE_API_URL || '/api';
```

### 2. Cập nhật Docker Compose
**File**: `docker-compose.yml`

```yaml
frontend:
  environment:
    - VITE_WS_URL=ws://localhost:8080/ws/chat
    - VITE_API_URL=/api  # Changed from http://localhost:8000
```

**Lý do**: 
- `/api` sẽ được proxy qua Vite dev server tới `python-ai:8000` (container network)
- Vite proxy config đã được setup trong `vite.config.js`

### 3. Thêm Environment Files
**Files created**:
- `frontend/.env` - Chứa giá trị mặc định
- `frontend/.env.example` - Template cho developers
- `frontend/.gitignore` - Ignore .env files

## Cách hoạt động (How It Works)

### Trong Docker Environment:
1. **WebSocket Connection**:
   - Browser connects tới `ws://localhost:8080/ws/chat`
   - Đây là host machine's port 8080
   - Port được map từ `java-websocket` container

2. **API Calls**:
   - Frontend gọi `/api` endpoint
   - Vite dev server proxy request tới `http://python-ai:8000` (container network)
   - Response được trả về cho browser

### Trong Local Development:
1. Tạo file `.env` trong `frontend/` directory:
   ```env
   VITE_WS_URL=ws://localhost:8080/ws/chat
   VITE_API_URL=/api
   ```

2. Run backend services:
   ```bash
   docker-compose up redis python-ai java-websocket
   ```

3. Run frontend locally:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## Kiểm tra (Verification)

### 1. Rebuild và restart containers:
```bash
docker-compose down
docker-compose build frontend
docker-compose up -d
```

### 2. Kiểm tra logs:
```bash
# Frontend logs
docker logs demo-frontend

# Backend logs
docker logs demo-python-ai
docker logs demo-java-websocket
```

### 3. Test connection:
1. Mở browser: http://localhost:3000
2. Mở DevTools > Console
3. Gửi message
4. Kiểm tra:
   - WebSocket connection status (green = connected)
   - API calls trong Network tab
   - Messages được hiển thị

## Network Architecture

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │
       ├─── WS: ws://localhost:8080/ws/chat
       │    (Host port mapped to java-websocket container)
       │
       └─── HTTP: http://localhost:3000/api
            (Vite proxy)
            
            ↓
            
┌──────────────────────────────────────┐
│         Docker Network               │
│                                      │
│  ┌──────────┐      ┌─────────────┐ │
│  │ Frontend │──────│  Python AI  │ │
│  │  :3000   │ proxy│    :8000    │ │
│  └──────────┘      └─────────────┘ │
│                                      │
│  ┌──────────────┐  ┌─────────────┐ │
│  │ Java WS      │──│   Redis     │ │
│  │   :8080      │  │   :6379     │ │
│  └──────────────┘  └─────────────┘ │
└──────────────────────────────────────┘
```

## Troubleshooting

### Issue: Frontend vẫn không connect được

**Giải pháp**:
1. Clear browser cache
2. Hard reload (Ctrl+Shift+R hoặc Cmd+Shift+R)
3. Kiểm tra tất cả containers đang chạy:
   ```bash
   docker-compose ps
   ```
4. Rebuild frontend container:
   ```bash
   docker-compose build frontend --no-cache
   docker-compose up -d frontend
   ```

### Issue: API calls fail với CORS error

**Giải pháp**:
- Vite proxy đã handle CORS
- Đảm bảo `VITE_API_URL=/api` (không phải full URL)
- Kiểm tra `vite.config.js` proxy configuration

### Issue: WebSocket connection fails

**Giải pháp**:
1. Kiểm tra java-websocket service:
   ```bash
   curl http://localhost:8080/health
   ```
2. Kiểm tra logs:
   ```bash
   docker logs demo-java-websocket -f
   ```
3. Đảm bảo port 8080 không bị service khác sử dụng:
   ```bash
   netstat -an | grep 8080
   ```

## Summary of Changes

**Files Modified**:
- ✅ `frontend/src/App.jsx` - Use environment variables
- ✅ `docker-compose.yml` - Update frontend environment
- ✅ `docker-compose.multi-node.yml` - Update multi-node frontend environment
- ✅ `frontend/vite.config.js` - Make proxy target configurable
- ✅ `frontend/README.md` - Update documentation
- ✅ `frontend/.env.example` - Add PYTHON_AI_TARGET

**Files Created**:
- ✅ `frontend/.env` - Default environment variables
- ✅ `frontend/.env.example` - Template file
- ✅ `frontend/.gitignore` - Ignore environment files
- ✅ `FRONTEND_BACKEND_FIX.md` - This documentation

## Next Steps

1. Test the application thoroughly
2. Update other environments (staging, production) với proper URLs
3. Document environment-specific configurations
4. Consider using Nginx for production instead of Vite dev server
