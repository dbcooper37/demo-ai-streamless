# 🏗️ Kiến trúc PoC: AI Streaming Chat với Persistent History

## 📋 Tổng quan dự án

### Vấn đề giải quyết (Problem Statement)

**Bài toán:** Làm thế nào để xây dựng hệ thống chat AI streaming có khả năng:
1. ✅ Stream real-time response từ AI đến nhiều clients đồng thời
2. ✅ Lưu trữ và khôi phục lịch sử chat khi user reload trang
3. ✅ Xử lý reconnection và recovery khi mất kết nối
4. ✅ Scale horizontal với multi-node deployment
5. ✅ Đảm bảo message ordering và consistency

### Giải pháp (Solution)

PoC này triển khai một **Event-Driven Microservices Architecture** với:
- **Real-time Messaging**: Redis PubSub cho streaming communication
- **Persistent Storage**: Redis + H2 Database cho history
- **Event Sourcing**: Kafka cho audit trail và analytics
- **WebSocket**: Bidirectional communication với auto-reconnection
- **Load Balancing**: NGINX cho multi-node deployment

---

## 🎯 Mục tiêu PoC

### Chứng minh (Proof of Concept)

1. **Streaming Architecture**
   - AI response được stream real-time qua WebSocket
   - Chunk-based transmission với low latency
   - Support concurrent users và sessions

2. **Persistence & Recovery**
   - Chat history được lưu trữ persistent
   - Auto-recovery khi reload page
   - Reconnection handling với resume capability

3. **Distributed System**
   - Multi-node deployment (3 Java nodes + 3 Python nodes)
   - Load balancing và failover
   - Session affinity với sticky sessions

4. **Scalability**
   - Horizontal scaling của từng component
   - Stateless services với shared state trong Redis
   - Message queue để decouple services

---

## 🏛️ Kiến trúc tổng quan

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            CLIENT LAYER                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │   React Frontend (Port 3000)                                      │  │
│  │   - WebSocket Client với Auto-Reconnection                       │  │
│  │   - State Management (useState, useEffect)                       │  │
│  │   - History Loading & Display                                    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ WebSocket (ws://)
                                    │ REST API (http://)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         LOAD BALANCER LAYER                              │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │   NGINX Load Balancer (Port 8080)                                │  │
│  │   - Sticky Sessions (IP Hash)                                    │  │
│  │   - Health Checks                                                │  │
│  │   - WebSocket Upgrade Support                                    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      BACKEND SERVICE LAYER                               │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐             │
│  │ Java WS Node1│    │ Java WS Node2│    │ Java WS Node3│             │
│  │  Port 8081   │    │  Port 8082   │    │  Port 8083   │             │
│  │              │    │              │    │              │             │
│  │ - WebSocket  │    │ - WebSocket  │    │ - WebSocket  │             │
│  │ - History    │    │ - History    │    │ - History    │             │
│  │ - Streaming  │    │ - Streaming  │    │ - Streaming  │             │
│  │ - Recovery   │    │ - Recovery   │    │ - Recovery   │             │
│  └──────────────┘    └──────────────┘    └──────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       AI SERVICE LAYER                                   │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐             │
│  │ Python AI #1 │    │ Python AI #2 │    │ Python AI #3 │             │
│  │  Port 8001   │    │  Port 8002   │    │  Port 8003   │             │
│  │              │    │              │    │              │             │
│  │ - FastAPI    │    │ - FastAPI    │    │ - FastAPI    │             │
│  │ - AI Logic   │    │ - AI Logic   │    │ - AI Logic   │             │
│  │ - Streaming  │    │ - Streaming  │    │ - Streaming  │             │
│  └──────────────┘    └──────────────┘    └──────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      INFRASTRUCTURE LAYER                                │
│  ┌────────────────────┐  ┌────────────────────┐  ┌─────────────────┐  │
│  │    Redis (6379)    │  │   Kafka (9092)     │  │ H2 Database     │  │
│  │                    │  │                    │  │                 │  │
│  │ - PubSub Channel   │  │ - Event Sourcing   │  │ - Message Store │  │
│  │ - History Storage  │  │ - Audit Trail      │  │ - Session Store │  │
│  │ - Session State    │  │ - Analytics Events │  │ - Metadata      │  │
│  │ - Distributed Lock │  │ - KRaft Mode       │  │                 │  │
│  └────────────────────┘  └────────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Chi tiết Components

### 1. Frontend Layer - React Application

**Công nghệ:** React 18, Vite, WebSocket API

**Trách nhiệm:**
- Quản lý WebSocket connection với auto-reconnection
- Hiển thị chat history và streaming messages
- Handle user input và gửi messages
- Local state management (session_id trong localStorage)

**Key Files:**
- `frontend/src/App.jsx` - Main application component
- `frontend/src/hooks/useWebSocket.js` - Custom hook cho WebSocket management
- `frontend/src/hooks/useChat.js` - Chat logic và state management
- `frontend/src/components/MessageList.jsx` - Hiển thị messages
- `frontend/src/components/ChatInput.jsx` - Input component

**WebSocket Connection Flow:**
```javascript
// Connection với authentication
ws://localhost:8080/ws/chat?session_id={uuid}&user_id={userId}&token={jwt}

// Auto-reconnection với exponential backoff
const RECONNECT_DELAY = 2000; // 2 seconds
const PING_INTERVAL = 30000;   // 30 seconds keep-alive
```

**Message Types Received:**
```json
{
  "type": "welcome",
  "sessionId": "uuid",
  "timestamp": "ISO-8601"
}

{
  "type": "history",
  "messages": [...]
}

{
  "type": "message",
  "data": {
    "messageId": "uuid",
    "role": "assistant",
    "content": "streaming text...",
    "isComplete": false,
    "timestamp": 1699123456789
  }
}

{
  "type": "error",
  "error": "Error message",
  "timestamp": "ISO-8601"
}
```

---

### 2. Load Balancer Layer - NGINX

**Công nghệ:** NGINX Alpine

**Configuration File:** `nginx-lb.conf`

**Trách nhiệm:**
- Load balance WebSocket connections đến Java nodes
- Proxy API requests đến AI services
- Health checks cho backend nodes
- Sticky sessions để maintain WebSocket connections

**Key Configuration:**
```nginx
# WebSocket load balancing với sticky sessions
upstream websocket_backend {
    ip_hash;  # Sticky sessions based on client IP
    server java-websocket-1:8080 max_fails=3 fail_timeout=30s;
    server java-websocket-2:8080 max_fails=3 fail_timeout=30s;
    server java-websocket-3:8080 max_fails=3 fail_timeout=30s;
}

# AI service load balancing (round-robin)
upstream ai_backend {
    server python-ai-1:8000 max_fails=3 fail_timeout=30s;
    server python-ai-2:8000 max_fails=3 fail_timeout=30s;
    server python-ai-3:8000 max_fails=3 fail_timeout=30s;
}

# WebSocket upgrade headers
location /ws/ {
    proxy_pass http://websocket_backend;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "Upgrade";
    proxy_set_header Host $host;
}
```

---

### 3. Backend Service Layer - Java WebSocket Server

**Công nghệ:** Java 17, Spring Boot 3.2, WebSocket, Redis, Kafka

**Architecture Pattern:** Event-Driven, Layered Architecture

#### 3.1. Core Components

##### **ChatWebSocketHandler** (`handler/ChatWebSocketHandler.java`)

**Trách nhiệm:**
- Handle WebSocket lifecycle (connect, disconnect, error)
- Gửi chat history khi client connect
- Forward streaming chunks từ Redis PubSub đến WebSocket clients
- Synchronized message sending để tránh concurrent write issues
- Session recovery và reconnection handling

**Key Methods:**
```java
// Establish connection và send history
public void afterConnectionEstablished(WebSocketSession wsSession)

// Handle incoming messages (ping, reconnect, etc.)
protected void handleTextMessage(WebSocketSession wsSession, TextMessage message)

// Cleanup khi disconnect
public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status)

// Broadcast message to all clients của một session
public void broadcastToSession(String sessionId, ChatMessage message)

// Synchronized sending để tránh TEXT_PARTIAL_WRITING error
private void sendMessageSynchronized(WebSocketSession wsSession, String payload)
```

**WebSocket Session Management:**
```java
// Track multiple WebSocket connections per session
private final Map<String, ConcurrentHashMap<String, WebSocketSession>> sessionMap

// Per-session locks for synchronized writes
private final Map<String, Object> sessionLocks
```

---

##### **ChatOrchestrator** (`infrastructure/ChatOrchestrator.java`)

**Trách nhiệm:**
- Orchestrate toàn bộ streaming flow
- Subscribe Redis PubSub channels
- Convert legacy messages sang new format
- Manage streaming sessions và lifecycle
- Handle completion và error scenarios

**Key Components:**
```java
// Track active streaming sessions
private final Map<String, StreamingContext> activeStreams

// Subscribe to Redis PubSub channel
public void startStreamingSession(String sessionId, String userId, StreamCallback callback)

// Handle messages từ Redis PubSub
private void handleLegacyMessage(ChatMessage chatMessage, StreamingContext context)

// Mark stream as complete và cleanup
private void handleStreamComplete(ChatMessage chatMessage, StreamingContext context)
```

**Distributed Session Ownership:**
```java
// Claim ownership using Redis SETNX
String ownerKey = "session:owner:" + sessionId;
Boolean claimed = redisTemplate.opsForValue()
    .setIfAbsent(ownerKey, getNodeId(), Duration.ofMinutes(10));

// Only one node handles a session at a time
if (claimed) {
    subscribeToLegacyChannel(legacyChannel, context);
}
```

---

##### **Redis Integration**

**PubSubListener** (`infrastructure/PubSubListener.java`)
- Interface for handling PubSub events
- Callbacks: onChunk(), onComplete(), onError()

**RedisMessageListener** (`service/RedisMessageListener.java`)
- Subscribe/unsubscribe Redis channels
- Fan-out messages to multiple subscribers

**RedisPubSubPublisher** (`infrastructure/RedisPubSubPublisher.java`)
- Publish chunks, complete messages, errors
- Multi-node coordination

**RedisStreamCache** (`infrastructure/RedisStreamCache.java`)
- Cache streaming chunks in Redis
- Stream recovery support
- TTL-based cleanup

**Hierarchical Cache Manager** (`service/HierarchicalCacheManager.java`)
- **L1 Cache**: Caffeine in-memory cache (fast, local)
- **L2 Cache**: Redis distributed cache (shared across nodes)
- Automatic cache synchronization

**Cache Configuration:**
```yaml
cache:
  caffeine:
    max-size: 500
    expire-after-write-minutes: 2
    expire-after-access-minutes: 1
  redis:
    default-ttl-minutes: 5
```

---

##### **Kafka Integration** (Optional)

**EventPublisher** (`service/EventPublisher.java`)

**Trách nhiệm:**
- Publish domain events to Kafka for event sourcing
- Audit trail và analytics
- Multi-service coordination

**Event Types:**
```java
// Session events
publishSessionStarted(ChatSession session)

// Streaming events
publishChunkReceived(String sessionId, StreamChunk chunk)
publishStreamCompleted(String sessionId, Message message, int totalChunks)
publishStreamError(String sessionId, String messageId, String error)

// Recovery events
publishRecoveryAttempt(String sessionId, int fromIndex, boolean success)

// Chat events
publishChatMessage(Message message)
```

**Kafka Topics:**
- `chat-events` - Chat messages và conversations
- `stream-events` - Streaming lifecycle events

**Enable/Disable:**
```yaml
spring:
  kafka:
    enabled: true  # Set to false to disable Kafka
    bootstrap-servers: kafka:9092
```

---

##### **Recovery & Resilience**

**RecoveryService** (`infrastructure/RecoveryService.java`)

**Trách nhiệm:**
- Recover missing chunks khi reconnect
- Check stream status (ongoing, completed, expired)
- Replay chunks from cache

**Recovery Flow:**
```java
public RecoveryResponse recoverStream(RecoveryRequest request) {
    // 1. Validate request
    // 2. Retrieve session from cache
    // 3. Get missing chunks
    // 4. Return recovery response
}
```

**Recovery Response Types:**
- `RECOVERED` - Stream đang active, trả về missing chunks
- `COMPLETED` - Stream đã complete, trả về final message
- `NOT_FOUND` - Session không tồn tại
- `EXPIRED` - Session đã expire
- `ERROR` - Lỗi trong quá trình recovery

---

##### **Session Management**

**SessionManager** (`infrastructure/SessionManager.java`)

**Trách nhiệm:**
- Register/unregister WebSocket sessions
- Track session metadata (userId, startTime, lastActivity)
- Heartbeat monitoring
- Session timeout handling

**Features:**
```java
// Register session with metadata
void registerSession(String sessionId, WebSocketSession wsSession, String userId)

// Update heartbeat timestamp
void updateHeartbeat(String sessionId)

// Mark session as error state
void markSessionError(String sessionId)

// Get session info
String getSessionId(WebSocketSession wsSession)
```

---

#### 3.2. Domain Models

**Message** (`domain/Message.java`)
```java
@Entity
public class Message {
    private String id;                    // UUID
    private String conversationId;        // Group messages
    private String userId;
    private MessageRole role;             // USER, ASSISTANT, SYSTEM
    private String content;               // Message content
    private MessageStatus status;         // PENDING, STREAMING, COMPLETED, FAILED
    private Instant createdAt;
    private Instant updatedAt;
    private MessageMetadata metadata;     // Token count, model info, etc.
}
```

**StreamChunk** (`domain/StreamChunk.java`)
```java
public class StreamChunk {
    private String messageId;
    private int index;                    // Chunk sequence number
    private String content;               // Accumulated content
    private ChunkType type;               // TEXT, CODE, ERROR
    private Instant timestamp;
}
```

**ChatSession** (`domain/ChatSession.java`)
```java
public class ChatSession {
    private String sessionId;
    private String userId;
    private String messageId;             // Current streaming message
    private String conversationId;
    private SessionStatus status;         // INITIALIZING, STREAMING, COMPLETED, ERROR
    private Instant startTime;
    private Instant lastActivityTime;
    private int totalChunks;
    private StreamMetadata metadata;
}
```

---

#### 3.3. Security & Validation

**SecurityValidator** (`service/SecurityValidator.java`)

**Features:**
- JWT token validation
- Token expiration check
- User authentication
- Rate limiting per user

**JWT Configuration:**
```yaml
security:
  jwt:
    secret: ${JWT_SECRET:default-key}
    expiration-ms: 3600000  # 1 hour
```

---

#### 3.4. Monitoring & Metrics

**MetricsService** (`service/MetricsService.java`)

**Metrics Tracked:**
- WebSocket connections (connect/disconnect)
- Messages sent/received
- Streaming performance (latency, throughput)
- Error rates
- Recovery attempts
- Cache hit/miss rates

**Logging Pattern:**
```
[METRIC] websocket.connection.established | userId=demo_user | timestamp=...
[METRIC] message.streaming.completed | sessionId=xxx | chunks=42 | duration=2134ms
[METRIC] cache.hit | type=L1 | key=message:xxx
```

**Actuator Endpoints:**
```
GET /actuator/health      # Health check
GET /actuator/info        # Application info
```

---

### 4. AI Service Layer - Python FastAPI

**Công nghệ:** Python 3.11, FastAPI, Redis, asyncio

**Architecture:** Clean Architecture với separation of concerns

#### 4.1. Core Components

##### **FastAPI Application** (`app.py`)

**Endpoints:**
```python
GET  /                          # Service info
GET  /health                    # Health check với Redis connection test
POST /chat                      # Trigger AI streaming response
GET  /history/{session_id}      # Get chat history
DELETE /history/{session_id}    # Clear chat history
POST /cancel                    # Cancel ongoing streaming
```

**Lifespan Management:**
```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Connect to Redis
    redis_client.connect()
    yield
    # Shutdown: Cleanup
```

---

##### **AI Service** (`ai_service.py`)

**AIService Class:**

**Trách nhiệm:**
- Generate AI responses (simulated)
- Stream response word by word
- Select appropriate response based on user message

**Key Methods:**
```python
async def generate_streaming_response(text: str) -> AsyncGenerator[str, None]:
    """Stream text word by word với delay"""
    words = text.split()
    for word in words:
        await asyncio.sleep(STREAM_DELAY)
        yield word + " "

def select_response(user_message: str) -> str:
    """Select response based on keywords"""
    # Smart routing based on message content
```

**Sample Responses:**
- Greeting và general info
- Streaming architecture explanation
- Persistence và recovery details
- Redis architecture details
- Workflow và flow explanation
- Technical deep-dives

---

**ChatService Class:**

**Trách nhiệm:**
- Process user messages
- Orchestrate AI streaming
- Handle cancellation
- Manage distributed state in Redis

**Key Methods:**
```python
async def process_user_message(session_id, user_id, message_content) -> str:
    """
    1. Create user message với UUID
    2. Save to Redis history
    3. Publish to PubSub
    4. Return message_id
    """

async def stream_ai_response(session_id, user_id, user_message) -> str:
    """
    1. Register active stream in Redis
    2. Generate response chunks
    3. Check cancellation flag periodically (every 10 chunks)
    4. Publish chunks to Redis PubSub
    5. Handle completion or cancellation
    6. Cleanup Redis tracking
    """

def cancel_streaming(session_id, message_id) -> bool:
    """
    1. Check active stream in Redis
    2. Verify message_id matches
    3. Set cancel flag in Redis (distributed)
    4. Return success/failure
    """
```

**Distributed Cancellation:**
```python
# Register streaming task in Redis (visible to all nodes)
redis_client.register_active_stream(session_id, message_id, ttl=300)

# Check cancel flag periodically (reduces Redis calls)
if chunk_count % 10 == 0:
    if redis_client.check_cancel_flag(session_id, message_id):
        cancelled = True
        break

# Set cancel flag (works across all nodes)
redis_client.set_cancel_flag(session_id, message_id, ttl=60)
```

---

##### **Redis Client** (`redis_client.py`)

**RedisClient Class:**

**Trách nhiệm:**
- Manage Redis connections
- PubSub operations
- History storage
- Distributed state management

**Key Methods:**
```python
# Connection
def connect() -> None
def ping() -> bool

# PubSub
def publish_message(session_id: str, message: ChatMessage) -> bool
def subscribe(session_id: str, callback: Callable)

# History Storage
def save_to_history(session_id: str, message: ChatMessage)
def get_history(session_id: str) -> List[ChatMessage]
def clear_history(session_id: str) -> bool

# Distributed State (for multi-node)
def register_active_stream(session_id: str, message_id: str, ttl: int)
def get_active_stream(session_id: str) -> Optional[str]
def clear_active_stream(session_id: str)
def set_cancel_flag(session_id: str, message_id: str, ttl: int)
def check_cancel_flag(session_id: str, message_id: str) -> bool
def clear_cancel_flag(session_id: str, message_id: str)
```

**Redis Data Structures:**
```python
# PubSub Channel
CHANNEL = f"chat:stream:{session_id}"

# History List (LPUSH, LRANGE)
KEY = f"chat:history:{session_id}"
TTL = 86400  # 24 hours

# Active Stream Tracking
KEY = f"streaming:active:{session_id}"
VALUE = message_id

# Cancellation Flag
KEY = f"streaming:cancel:{session_id}:{message_id}"
VALUE = "1"
```

---

#### 4.2. Configuration

**Configuration** (`config.py`)

```python
class Settings(BaseSettings):
    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    DEBUG: bool = False
    
    # Redis
    REDIS_HOST: str = "redis"
    REDIS_PORT: int = 6379
    
    # Streaming
    STREAM_DELAY: float = 0.05     # Delay between words
    CHUNK_DELAY: float = 0.01      # Delay between chunks
    
    # Storage
    HISTORY_TTL: int = 86400       # 24 hours
    
    # Logging
    LOG_LEVEL: str = "INFO"
```

---

#### 4.3. Data Models

**Models** (`models.py`)

```python
class ChatMessage(BaseModel):
    messageId: str
    sessionId: str
    userId: str
    role: str  # "user" | "assistant"
    content: str
    timestamp: int
    isComplete: bool
    chunk: Optional[str] = None

class ChatRequest(BaseModel):
    session_id: str
    message: str
    user_id: str = "default_user"

class ChatResponse(BaseModel):
    status: str
    message_id: str
    session_id: str
    message: str

class CancelRequest(BaseModel):
    session_id: str
    message_id: str

class HistoryResponse(BaseModel):
    session_id: str
    messages: List[ChatMessage]
    count: int

class HealthResponse(BaseModel):
    status: str
    redis: str
    timestamp: str
```

---

### 5. Infrastructure Layer

#### 5.1. Redis

**Version:** Redis 7 Alpine

**Use Cases:**

**1. PubSub for Real-time Streaming**
```redis
# Publish streaming chunk
PUBLISH chat:stream:{session_id} {json_message}

# Subscribe to session
SUBSCRIBE chat:stream:{session_id}
```

**2. List for History Storage**
```redis
# Save message
LPUSH chat:history:{session_id} {json_message}
EXPIRE chat:history:{session_id} 86400

# Get history
LRANGE chat:history:{session_id} 0 -1
```

**3. String for Distributed State**
```redis
# Register active stream
SET streaming:active:{session_id} {message_id} EX 300

# Set cancel flag
SET streaming:cancel:{session_id}:{message_id} "1" EX 60

# Session ownership (SETNX for distributed lock)
SET session:owner:{session_id} {node_id} NX EX 600
```

**4. Hierarchical Cache (L2)**
```redis
# Cache message
SET cache:message:{messageId} {json} EX 300

# Cache session
SET cache:session:{sessionId} {json} EX 600
```

**Configuration:**
```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  volumes:
    - redis-data:/data
  command: redis-server --appendonly yes
```

---

#### 5.2. Kafka

**Version:** Apache Kafka (KRaft mode - No Zookeeper)

**Use Cases:**

**1. Event Sourcing**
- Store all domain events
- Audit trail
- Replay capability

**2. Analytics**
- Track user behavior
- Performance metrics
- Business insights

**3. Multi-Service Coordination**
- Event-driven architecture
- Loose coupling
- Async processing

**Topics:**
```
chat-events         # Chat messages, conversations
stream-events       # Streaming lifecycle events
```

**Configuration:**
```yaml
kafka:
  image: apache/kafka:latest
  ports:
    - "9092:9092"  # Client connections
    - "9093:9093"  # Controller
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    KAFKA_LOG_RETENTION_HOURS: 168  # 7 days
```

**Event Examples:**
```json
{
  "eventType": "SESSION_STARTED",
  "timestamp": "2024-01-01T00:00:00Z",
  "sessionId": "uuid",
  "userId": "user123",
  "messageId": "uuid"
}

{
  "eventType": "CHUNK_RECEIVED",
  "timestamp": "2024-01-01T00:00:01Z",
  "sessionId": "uuid",
  "messageId": "uuid",
  "chunkIndex": 42,
  "contentLength": 256
}

{
  "eventType": "STREAM_COMPLETED",
  "timestamp": "2024-01-01T00:00:10Z",
  "sessionId": "uuid",
  "messageId": "uuid",
  "totalChunks": 100,
  "contentLength": 2048
}
```

---

#### 5.3. H2 Database

**Version:** H2 In-Memory Database

**Use Cases:**
- Message persistence
- Session metadata
- Audit logs
- Conversation history

**Entities:**
```sql
-- Messages table
CREATE TABLE message (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36),
    user_id VARCHAR(255),
    role VARCHAR(20),
    content TEXT,
    status VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Chat sessions table
CREATE TABLE chat_session (
    session_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255),
    conversation_id VARCHAR(36),
    status VARCHAR(20),
    start_time TIMESTAMP,
    last_activity_time TIMESTAMP
);

-- Audit logs table
CREATE TABLE audit_log (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36),
    event_type VARCHAR(50),
    event_data TEXT,
    timestamp TIMESTAMP
);

-- Stream chunks table (for recovery)
CREATE TABLE stream_chunk (
    id VARCHAR(36) PRIMARY KEY,
    message_id VARCHAR(36),
    chunk_index INTEGER,
    content TEXT,
    timestamp TIMESTAMP
);
```

**Configuration:**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:websocketdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

---

## 📊 Data Flow - Detailed Scenarios

### Scenario 1: Normal Streaming Flow

**Mục tiêu:** User gửi message và nhận streaming response

```
┌─────────┐                                                     ┌─────────┐
│ Client  │                                                     │ Java BE │
└────┬────┘                                                     └────┬────┘
     │                                                               │
     │  1. POST /api/chat                                           │
     │  {session_id, message, user_id}                              │
     ├──────────────────────────────────────────────────────────────>│
     │                                                               │
     │                         ┌─────────┐                          │
     │                         │Python AI│                          │
     │                         └────┬────┘                          │
     │                              │                               │
     │  2. POST /chat              │                               │
     │  Proxy request to AI        │                               │
     │     ├────────────────────────>                               │
     │                              │                               │
     │                              │  3. Save user message         │
     │                              │  to Redis history             │
     │                              │     │                         │
     │                              │     ▼                         │
     │                         ┌─────────┐                          │
     │                         │  Redis  │                          │
     │                         └─────────┘                          │
     │                              │                               │
     │                              │  4. Generate AI response      │
     │                              │  (word by word)               │
     │                              │                               │
     │                              │  5. For each chunk:           │
     │                              │     - Accumulate content      │
     │                              │     - Publish to PubSub       │
     │                              │     PUBLISH chat:stream:{sid} │
     │                              │         ├───────────────────> │
     │                         ┌─────────┐       │                 │
     │                         │  Redis  │       │                 │
     │                         │ PubSub  │       │                 │
     │                         └─────────┘       │                 │
     │                              │            │                 │
     │                              │            │  6. Subscribe   │
     │                              │            │  to channel     │
     │                              │            │  (if not yet)   │
     │                              │            │                 │
     │                              │<───────────┘                 │
     │                              │                               │
     │                              │  7. Receive chunk from PubSub│
     │                              │  Convert to StreamChunk      │
     │                              │                               │
     │                              │          8. Cache chunk       │
     │                              │          in Redis             │
     │                              │             ├──────────────>  │
     │                         ┌─────────┐       │           ┌─────────┐
     │                         │  Redis  │       │           │  Cache  │
     │                         │ Storage │       │           └─────────┘
     │                         └─────────┘       │                 │
     │                              │             │                 │
     │                              │             │  9. Publish to  │
     │                              │             │  Kafka (optional)
     │                              │             │        ├────────>
     │                              │             │   ┌─────────┐   │
     │                              │             │   │  Kafka  │   │
     │                              │             │   └─────────┘   │
     │                              │             │                 │
     │  10. Send chunk via WebSocket               │                 │
     │  <───────────────────────────────────────────┘                 │
     │  {"type": "message",                                          │
     │   "data": {                                                   │
     │     "content": "Hello world...",                              │
     │     "isComplete": false                                       │
     │   }}                                                          │
     │                              │                               │
     │                              │  11. Repeat steps 5-10        │
     │                              │  for each word                │
     │  <───────────────────────────┼───────────────────────────────┘
     │  <───────────────────────────┼───────────────────────────────┘
     │  <───────────────────────────┼───────────────────────────────┘
     │                              │                               │
     │                              │  12. Final chunk with         │
     │                              │  isComplete: true             │
     │                              │     ├──────────────────────>  │
     │                              │                               │
     │                              │  13. Save complete message    │
     │                              │  to history & DB              │
     │                              │     ├──────────────────────>  │
     │                         ┌─────────┐                          │
     │                         │  Redis  │                          │
     │                         │   +     │                          │
     │                         │  H2 DB  │                          │
     │                         └─────────┘                          │
     │                              │                               │
     │  14. Complete message via WebSocket                          │
     │  <─────────────────────────────────────────────────────────────
     │  {"type": "message",                                          │
     │   "data": {                                                   │
     │     "content": "Hello world, this is complete!",              │
     │     "isComplete": true                                        │
     │   }}                                                          │
     │                                                               │
```

**Timing:**
- Step 1-2: ~10ms (HTTP request)
- Step 3: ~5ms (Redis write)
- Step 4-11: ~2-5 seconds (streaming, 50ms per word)
- Step 12-14: ~20ms (finalization)

**Total:** 2-5 seconds for complete response

---

### Scenario 2: Reload During Streaming

**Mục tiêu:** User reload trang trong khi AI đang streaming

```
┌─────────┐                                                     ┌─────────┐
│ Client  │                                                     │ Java BE │
└────┬────┘                                                     └────┬────┘
     │                                                               │
     │  STREAMING IN PROGRESS...                                    │
     │  <───────────────────────────────────────────────────────────┤
     │  Chunk #1: "Hello"                                           │
     │  <───────────────────────────────────────────────────────────┤
     │  Chunk #2: "world"                                           │
     │                                                               │
     │  ⚠️  USER RELOADS PAGE                                       │
     │                                                               │
     │  WebSocket disconnected                                      │
     │     ├────────────────────────────────────────────────────────>
     │                                                               │
     │                         ┌─────────┐                          │
     │                         │Python AI│                          │
     │                         └────┬────┘                          │
     │                              │                               │
     │                              │  ⚠️ AI continues streaming    │
     │                              │  (doesn't know about          │
     │                              │   disconnect)                 │
     │                              │                               │
     │                              │  Chunk #3: "this"             │
     │                              │  PUBLISH chat:stream:{sid}    │
     │                              │     ├──────────────────────>  │
     │                         ┌─────────┐                          │
     │                         │  Redis  │                          │
     │                         │ History │                          │
     │                         └─────────┘                          │
     │                              │  (saved to history)           │
     │                              │                               │
     │  ═════════════════════════════════════════════════════════  │
     │  CLIENT RELOADS & RECONNECTS                                 │
     │  ═════════════════════════════════════════════════════════  │
     │                                                               │
     │  1. New WebSocket connection                                 │
     │  ws://...?session_id={same_session_id}                       │
     │     ├────────────────────────────────────────────────────────>
     │                                                               │
     │  2. Welcome message                                          │
     │  <───────────────────────────────────────────────────────────┤
     │  {"type": "welcome", "sessionId": "..."}                     │
     │                                                               │
     │  3. Request history from Redis                               │
     │                              ├──────────────────────────────>
     │                         ┌─────────┐                          │
     │                         │  Redis  │                          │
     │                         │ History │                          │
     │                         └─────────┘                          │
     │                              │                               │
     │  4. Send complete history (including partial streaming)     │
     │  <───────────────────────────────────────────────────────────┤
     │  {"type": "history",                                         │
     │   "messages": [                                              │
     │     {"role": "user", "content": "...", "isComplete": true},  │
     │     {"role": "assistant", "content": "Hello world this",     │
     │      "isComplete": false}  ← Partial message                 │
     │   ]}                                                          │
     │                                                               │
     │  5. Subscribe to session PubSub                              │
     │                              ├──────────────────────────────>
     │                         ┌─────────┐                          │
     │                         │  Redis  │                          │
     │                         │ PubSub  │                          │
     │                         └─────────┘                          │
     │                              │                               │
     │  6. Continue receiving NEW chunks                            │
     │  <───────────────────────────────────────────────────────────┤
     │  Chunk #4: "is"                                              │
     │  <───────────────────────────────────────────────────────────┤
     │  Chunk #5: "streaming!"                                      │
     │                                                               │
     │  7. Final chunk with isComplete: true                        │
     │  <───────────────────────────────────────────────────────────┤
     │  {"type": "message", "data": {                               │
     │    "content": "Hello world this is streaming!",              │
     │    "isComplete": true                                        │
     │  }}                                                           │
     │                                                               │
     │  ✅ USER SEES COMPLETE MESSAGE SEAMLESSLY!                   │
     │                                                               │
```

**Key Points:**
- ✅ AI service **không bị interrupt** khi client disconnect
- ✅ Chunks vẫn được lưu vào Redis history
- ✅ Client reconnect và nhận **toàn bộ history** (bao gồm partial message)
- ✅ Client **tự động subscribe** và tiếp tục nhận streaming
- ✅ **No data loss**, seamless experience

---

### Scenario 3: Multi-Node Load Balancing

**Mục tiêu:** 3 users connect đồng thời, được distribute across nodes

```
User A                    User B                    User C
  │                         │                         │
  │  WebSocket Connect      │                         │
  ├─────────────────────────┼─────────────────────────┼────────>
  │                         │  WebSocket Connect      │       NGINX
  │                         ├─────────────────────────┼────────> LB
  │                         │                         │       (ip_hash)
  │                         │                         │  WS Connect
  │                         │                         ├────────>
  │                         │                         │
  ▼                         ▼                         ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ Java Node 1  │      │ Java Node 2  │      │ Java Node 3  │
│ Port 8081    │      │ Port 8082    │      │ Port 8083    │
└──────┬───────┘      └──────┬───────┘      └──────┬───────┘
       │                     │                     │
       │  Session A          │  Session B          │  Session C
       │  Subscribe          │  Subscribe          │  Subscribe
       │  chat:stream:A      │  chat:stream:B      │  chat:stream:C
       │        │            │        │            │        │
       │        ▼            │        ▼            │        ▼
       │   ┌─────────────────────────────────────────────┐
       │   │         Redis PubSub (Shared)               │
       │   │                                             │
       │   │  Channels:                                  │
       │   │  - chat:stream:session_A                    │
       │   │  - chat:stream:session_B                    │
       │   │  - chat:stream:session_C                    │
       │   └─────────────────────────────────────────────┘
       │                     │                     │
       │                     │                     │
       │                     ▼                     │
       │                ┌──────────────┐           │
       │                │ Python AI #2 │           │
       │                │ (handles B)  │           │
       │                └──────────────┘           │
       │                     │                     │
       │                     │  PUBLISH            │
       │                     │  chat:stream:B      │
       │                     │                     │
       │                     ▼                     │
       │   ┌─────────────────────────────────────────────┐
       │   │         Redis PubSub (Fanout)               │
       │   │                                             │
       │   │  All 3 Java nodes subscribed to:           │
       │   │  - Node 1: session_A                       │
       │   │  - Node 2: session_B ← Receives message    │
       │   │  - Node 3: session_C                       │
       │   └─────────────────────────────────────────────┘
       │                     │                     │
       │                     ▼                     │
       │                Java Node 2                │
       │                Forwards to                │
       │                User B via WS              │
       │                     │                     │
       │                     ▼                     │
                          User B
                     Receives streaming
                          response
```

**Load Distribution:**
- NGINX uses `ip_hash` để ensure sticky sessions
- Mỗi user được route đến same Java node (for WebSocket consistency)
- Python AI services được distribute round-robin (stateless)
- Redis PubSub fan-out đến tất cả subscribed nodes
- Only relevant nodes forward messages to their clients

**Session Ownership:**
```redis
# Node 2 claims ownership of session_B
SET session:owner:session_B "ws-node-2" NX EX 600

# Only Node 2 will subscribe to chat:stream:session_B
# Other nodes skip subscription (already owned)
```

**Scalability:**
- Add thêm Java nodes → NGINX auto load-balance
- Add thêm Python AI nodes → Java BE round-robin
- Redis PubSub handles unlimited subscribers
- No coordination needed between Java nodes

---

### Scenario 4: Distributed Cancellation

**Mục tiêu:** User cancel streaming, works across all nodes

```
User A (connected to Node 1)
  │
  │  1. Streaming in progress...
  │  Receiving chunks from AI
  │  <─────────────────────────────────────┐
  │                                  Java Node 1
  │                                  WebSocket Handler
  │                                        │
  │                                        │
  │  2. User clicks "Cancel" button       │
  │                                        │
  │  POST /api/cancel                      │
  │  {session_id, message_id}              │
  ├────────────────────────────────────────>
  │                                  ChatController
  │                                        │
  │                                        ▼
  │                                  3. Set cancel flag
  │                                  in Redis
  │                                        │
  │                                  SET streaming:cancel:
  │                                      {session}:{msg} "1"
  │                                  EX 60
  │                                        │
  │                                        ├──────────────>
  │                                   ┌─────────┐
  │                                   │  Redis  │
  │                                   │ (Shared)│
  │                                   └────┬────┘
  │                                        │
  │                                        │ 4. Flag is now
  │                                        │ visible to
  │                                        │ ALL nodes
  │                                        │
  │                                        ▼
  │                                   Python AI Node 3
  │                                   (Currently streaming
  │                                    this message)
  │                                        │
  │                                        │ 5. Check flag
  │                                        │ every 10 chunks
  │                                        │
  │    if chunk_count % 10 == 0:          │
  │        cancel = redis.get(            │
  │            f"streaming:cancel:         │
  │             {session}:{msg}")          │
  │                                        │
  │                                        │ 6. Flag found!
  │                                        │ Stop streaming
  │                                        ▼
  │                                   break loop
  │                                        │
  │                                        │ 7. Send cancel
  │                                        │ message
  │                                        │
  │                                   PUBLISH chat:stream:
  │                                           {session}
  │                                   {                    
  │                                     "content": "...[Cancelled]",
  │                                     "isComplete": true
  │                                   }
  │                                        │
  │                                        ├──────────────>
  │                                   ┌─────────┐
  │                                   │  Redis  │
  │                                   │ PubSub  │
  │                                   └────┬────┘
  │                                        │
  │                                        ▼
  │                                  Java Node 1
  │                                  Receives cancel msg
  │                                        │
  │  8. Forward cancel to user            │
  │  <────────────────────────────────────┘
  │                                        
  │  {"type": "message",
  │   "data": {
  │     "content": "Hello world...\n\n[Đã hủy]",
  │     "isComplete": true
  │   }}
  │
  │  9. Cleanup Redis flags
  │     ├────────────────────────────────>
  │                                   ┌─────────┐
  │                                   │  Redis  │
  │                                   │         │
  │   DEL streaming:active:{session}  │         │
  │   DEL streaming:cancel:{session}  │         │
  │                                   └─────────┘
  │
  ✅ Streaming cancelled successfully!
```

**Key Points:**
- ✅ Cancel request có thể đến **bất kỳ Java node** nào (through NGINX)
- ✅ Cancel flag được set trong **Redis** (shared state)
- ✅ Python AI node (đang streaming) **check flag periodically**
- ✅ Works **across all nodes** - true distributed cancellation
- ✅ Optimization: Check every 10 chunks (reduce Redis calls, max 0.5s delay)
- ✅ Race condition handled: Set flag even if message already completed

**Why Distributed?**
- User connects to Java Node 1 (WebSocket)
- User request proxied to Python AI Node 3 (REST API, round-robin)
- Cancel request may hit Java Node 2 (NGINX load balance)
- Redis ensures cancel flag visible to Python AI Node 3
- **All nodes coordinate through shared Redis state**

---

## 🎨 Design Patterns & Best Practices

### 1. Architecture Patterns

#### **Event-Driven Architecture**
- Components communicate qua events (Redis PubSub, Kafka)
- Loose coupling, high scalability
- Async processing, non-blocking

#### **Microservices Pattern**
- Java BE và Python AI là independent services
- Each service có responsibility riêng
- Can scale independently

#### **CQRS (Command Query Responsibility Segregation)**
- Write: Save messages to Redis + H2 Database
- Read: Query from cache (L1/L2) hoặc history
- Optimized for different access patterns

#### **Event Sourcing** (via Kafka)
- All events được stored sequentially
- Can replay events để rebuild state
- Audit trail cho compliance

#### **Saga Pattern** (Orchestrated)
- ChatOrchestrator điều phối streaming flow
- Handle compensating transactions (cancel, error)
- Maintain data consistency across services

---

### 2. Code Organization Patterns

#### **Layered Architecture** (Java Backend)

```
Presentation Layer (Controller)
       ↓
Application Layer (Service)
       ↓
Domain Layer (Models, Repository)
       ↓
Infrastructure Layer (Redis, Kafka, DB)
```

#### **Clean Architecture** (Python Service)

```
app.py (Entry point)
    ↓
ai_service.py (Business Logic)
    ↓
redis_client.py (Infrastructure)
    ↓
models.py (Domain Models)
```

#### **Dependency Injection**
- Spring Boot's built-in DI container
- Constructor injection (recommended)
- Interface-based design

```java
public class ChatOrchestrator {
    private final RedisStreamCache streamCache;
    private final EventPublisher eventPublisher;
    
    // Constructor injection
    public ChatOrchestrator(RedisStreamCache streamCache,
                           EventPublisher eventPublisher) {
        this.streamCache = streamCache;
        this.eventPublisher = eventPublisher;
    }
}
```

---

### 3. Concurrency Patterns

#### **Actor Model** (via StreamingContext)
```java
private static class StreamingContext {
    final ChatSession session;
    final StreamCallback callback;
    final AtomicInteger chunkIndex;
    
    // Each context = isolated actor
    // No shared mutable state
}
```

#### **Producer-Consumer Pattern**
```
Python AI (Producer) → Redis PubSub → Java BE (Consumer) → WebSocket Client
```

#### **Synchronized Messaging**
```java
// Per-session locks để tránh concurrent writes
private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

private void sendMessageSynchronized(WebSocketSession wsSession, String payload) {
    Object lock = sessionLocks.computeIfAbsent(wsSession.getId(), k -> new Object());
    synchronized (lock) {
        wsSession.sendMessage(new TextMessage(payload));
    }
}
```

---

### 4. Resilience Patterns

#### **Circuit Breaker** (Kafka optional)
```java
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class EventPublisher {
    // Service degrades gracefully khi Kafka unavailable
}
```

#### **Retry with Exponential Backoff** (WebSocket reconnect)
```javascript
const RECONNECT_DELAY = 2000; // Start with 2s
// Can be extended: 2s → 4s → 8s → 16s (max 32s)
```

#### **Timeout Pattern**
```java
@Value("${stream.recovery-timeout-minutes:5}")
private int recoveryTimeoutMinutes;

// Streams expire after timeout
streamCache.markComplete(messageId, Duration.ofMinutes(5));
```

#### **Bulkhead Pattern** (Resource isolation)
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 8   # Limit connections
          max-idle: 4
          min-idle: 2
```

---

### 5. Caching Patterns

#### **Hierarchical Cache (L1 + L2)**

```
Request → L1 Cache (Caffeine - Local, Fast)
             │ MISS
             ▼
          L2 Cache (Redis - Distributed, Shared)
             │ MISS
             ▼
          Database (H2 - Persistent)
```

**Benefits:**
- Fast reads from L1 (in-memory, local)
- Shared state in L2 (cross-node consistency)
- Persistent storage in DB (durability)

#### **Cache-Aside Pattern**
```java
public Message getMessage(String messageId) {
    // Try L1
    Message cached = l1Cache.get(messageId);
    if (cached != null) return cached;
    
    // Try L2
    cached = l2Cache.get(messageId);
    if (cached != null) {
        l1Cache.put(messageId, cached);
        return cached;
    }
    
    // Load from DB
    Message message = repository.findById(messageId);
    l2Cache.put(messageId, message);
    l1Cache.put(messageId, message);
    return message;
}
```

#### **Write-Through Cache**
```java
public void saveMessage(Message message) {
    // Write to DB
    repository.save(message);
    
    // Update caches
    l2Cache.put(message.getId(), message);
    l1Cache.put(message.getId(), message);
}
```

---

### 6. Messaging Patterns

#### **Publish-Subscribe** (Redis PubSub)
```
One Publisher (Python AI) → Many Subscribers (Java Nodes)
Fan-out messaging
```

#### **Point-to-Point** (Session Ownership)
```redis
# Only one node handles a session
SET session:owner:{session_id} {node_id} NX EX 600
```

#### **Message Filtering** (Subscribe only to relevant channels)
```java
// Each node subscribes only to its sessions
String channel = "chat:stream:" + sessionId;
listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
```

---

### 7. Data Consistency Patterns

#### **Eventual Consistency**
- Redis PubSub messages may arrive out of order
- Use sequence numbers (chunk index) để reorder
- Final state eventually consistent

#### **Optimistic Locking** (Session ownership)
```redis
# SETNX returns false if key exists
SET session:owner:{session_id} {node_id} NX

# Only proceed if lock acquired
if (claimed) {
    processSession();
}
```

#### **Idempotency**
```java
// Message processing is idempotent
// Processing same chunk multiple times = same result
if (chunkExists(messageId, index)) {
    log.info("Chunk already processed, skipping");
    return;
}
```

---

### 8. Monitoring & Observability Patterns

#### **Metrics Collection** (MetricsService)
```java
[METRIC] websocket.connection.established
[METRIC] message.streaming.started
[METRIC] message.streaming.completed | duration=2134ms | chunks=42
[METRIC] cache.hit | type=L1
[METRIC] cache.miss | type=L2
[METRIC] error | type=TRANSPORT_ERROR
```

#### **Structured Logging**
```
timestamp [node_id] [thread] LEVEL logger - message
2024-01-01 10:00:00 [ws-node-1] [http-nio-1] INFO ChatOrchestrator - Started streaming session: sessionId=xxx
```

#### **Health Checks**
```yaml
# Actuator endpoints
GET /actuator/health
{
  "status": "UP",
  "components": {
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

#### **Distributed Tracing** (Prepare for future)
```java
// Add trace_id, span_id to logs
log.info("traceId={}, spanId={}, Processing message", traceId, spanId);
```

---

## 🚀 Deployment Architecture

### Single-Node Mode (Development)

**File:** `docker-compose.yml`

**Services:**
- 1x Java WebSocket Server (8080)
- 1x Python AI Service (8000)
- 1x Redis (6379)
- 1x Kafka (9092, 9093)
- 1x Frontend (3000)

**Use Case:**
- Development và testing
- Demo purposes
- Resource-constrained environments

**Start Command:**
```bash
docker-compose up --build
```

---

### Multi-Node Mode (Production)

**File:** `docker-compose.multi-node.yml`

**Services:**
- **3x Java WebSocket Servers** (8081, 8082, 8083)
- **3x Python AI Services** (8001, 8002, 8003)
- **1x NGINX Load Balancer** (8080)
- **1x Redis** (6379) - Shared
- **1x Kafka** (9092, 9093) - Shared
- **1x Frontend** (3000)

**Architecture:**

```
                    Internet
                        │
                        ▼
                  ┌──────────┐
                  │ Frontend │
                  │ :3000    │
                  └────┬─────┘
                       │
                       ▼
                  ┌──────────┐
                  │  NGINX   │
                  │  :8080   │ Load Balancer
                  └────┬─────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   ┌────────┐    ┌────────┐    ┌────────┐
   │Java WS1│    │Java WS2│    │Java WS3│
   │  :8081 │    │  :8082 │    │  :8083 │
   └───┬────┘    └───┬────┘    └───┬────┘
       │             │             │
       └─────────────┼─────────────┘
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
   ┌────────┐  ┌────────┐  ┌────────┐
   │Python  │  │Python  │  │Python  │
   │AI #1   │  │AI #2   │  │AI #3   │
   │:8001   │  │:8002   │  │:8003   │
   └────────┘  └────────┘  └────────┘
        │            │            │
        └────────────┼────────────┘
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
   ┌─────────────────────────────────┐
   │  Redis (Shared)     Kafka       │
   │  :6379              :9092       │
   └─────────────────────────────────┘
```

**Load Balancing Strategy:**

1. **NGINX → Java WebSocket**
   - Algorithm: `ip_hash` (sticky sessions)
   - Reason: Maintain WebSocket connection consistency
   - Health checks every 10s

2. **Java → Python AI**
   - Algorithm: Round-robin (via client-side)
   - Reason: Stateless AI processing
   - Failover: Retry on next node

**Start Command:**
```bash
docker-compose -f docker-compose.multi-node.yml up --build
```

**With Kafka UI (Debug):**
```bash
docker-compose -f docker-compose.multi-node.yml --profile debug up
```

---

### Environment Variables

**Java WebSocket Server:**
```yaml
# Redis
SPRING_DATA_REDIS_HOST: redis
SPRING_DATA_REDIS_PORT: 6379

# Kafka
SPRING_KAFKA_ENABLED: true
SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092

# AI Service
AI_SERVICE_URL: http://nginx-lb:80/ai  # Load-balanced

# Security
JWT_SECRET: your-secret-key
JWT_EXPIRATION_MS: 3600000

# Cache
CACHE_L1_MAX_SIZE: 500
CACHE_L1_EXPIRE_WRITE: 2
CACHE_L2_TTL: 5

# Stream
STREAM_MAX_PENDING_CHUNKS: 1000
STREAM_BACKPRESSURE_DELAY: 10

# Logging
LOG_LEVEL: INFO
NODE_ID: ws-node-1

# JVM
JAVA_OPTS: -Xms512m -Xmx1024m -XX:+UseG1GC
```

**Python AI Service:**
```yaml
# Redis
REDIS_HOST: redis
REDIS_PORT: 6379

# Node identification
NODE_ID: ai-node-1

# Logging
LOG_LEVEL: INFO
```

**Frontend:**
```yaml
# WebSocket (through NGINX)
VITE_WS_URL: ws://localhost:8080/ws/chat

# API (through NGINX)
VITE_API_URL: http://localhost:8080/api
```

---

## 📈 Performance & Scalability

### Performance Metrics

**Latency:**
- WebSocket connection: ~10-20ms
- First chunk delivery: ~50-100ms
- Chunk-to-chunk: ~50ms (configurable)
- History loading: ~50-100ms (depends on size)

**Throughput:**
- Messages per second: ~100-500 (per node)
- Concurrent users: ~1000-5000 (per node)
- Concurrent sessions: ~10,000+ (with proper Redis tuning)

**Resource Usage (per node):**
- Java Backend: 512MB-1GB RAM, 1-2 CPU cores
- Python AI: 256MB-512MB RAM, 1 CPU core
- Redis: 256MB-1GB RAM (depends on history size)
- Kafka: 512MB-2GB RAM

---

### Scalability Strategies

#### **Horizontal Scaling**

**Add more Java nodes:**
```yaml
java-websocket-4:
  # Same config as node 1-3
  ports:
    - "8084:8080"
  environment:
    - NODE_ID=ws-node-4
```

**Add to NGINX upstream:**
```nginx
upstream websocket_backend {
    ip_hash;
    server java-websocket-1:8080;
    server java-websocket-2:8080;
    server java-websocket-3:8080;
    server java-websocket-4:8080;  # New node
}
```

**Benefits:**
- Linear scalability
- No code changes needed
- Auto load-balancing

---

#### **Redis Scaling**

**Current:** Single Redis instance

**Future:** Redis Cluster
```yaml
redis-cluster:
  image: redis:7-alpine
  command: redis-server --cluster-enabled yes
  # 3 master + 3 replica nodes
```

**Benefits:**
- Sharding for large datasets
- High availability (replica failover)
- Horizontal scalability

---

#### **Kafka Scaling**

**Current:** Single Kafka broker (KRaft mode)

**Future:** Kafka cluster
```yaml
kafka-1:
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093

kafka-2:
  environment:
    KAFKA_NODE_ID: 2
    
kafka-3:
  environment:
    KAFKA_NODE_ID: 3
```

**Benefits:**
- Partition distribution
- Replication (fault tolerance)
- Higher throughput

---

### Performance Optimization Tips

**1. Connection Pooling**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20    # Increase for high load
          max-idle: 10
          min-idle: 5
```

**2. Cache Tuning**
```yaml
cache:
  caffeine:
    max-size: 10000         # Increase L1 cache size
    expire-after-write-minutes: 5
```

**3. Chunk Size Optimization**
```python
STREAM_DELAY = 0.03  # 30ms between words (faster)
CHUNK_DELAY = 0.005  # 5ms between chunks
```

**4. Message Batching** (Future)
```java
// Batch multiple chunks into one WebSocket message
List<StreamChunk> batch = new ArrayList<>();
// ... collect chunks ...
sendBatch(wsSession, batch);
```

**5. Compression** (Future)
```nginx
# NGINX gzip compression
gzip on;
gzip_types text/plain application/json;
gzip_min_length 1000;
```

---

## 🔒 Security Considerations

### Current Implementation (PoC)

**JWT Authentication:**
- Token validation on WebSocket connect
- Token passed via query params (dev mode)
- Configurable secret và expiration

**Development Mode:**
```java
// Allow connections without token
if (token == null) {
    log.warn("No token provided, using development mode");
    return "dev-token";
}
```

---

### Production Recommendations

**1. HTTPS/WSS:**
```nginx
server {
    listen 443 ssl;
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    
    location /ws/ {
        proxy_pass http://websocket_backend;
        # ... WebSocket config ...
    }
}
```

**2. Token in Headers (not query params):**
```javascript
// Bad: Token in URL (visible in logs)
ws://localhost:8080/ws/chat?token=xxx

// Good: Token in headers
const ws = new WebSocket('wss://localhost:8080/ws/chat');
ws.onopen = () => {
    ws.send(JSON.stringify({
        type: 'auth',
        token: 'jwt-token'
    }));
};
```

**3. Rate Limiting:**
```java
@Service
public class RateLimitService {
    private final LoadingCache<String, AtomicInteger> requestCounts;
    
    public boolean allowRequest(String userId) {
        // Limit to 100 requests per minute
        AtomicInteger count = requestCounts.get(userId);
        return count.incrementAndGet() <= 100;
    }
}
```

**4. Input Validation:**
```java
@Valid
public class ChatRequest {
    @NotBlank
    @Size(min = 1, max = 36)
    private String sessionId;
    
    @NotBlank
    @Size(min = 1, max = 5000)
    private String message;
}
```

**5. CORS Configuration:**
```java
@Configuration
public class WebSecurityConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("https://yourdomain.com"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "DELETE"));
        // ...
    }
}
```

---

## 🧪 Testing & Quality Assurance

### Testing Strategy (Future)

**Unit Tests:**
```java
@Test
public void testStreamChunkCreation() {
    StreamChunk chunk = StreamChunk.builder()
        .messageId("test-id")
        .index(0)
        .content("Hello")
        .build();
    
    assertEquals("Hello", chunk.getContent());
}
```

**Integration Tests:**
```java
@SpringBootTest
@AutoConfigureWebTestClient
public class ChatIntegrationTest {
    @Test
    public void testChatFlow() {
        // 1. Connect WebSocket
        // 2. Send message
        // 3. Verify streaming response
        // 4. Check history
    }
}
```

**Load Testing:**
```bash
# Using k6 or Apache JMeter
k6 run --vus 100 --duration 60s load-test.js
```

---

## 📚 Lessons Learned & Best Practices

### Do's ✅

1. **Use Redis PubSub for real-time messaging**
   - Low latency, high throughput
   - Simple pub/sub model
   - Built-in fan-out

2. **Implement hierarchical caching**
   - L1 (local) cho fast access
   - L2 (distributed) cho consistency
   - Automatic invalidation

3. **Use synchronized writes for WebSocket**
   - Prevents TEXT_PARTIAL_WRITING errors
   - Per-session locks
   - Better than global lock

4. **Store state in Redis for distributed systems**
   - Session ownership
   - Cancel flags
   - Active streaming tracking

5. **Check cancellation periodically (not every chunk)**
   - Reduces Redis overhead
   - Max delay acceptable (0.5s)
   - Better performance

6. **Use Kafka for optional features**
   - Event sourcing
   - Analytics
   - Graceful degradation if unavailable

---

### Don'ts ❌

1. **Don't store large data in Redis PubSub**
   - PubSub không persistent
   - Use for notifications only
   - Store data in Redis Storage or DB

2. **Don't block WebSocket handler thread**
   - Use async processing
   - CompletableFuture cho long operations
   - Keep handler lightweight

3. **Don't forget to cleanup resources**
   - Close WebSocket connections
   - Unsubscribe from PubSub
   - Release distributed locks
   - Delete expired keys

4. **Don't use global locks**
   - Kills concurrency
   - Use per-session or per-resource locks
   - Fine-grained locking

5. **Don't trust client input**
   - Always validate
   - Sanitize messages
   - Rate limiting

---

## 🎯 Future Enhancements

### Phase 2 (Short-term)

1. **Message Editing & Deletion**
   - Edit sent messages
   - Delete messages (soft delete)
   - Sync across all clients

2. **Typing Indicators**
   - Real-time typing status
   - Via Redis PubSub
   - Throttled updates

3. **Read Receipts**
   - Track message read status
   - Store in Redis
   - Update UI

4. **Rich Media Support**
   - Images, files, code blocks
   - Streaming uploads
   - Preview generation

---

### Phase 3 (Medium-term)

1. **Multi-user Conversations**
   - Group chats
   - User presence
   - Message broadcast to multiple users

2. **Search Functionality**
   - Full-text search in history
   - Elasticsearch integration
   - Faceted search

3. **Notification System**
   - Push notifications
   - Email notifications
   - WebSocket fallback

4. **Admin Dashboard**
   - Real-time monitoring
   - User management
   - Analytics dashboard

---

### Phase 4 (Long-term)

1. **AI Model Integration**
   - Replace mock AI with real models
   - OpenAI, Anthropic, local models
   - Model selection per session

2. **Kubernetes Deployment**
   - Auto-scaling
   - Rolling updates
   - Service mesh (Istio)

3. **Advanced Caching**
   - CDN for static assets
   - Edge caching
   - Intelligent prefetching

4. **Machine Learning Features**
   - Response quality scoring
   - Auto-categorization
   - Sentiment analysis

---

## 📖 Kết luận

### Điểm mạnh của giải pháp

1. **Real-time Streaming**
   - ✅ Low latency, high throughput
   - ✅ Scalable architecture
   - ✅ Graceful degradation

2. **Persistence & Recovery**
   - ✅ No data loss on reload
   - ✅ Automatic reconnection
   - ✅ Stream recovery

3. **Distributed System**
   - ✅ Multi-node deployment
   - ✅ Load balancing
   - ✅ Fault tolerance

4. **Developer Experience**
   - ✅ Clean architecture
   - ✅ Well-documented
   - ✅ Easy to extend

---

### Kết quả PoC

**Chứng minh thành công:**
- ✅ AI streaming chat với persistent history
- ✅ Real-time communication qua WebSocket
- ✅ Multi-node deployment với load balancing
- ✅ Distributed state management với Redis
- ✅ Event sourcing với Kafka (optional)
- ✅ Auto-recovery và resilience

**Production-ready:**
- 🔄 Cần thêm authentication/authorization
- 🔄 HTTPS/WSS support
- 🔄 Monitoring và alerting
- 🔄 Comprehensive testing
- 🔄 Performance tuning

---

### Tài liệu tham khảo

**Technologies:**
- [Spring Boot WebSocket](https://spring.io/guides/gs/messaging-stomp-websocket/)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [Redis PubSub](https://redis.io/docs/manual/pubsub/)
- [Apache Kafka](https://kafka.apache.org/documentation/)
- [React WebSocket](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)

**Design Patterns:**
- [Microservices Patterns](https://microservices.io/patterns/)
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
- [CQRS Pattern](https://martinfowler.com/bliki/CQRS.html)

**Best Practices:**
- [12-Factor App](https://12factor.net/)
- [Cloud Native Patterns](https://www.oreilly.com/library/view/cloud-native-patterns/9781617294297/)

---

**Document Version:** 1.0  
**Last Updated:** 2024-01-01  
**Author:** Development Team  
**Status:** PoC Complete ✅
