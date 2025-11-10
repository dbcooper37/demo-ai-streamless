"""
Data models for AI Service
"""
from typing import Optional, Literal
from pydantic import BaseModel, Field
from datetime import datetime


class ChatRequest(BaseModel):
    """Chat request from client"""
    session_id: str = Field(..., description="Unique session identifier")
    message: str = Field(..., min_length=1, description="User message content")
    user_id: str = Field(default="default_user", description="User identifier")
    
    class Config:
        json_schema_extra = {
            "example": {
                "session_id": "session_12345",
                "message": "Hello, how are you?",
                "user_id": "user_001"
            }
        }


class ChatMessage(BaseModel):
    """Chat message structure"""
    message_id: str
    session_id: str
    user_id: str
    role: Literal["user", "assistant"]
    content: str
    timestamp: int
    is_complete: bool
    chunk: Optional[str] = None
    
    @classmethod
    def create_user_message(cls, message_id: str, session_id: str, 
                           user_id: str, content: str) -> "ChatMessage":
        """Create a user message"""
        return cls(
            message_id=message_id,
            session_id=session_id,
            user_id=user_id,
            role="user",
            content=content,
            timestamp=int(datetime.now().timestamp() * 1000),
            is_complete=True
        )
    
    @classmethod
    def create_assistant_message(cls, message_id: str, session_id: str,
                                 user_id: str, content: str, 
                                 is_complete: bool = False,
                                 chunk: Optional[str] = None) -> "ChatMessage":
        """Create an assistant message"""
        return cls(
            message_id=message_id,
            session_id=session_id,
            user_id=user_id,
            role="assistant",
            content=content,
            timestamp=int(datetime.now().timestamp() * 1000),
            is_complete=is_complete,
            chunk=chunk
        )


class ChatResponse(BaseModel):
    """Response from chat endpoint"""
    status: str
    message_id: str
    session_id: str
    message: Optional[str] = None


class HistoryResponse(BaseModel):
    """Response from history endpoint"""
    session_id: str
    messages: list[ChatMessage]
    count: int


class HealthResponse(BaseModel):
    """Health check response"""
    status: str
    redis: str
    timestamp: str
