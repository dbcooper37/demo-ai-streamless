"""
Redis client and operations
"""
import json
import logging
from typing import Optional, List
import redis
from redis.exceptions import RedisError

from config import settings
from models import ChatMessage


logger = logging.getLogger(__name__)


class RedisClient:
    """Redis client wrapper with error handling"""
    
    def __init__(self):
        self._client: Optional[redis.Redis] = None
    
    def connect(self) -> redis.Redis:
        """Connect to Redis"""
        try:
            self._client = redis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                db=settings.REDIS_DB,
                password=settings.REDIS_PASSWORD,
                decode_responses=True,
                socket_connect_timeout=5,
                socket_timeout=5
            )
            # Test connection
            self._client.ping()
            logger.info(f"Connected to Redis at {settings.REDIS_HOST}:{settings.REDIS_PORT}")
            return self._client
        except RedisError as e:
            logger.error(f"Failed to connect to Redis: {e}")
            raise
    
    @property
    def client(self) -> redis.Redis:
        """Get Redis client instance"""
        if self._client is None:
            return self.connect()
        return self._client
    
    def ping(self) -> bool:
        """Check Redis connection"""
        try:
            return self.client.ping()
        except RedisError as e:
            logger.error(f"Redis ping failed: {e}")
            return False
    
    def publish_message(self, session_id: str, message: ChatMessage) -> bool:
        """Publish message to Redis PubSub channel"""
        try:
            channel = f"chat:stream:{session_id}"
            payload = message.model_dump_json()

            logger.info("=== PUBLISHING TO REDIS ===")
            logger.info(f"Channel: {channel}")
            logger.info(f"Message ID: {message.message_id}")
            logger.info(f"Role: {message.role}")
            logger.info(f"Is Complete: {message.is_complete}")
            logger.info(f"Content Length: {len(message.content)}")
            logger.info(f"Chunk: {message.chunk}")
            logger.info(f"Payload size: {len(payload)} bytes")
            logger.info(f"Payload (first 200 chars): {payload[:200]}")

            result = self.client.publish(channel, payload)

            logger.info(f"=== PUBLISH RESULT ===")
            logger.info(f"Subscribers received: {result}")
            if result == 0:
                logger.warning(f"WARNING: No subscribers listening to channel {channel}!")
            logger.info("=== PUBLISH COMPLETE ===")

            return True
        except RedisError as e:
            logger.error(f"=== FAILED TO PUBLISH MESSAGE ===")
            logger.error(f"Error: {e}")
            return False
    
    def save_to_history(self, session_id: str, message: ChatMessage) -> bool:
        """Save message to chat history"""
        try:
            key = f"chat:history:{session_id}"
            payload = message.model_dump_json()
            
            # Add to list
            self.client.rpush(key, payload)
            
            # Set expiration
            self.client.expire(key, settings.CHAT_HISTORY_TTL)
            
            logger.debug(f"Saved message to history: {key}")
            return True
        except RedisError as e:
            logger.error(f"Failed to save to history: {e}")
            return False
    
    def get_history(self, session_id: str) -> List[ChatMessage]:
        """Get chat history for a session"""
        try:
            key = f"chat:history:{session_id}"
            history = self.client.lrange(key, 0, -1)
            
            messages = []
            for msg_json in history:
                try:
                    msg_dict = json.loads(msg_json)
                    messages.append(ChatMessage(**msg_dict))
                except (json.JSONDecodeError, ValueError) as e:
                    logger.warning(f"Failed to parse message: {e}")
                    continue
            
            logger.debug(f"Retrieved {len(messages)} messages from history")
            return messages
            
        except RedisError as e:
            logger.error(f"Failed to get history: {e}")
            return []
    
    def clear_history(self, session_id: str) -> bool:
        """Clear chat history for a session"""
        try:
            key = f"chat:history:{session_id}"
            self.client.delete(key)
            logger.info(f"Cleared history for session: {session_id}")
            return True
        except RedisError as e:
            logger.error(f"Failed to clear history: {e}")
            return False
    
    def set_cancel_flag(self, session_id: str, message_id: str, ttl: int = 60) -> bool:
        """Set cancel flag in Redis for distributed cancellation"""
        try:
            key = f"chat:cancel:{session_id}:{message_id}"
            self.client.setex(key, ttl, "1")
            logger.info(f"Set cancel flag: session={session_id}, message={message_id}")
            return True
        except RedisError as e:
            logger.error(f"CRITICAL: Failed to set cancel flag (Redis down?): {e}")
            return False
    
    def check_cancel_flag(self, session_id: str, message_id: str) -> bool:
        """Check if cancellation has been requested"""
        try:
            key = f"chat:cancel:{session_id}:{message_id}"
            result = self.client.exists(key)
            return result > 0
        except RedisError as e:
            # IMPORTANT: If Redis fails, we continue streaming (fail-safe)
            # This prevents Redis outages from breaking all streaming
            logger.warning(f"Redis unavailable for cancel check, continuing stream: {e}")
            return False
    
    def clear_cancel_flag(self, session_id: str, message_id: str) -> bool:
        """Clear cancel flag after processing"""
        try:
            key = f"chat:cancel:{session_id}:{message_id}"
            self.client.delete(key)
            return True
        except RedisError as e:
            logger.error(f"Failed to clear cancel flag: {e}")
            return False
    
    def register_active_stream(self, session_id: str, message_id: str, ttl: int = 300) -> bool:
        """Register an active streaming task in Redis"""
        try:
            key = f"chat:active:{session_id}"
            self.client.setex(key, ttl, message_id)
            logger.info(f"Registered active stream: session={session_id}, message={message_id}")
            return True
        except RedisError as e:
            logger.error(f"Failed to register active stream: {e}")
            return False
    
    def get_active_stream(self, session_id: str) -> str:
        """Get the active streaming message ID for a session"""
        try:
            key = f"chat:active:{session_id}"
            message_id = self.client.get(key)
            return message_id if message_id else None
        except RedisError as e:
            logger.error(f"Failed to get active stream: {e}")
            return None
    
    def clear_active_stream(self, session_id: str) -> bool:
        """Clear active streaming task registration"""
        try:
            key = f"chat:active:{session_id}"
            self.client.delete(key)
            return True
        except RedisError as e:
            logger.error(f"Failed to clear active stream: {e}")
            return False


# Global Redis client instance
redis_client = RedisClient()
