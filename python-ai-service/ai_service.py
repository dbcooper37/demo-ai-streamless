"""
AI Service for generating responses
"""
import asyncio
import logging
from typing import AsyncGenerator
import uuid

from config import settings
from models import ChatMessage
from redis_client import redis_client


logger = logging.getLogger(__name__)


class AIService:
    """AI response generation service"""
    
    SAMPLE_RESPONSES = [
        "Xin chào! Tôi là AI assistant. Tôi có thể giúp gì cho bạn hôm nay?",
        "Đây là một ví dụ về streaming response. Mỗi chunk sẽ được gửi qua Redis PubSub.",
        "Khi bạn reload trang, bạn sẽ thấy toàn bộ lịch sử chat cũ và tiếp tục nhận streaming mới.",
        "Hệ thống này sử dụng Redis để lưu trữ lịch sử và PubSub để streaming real-time.",
        "Tôi hiểu câu hỏi của bạn. Đây là câu trả lời được tạo bởi AI service.",
    ]
    
    @classmethod
    async def generate_streaming_response(cls, text: str) -> AsyncGenerator[str, None]:
        """
        Generate streaming response word by word
        Simulates AI processing with delay between words
        """
        words = text.split()
        for i, word in enumerate(words):
            chunk = word + (" " if i < len(words) - 1 else "")
            await asyncio.sleep(settings.STREAM_DELAY)
            yield chunk
    
    @classmethod
    def select_response(cls, user_message: str) -> str:
        """
        Select appropriate response based on user message
        In real implementation, this would call an actual AI model
        """
        message_lower = user_message.lower()
        
        if "streaming" in message_lower:
            return cls.SAMPLE_RESPONSES[1]
        elif "reload" in message_lower or "history" in message_lower:
            return cls.SAMPLE_RESPONSES[2]
        elif "redis" in message_lower or "pubsub" in message_lower:
            return cls.SAMPLE_RESPONSES[3]
        elif any(word in message_lower for word in ["how", "what", "why", "when", "where"]):
            return cls.SAMPLE_RESPONSES[4]
        else:
            return cls.SAMPLE_RESPONSES[0]


class ChatService:
    """Service for handling chat operations"""
    
    def __init__(self):
        self.ai_service = AIService()
    
    async def process_user_message(self, session_id: str, user_id: str, 
                                   message_content: str) -> str:
        """
        Process user message and return message ID
        """
        message_id = str(uuid.uuid4())
        
        # Create user message
        user_message = ChatMessage.create_user_message(
            message_id=message_id,
            session_id=session_id,
            user_id=user_id,
            content=message_content
        )
        
        # Save to history
        redis_client.save_to_history(session_id, user_message)
        
        # Publish to PubSub
        redis_client.publish_message(session_id, user_message)
        
        logger.info(f"Processed user message: session={session_id}, msg_id={message_id}")
        
        return message_id
    
    async def stream_ai_response(self, session_id: str, user_id: str, 
                                 user_message: str) -> None:
        """
        Stream AI response chunk by chunk
        """
        message_id = str(uuid.uuid4())
        
        logger.info(f"Starting AI response streaming for session={session_id}, msg_id={message_id}")
        
        # Select response based on user message
        response_text = AIService.select_response(user_message)
        logger.info(f"Selected response text (length={len(response_text)}): {response_text[:50]}...")
        
        accumulated_content = ""
        chunk_count = 0
        
        try:
            # Stream response word by word
            async for chunk in AIService.generate_streaming_response(response_text):
                accumulated_content += chunk
                chunk_count += 1
                
                # Create streaming message
                stream_message = ChatMessage.create_assistant_message(
                    message_id=message_id,
                    session_id=session_id,
                    user_id=user_id,
                    content=accumulated_content,
                    is_complete=False,
                    chunk=chunk
                )
                
                # Publish to Redis PubSub
                published = redis_client.publish_message(session_id, stream_message)
                logger.debug(f"Published chunk #{chunk_count} to Redis: chunk='{chunk}', accumulated_length={len(accumulated_content)}, published={published}")
                
                await asyncio.sleep(settings.CHUNK_DELAY)
            
            # Send final complete message
            final_message = ChatMessage.create_assistant_message(
                message_id=message_id,
                session_id=session_id,
                user_id=user_id,
                content=accumulated_content,
                is_complete=True
            )
            
            # Publish final message
            published = redis_client.publish_message(session_id, final_message)
            logger.info(f"Published final complete message: published={published}")
            
            # Save to history
            redis_client.save_to_history(session_id, final_message)
            
            logger.info(f"Completed AI response streaming: session={session_id}, msg_id={message_id}, chunks={chunk_count}, total_length={len(accumulated_content)}")
            
        except Exception as e:
            logger.error(f"Error during streaming: {e}")
            
            # Send error message
            error_message = ChatMessage.create_assistant_message(
                message_id=message_id,
                session_id=session_id,
                user_id=user_id,
                content="Xin lỗi, đã có lỗi xảy ra khi xử lý yêu cầu của bạn.",
                is_complete=True
            )
            redis_client.publish_message(session_id, error_message)
            redis_client.save_to_history(session_id, error_message)


# Global chat service instance
chat_service = ChatService()
