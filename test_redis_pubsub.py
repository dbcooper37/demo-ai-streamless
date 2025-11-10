#!/usr/bin/env python3
"""
Test script to verify Redis PubSub is working
Run this to check if messages are being published and received
"""
import redis
import json
import time
import sys

def test_pubsub():
    """Test Redis PubSub functionality"""
    
    # Connect to Redis
    r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)
    
    # Test connection
    try:
        r.ping()
        print("✓ Connected to Redis")
    except redis.ConnectionError as e:
        print(f"✗ Failed to connect to Redis: {e}")
        return False
    
    # Test channel
    test_channel = "chat:stream:test_session"
    
    # Subscribe to channel in a separate thread
    pubsub = r.pubsub()
    pubsub.subscribe(test_channel)
    print(f"✓ Subscribed to channel: {test_channel}")
    
    # Publish a test message
    test_message = {
        "message_id": "test123",
        "session_id": "test_session",
        "user_id": "test_user",
        "role": "assistant",
        "content": "Test message",
        "timestamp": int(time.time() * 1000),
        "is_complete": False
    }
    
    subscribers = r.publish(test_channel, json.dumps(test_message))
    print(f"✓ Published test message, subscribers={subscribers}")
    
    if subscribers == 0:
        print("⚠ Warning: No subscribers listening to this channel!")
    
    # Try to receive the message
    print("Waiting for message...")
    for i in range(5):
        message = pubsub.get_message()
        if message and message['type'] == 'message':
            print(f"✓ Received message: {message['data'][:100]}...")
            return True
        time.sleep(0.5)
    
    print("✗ No message received")
    return False

if __name__ == "__main__":
    print("Testing Redis PubSub...")
    print("-" * 50)
    success = test_pubsub()
    sys.exit(0 if success else 1)
