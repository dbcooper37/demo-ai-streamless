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
        # Track active streaming tasks for cancellation
        self.active_tasks = {}  # session_id -> {"task": Task, "message_id": str, "cancelled": bool}
    
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
                                 user_message: str) -> str:
        """
        Stream AI response chunk by chunk
        Returns the message_id for tracking
        """
        message_id = str(uuid.uuid4())
        
        # Track this streaming task
        self.active_tasks[session_id] = {
            "message_id": message_id,
            "cancelled": False
        }
        
        logger.info(f"Starting AI response streaming for session={session_id}, msg_id={message_id}")
        
        # Select response based on user message
        response_text = AIService.select_response(user_message)
        logger.info(f"Selected response text (length={len(response_text)}): {response_text[:50]}...")
        
        accumulated_content = ""
        chunk_count = 0
        cancelled = False
        
        try:
            # Stream response word by word
            async for chunk in AIService.generate_streaming_response(response_text):
                # Check if cancelled
                if self.active_tasks.get(session_id, {}).get("cancelled", False):
                    logger.info(f"Streaming cancelled for session={session_id}, msg_id={message_id}")
                    cancelled = True
                    break
                
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
            
            if cancelled:
                # Send cancellation message
                cancel_message = ChatMessage.create_assistant_message(
                    message_id=message_id,
                    session_id=session_id,
                    user_id=user_id,
                    content=accumulated_content + "\n\n[Đã hủy]",
                    is_complete=True
                )
                redis_client.publish_message(session_id, cancel_message)
                redis_client.save_to_history(session_id, cancel_message)
                logger.info(f"Streaming cancelled: session={session_id}, msg_id={message_id}, chunks={chunk_count}")
            else:
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
            
        except asyncio.CancelledError:
            logger.info(f"Streaming task cancelled externally: session={session_id}, msg_id={message_id}")
            # Send cancellation message
            cancel_message = ChatMessage.create_assistant_message(
                message_id=message_id,
                session_id=session_id,
                user_id=user_id,
                content=accumulated_content + "\n\n[Đã hủy]",
                is_complete=True
            )
            redis_client.publish_message(session_id, cancel_message)
            redis_client.save_to_history(session_id, cancel_message)
            raise
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
        finally:
            # Clean up tracking
            if session_id in self.active_tasks:
                del self.active_tasks[session_id]
        
        return message_id
    
    def cancel_streaming(self, session_id: str, message_id: str) -> bool:
        """
        Cancel an active streaming task
        Returns True if cancellation was successful
        """
        if session_id in self.active_tasks:
            task_info = self.active_tasks[session_id]
            if task_info["message_id"] == message_id:
                task_info["cancelled"] = True
                logger.info(f"Marked streaming for cancellation: session={session_id}, msg_id={message_id}")
                return True
            else:
                logger.warning(f"Message ID mismatch for cancellation: expected={task_info['message_id']}, got={message_id}")
                return False
        else:
            logger.warning(f"No active streaming task found for session={session_id}")
            return False


# Global chat service instance
chat_service = ChatService()
