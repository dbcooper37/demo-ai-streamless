"""
FastAPI application for AI Chat Service
Refactored with proper structure and error handling
"""
import asyncio
import logging
import sys
from contextlib import asynccontextmanager
from datetime import datetime

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from config import settings
from models import ChatRequest, ChatResponse, HistoryResponse, HealthResponse
from redis_client import redis_client
from ai_service import chat_service


# Configure logging
logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    # Startup
    logger.info("Starting AI Chat Service...")
    try:
        redis_client.connect()
        logger.info("Application started successfully")
    except Exception as e:
        logger.error(f"Failed to start application: {e}")
        raise
    
    yield
    
    # Shutdown
    logger.info("Shutting down AI Chat Service...")


# Initialize FastAPI app
app = FastAPI(
    title="AI Chat Service",
    description="AI streaming chat service with Redis PubSub integration",
    version="2.0.0",
    lifespan=lifespan
)

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify exact origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Global exception handler"""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "detail": "Internal server error",
            "message": str(exc) if settings.DEBUG else "An error occurred"
        }
    )


@app.get("/", tags=["Root"])
async def root():
    """Root endpoint"""
    return {
        "service": "AI Chat Service",
        "version": "2.0.0",
        "status": "running",
        "endpoints": {
            "health": "/health",
            "chat": "/chat",
            "history": "/history/{session_id}"
        }
    }


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    """
    Health check endpoint
    Verifies Redis connection
    """
    try:
        redis_connected = redis_client.ping()
        
        if not redis_connected:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Redis connection failed"
            )
        
        return HealthResponse(
            status="healthy",
            redis="connected",
            timestamp=datetime.now().isoformat()
        )
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Service unhealthy: {str(e)}"
        )


@app.post("/chat", response_model=ChatResponse, tags=["Chat"])
async def chat(request: ChatRequest):
    """
    Process chat message and stream AI response via Redis PubSub
    
    The AI response is streamed in real-time through Redis PubSub.
    Clients should connect to WebSocket to receive streaming chunks.
    """
    try:
        # Validate input
        if not request.message.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Message cannot be empty"
            )
        
        # Process user message
        user_message_id = await chat_service.process_user_message(
            session_id=request.session_id,
            user_id=request.user_id,
            message_content=request.message
        )
        
        # Start streaming AI response asynchronously (fire and forget)
        asyncio.create_task(
            chat_service.stream_ai_response(
                session_id=request.session_id,
                user_id=request.user_id,
                user_message=request.message
            )
        )
        
        return ChatResponse(
            status="streaming",
            message_id=user_message_id,
            session_id=request.session_id,
            message="AI is generating response..."
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in chat endpoint: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to process chat message: {str(e)}"
        )


@app.get("/history/{session_id}", response_model=HistoryResponse, tags=["Chat"])
async def get_history(session_id: str):
    """
    Get chat history for a session
    
    Returns all messages stored in Redis for the given session.
    History is automatically expired after 24 hours (configurable).
    """
    try:
        messages = redis_client.get_history(session_id)
        
        return HistoryResponse(
            session_id=session_id,
            messages=messages,
            count=len(messages)
        )
        
    except Exception as e:
        logger.error(f"Error retrieving history: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to retrieve chat history: {str(e)}"
        )


@app.delete("/history/{session_id}", tags=["Chat"])
async def clear_history(session_id: str):
    """
    Clear chat history for a session
    
    Deletes all messages for the given session from Redis.
    """
    try:
        success = redis_client.clear_history(session_id)
        
        if not success:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to clear history"
            )
        
        return {
            "status": "success",
            "message": f"History cleared for session {session_id}"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error clearing history: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to clear history: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn
    
    logger.info(f"Starting server on {settings.HOST}:{settings.PORT}")
    uvicorn.run(
        "app:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
        log_level=settings.LOG_LEVEL.lower()
    )
