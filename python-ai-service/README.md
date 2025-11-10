# AI Chat Service

Production-ready AI streaming chat service với Redis PubSub integration.

## 📁 Project Structure

```
python-ai-service/
├── app.py              # FastAPI application entry point
├── config.py           # Configuration management
├── models.py           # Pydantic data models
├── redis_client.py     # Redis client wrapper
├── ai_service.py       # AI service & chat logic
├── requirements.txt    # Python dependencies
├── .env.example        # Environment variables example
├── Dockerfile          # Docker container config
└── README.md           # This file
```

## 🚀 Features

- ✅ **Modular Architecture**: Clean separation of concerns
- ✅ **Type Safety**: Full Pydantic models with validation
- ✅ **Error Handling**: Comprehensive error handling & logging
- ✅ **Configuration**: Environment-based configuration
- ✅ **Streaming Response**: Real-time AI response streaming via Redis PubSub
- ✅ **Chat History**: Persistent chat history with automatic expiration
- ✅ **Health Checks**: Endpoint for monitoring service health
- ✅ **Production Ready**: Proper logging, CORS, exception handling

## 🛠️ Installation

### Local Development

```bash
# Install dependencies
pip install -r requirements.txt

# Copy environment file
cp .env.example .env

# Edit configuration
nano .env

# Run service
python app.py
```

### Docker

```bash
# Build image
docker build -t ai-chat-service .

# Run container
docker run -p 8000:8000 \
  -e REDIS_HOST=redis \
  -e LOG_LEVEL=INFO \
  ai-chat-service
```

## 📚 API Documentation

### Endpoints

#### GET `/`
Root endpoint with service information

#### GET `/health`
Health check endpoint
- Returns: Service status and Redis connection status

#### POST `/chat`
Process chat message and stream AI response
- Body: `{ "session_id": "string", "message": "string", "user_id": "string" }`
- Returns: `{ "status": "streaming", "message_id": "string", "session_id": "string" }`

#### GET `/history/{session_id}`
Get chat history for a session
- Returns: `{ "session_id": "string", "messages": [...], "count": number }`

#### DELETE `/history/{session_id}`
Clear chat history for a session
- Returns: `{ "status": "success", "message": "..." }`

### Interactive API Docs

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## 🔧 Configuration

All configuration via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| HOST | 0.0.0.0 | Server host |
| PORT | 8000 | Server port |
| DEBUG | false | Debug mode |
| REDIS_HOST | redis | Redis host |
| REDIS_PORT | 6379 | Redis port |
| REDIS_PASSWORD | - | Redis password (optional) |
| CHAT_HISTORY_TTL | 86400 | History TTL in seconds (24h) |
| STREAM_DELAY | 0.1 | Delay between words (seconds) |
| CHUNK_DELAY | 0.05 | Delay between chunks (seconds) |
| LOG_LEVEL | INFO | Logging level |

## 📝 Code Structure

### `config.py`
- Environment-based configuration
- Settings validation
- Redis URL generation

### `models.py`
- Pydantic models for request/response
- Type validation
- Factory methods for message creation

### `redis_client.py`
- Redis connection management
- PubSub operations
- History management
- Error handling

### `ai_service.py`
- AI response generation (simulated)
- Streaming logic
- Chat orchestration

### `app.py`
- FastAPI application
- Route handlers
- Middleware setup
- Global error handling

## 🧪 Testing

```bash
# Health check
curl http://localhost:8000/health

# Send chat message
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id": "test123", "message": "Hello!", "user_id": "user1"}'

# Get history
curl http://localhost:8000/history/test123
```

## 📊 Logging

Structured logging với levels:
- INFO: Normal operations
- WARNING: Potential issues
- ERROR: Error conditions
- DEBUG: Detailed debugging (enable with LOG_LEVEL=DEBUG)

## 🔒 Security Notes

- Configure CORS allowed origins in production
- Use Redis password in production
- Enable HTTPS/TLS
- Implement rate limiting
- Add authentication/authorization

## 🚀 Production Deployment

1. Set appropriate environment variables
2. Configure CORS origins
3. Enable Redis authentication
4. Setup reverse proxy (nginx)
5. Configure logging aggregation
6. Setup monitoring & alerts

## 📄 License

MIT License
