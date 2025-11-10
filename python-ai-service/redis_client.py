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
            result = self.client.publish(channel, payload)
            logger.info(f"Published to {channel}: role={message.role}, is_complete={message.is_complete}, content_len={len(message.content)}, subscribers={result}")
            return True
        except RedisError as e:
            logger.error(f"Failed to publish message: {e}")
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


# Global Redis client instance
redis_client = RedisClient()
