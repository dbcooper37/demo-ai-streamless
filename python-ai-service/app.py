import asyncio
import json
import time
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import redis
import uuid

app = FastAPI()

# Redis connection
redis_client = redis.Redis(
    host='redis',
    port=6379,
    decode_responses=True
)

class ChatRequest(BaseModel):
    session_id: str
    message: str
    user_id: str = "default_user"

class AIResponse:
    """Simulates AI streaming response"""

    SAMPLE_RESPONSES = [
        "Xin chào! Tôi là AI assistant. Tôi có thể giúp gì cho bạn hôm nay?",
        "Đây là một ví dụ về streaming response. Mỗi chunk sẽ được gửi qua Redis PubSub.",
        "Khi bạn reload trang, bạn sẽ thấy toàn bộ lịch sử chat cũ và tiếp tục nhận streaming mới.",
        "Hệ thống này sử dụng Redis để lưu trữ lịch sử và PubSub để streaming real-time.",
    ]

    @staticmethod
    async def generate_streaming_response(text: str):
        """Simulate AI generating response word by word"""
        words = text.split()
        for i, word in enumerate(words):
            chunk = word + (" " if i < len(words) - 1 else "")
            await asyncio.sleep(0.1)  # Simulate AI processing delay
            yield chunk

@app.get("/health")
async def health():
    """Health check endpoint"""
    try:
        redis_client.ping()
        return {"status": "healthy", "redis": "connected"}
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Redis connection failed: {str(e)}")

@app.post("/chat")
async def chat(request: ChatRequest):
    """
    Process chat message and stream AI response via Redis PubSub
    """
    message_id = str(uuid.uuid4())

    # Store user message
    user_message = {
        "message_id": str(uuid.uuid4()),
        "session_id": request.session_id,
        "user_id": request.user_id,
        "role": "user",
        "content": request.message,
        "timestamp": int(time.time() * 1000),
        "is_complete": True
    }

    # Save user message to history
    redis_client.rpush(
        f"chat:history:{request.session_id}",
        json.dumps(user_message)
    )

    # Set expiration (24 hours)
    redis_client.expire(f"chat:history:{request.session_id}", 86400)

    # Publish user message immediately
    redis_client.publish(
        f"chat:stream:{request.session_id}",
        json.dumps(user_message)
    )

    # Select response based on message content
    response_text = AIResponse.SAMPLE_RESPONSES[0]
    if "streaming" in request.message.lower():
        response_text = AIResponse.SAMPLE_RESPONSES[1]
    elif "reload" in request.message.lower():
        response_text = AIResponse.SAMPLE_RESPONSES[2]
    elif "redis" in request.message.lower():
        response_text = AIResponse.SAMPLE_RESPONSES[3]

    # Start streaming AI response asynchronously
    asyncio.create_task(
        stream_ai_response(request.session_id, request.user_id, message_id, response_text)
    )

    return {
        "status": "streaming",
        "message_id": message_id,
        "session_id": request.session_id
    }

async def stream_ai_response(session_id: str, user_id: str, message_id: str, response_text: str):
    """Stream AI response chunk by chunk via Redis PubSub"""

    accumulated_content = ""

    async for chunk in AIResponse.generate_streaming_response(response_text):
        accumulated_content += chunk

        # Create streaming message
        stream_message = {
            "message_id": message_id,
            "session_id": session_id,
            "user_id": user_id,
            "role": "assistant",
            "content": accumulated_content,
            "chunk": chunk,
            "timestamp": int(time.time() * 1000),
            "is_complete": False
        }

        # Publish to Redis PubSub for real-time streaming
        redis_client.publish(
            f"chat:stream:{session_id}",
            json.dumps(stream_message)
        )

        await asyncio.sleep(0.05)

    # Send final complete message
    final_message = {
        "message_id": message_id,
        "session_id": session_id,
        "user_id": user_id,
        "role": "assistant",
        "content": accumulated_content,
        "timestamp": int(time.time() * 1000),
        "is_complete": True
    }

    # Publish final message
    redis_client.publish(
        f"chat:stream:{session_id}",
        json.dumps(final_message)
    )

    # Save complete message to history
    redis_client.rpush(
        f"chat:history:{session_id}",
        json.dumps(final_message)
    )

    # Set expiration (24 hours)
    redis_client.expire(f"chat:history:{session_id}", 86400)

@app.get("/history/{session_id}")
async def get_history(session_id: str):
    """Get chat history for a session"""
    try:
        history = redis_client.lrange(f"chat:history:{session_id}", 0, -1)
        messages = [json.loads(msg) for msg in history]
        return {
            "session_id": session_id,
            "messages": messages,
            "count": len(messages)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
