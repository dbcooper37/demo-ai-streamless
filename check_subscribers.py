#!/usr/bin/env python3
"""
Quick script to check Redis PubSub subscribers for a specific session
Usage: python3 check_subscribers.py [session_id]
"""
import redis
import sys
import json
from datetime import datetime

def check_subscribers(session_id=None):
    """Check how many subscribers are listening to a session's channel"""
    
    if session_id is None:
        session_id = f"test_session_{int(datetime.now().timestamp())}"
        print(f"No session_id provided, using: {session_id}")
    
    channel = f"chat:stream:{session_id}"
    
    try:
        # Connect to Redis
        r = redis.Redis(host='localhost', port=6379, decode_responses=True)
        
        # Test connection
        print(f"✅ Connected to Redis")
        print(f"📡 Channel: {channel}")
        print()
        
        # Create a test message (same format as Python AI service)
        test_message = {
            "message_id": f"test_{int(datetime.now().timestamp())}",
            "session_id": session_id,
            "user_id": "test_user",
            "role": "assistant",
            "content": "Test message to check subscribers",
            "timestamp": int(datetime.now().timestamp() * 1000),
            "is_complete": False,
            "chunk": "Test"
        }
        
        # Publish and get subscriber count
        payload = json.dumps(test_message)
        subscribers = r.publish(channel, payload)
        
        print(f"📤 Published test message to: {channel}")
        print(f"👥 Active subscribers: {subscribers}")
        print()
        
        if subscribers == 0:
            print("❌ WARNING: No subscribers!")
            print()
            print("This means:")
            print("  • No Java WebSocket Server is listening to this channel")
            print("  • WebSocket client hasn't connected yet, OR")
            print("  • Session ID doesn't match between WebSocket and this test")
            print()
            print("Solutions:")
            print("  1. Connect a WebSocket client with this session_id:")
            print(f"     wscat -c 'ws://localhost:8080/ws?session_id={session_id}&user_id=demo_user&token=dev-token'")
            print()
            print("  2. Or use the automated test:")
            print("     python3 test_streaming_websocket.py")
            print()
            return False
        else:
            print(f"✅ SUCCESS: {subscribers} subscriber(s) are listening!")
            print()
            print("This means:")
            print(f"  • {subscribers} Java WebSocket Server(s) are subscribed to this channel")
            print("  • Messages published to this channel will be received")
            print("  • Streaming should work for this session!")
            print()
            print("Next steps:")
            print("  1. Send a real message via HTTP:")
            print(f"     curl -X POST http://localhost:8000/chat \\")
            print(f"       -H 'Content-Type: application/json' \\")
            print(f"       -d '{{\"session_id\":\"{session_id}\",\"user_id\":\"demo_user\",\"message\":\"hello\"}}'")
            print()
            print("  2. Check Python logs to verify subscribers count:")
            print("     docker compose logs -f python-ai-service | grep subscribers")
            print()
            return True
            
    except redis.ConnectionError as e:
        print(f"❌ Redis connection error: {e}")
        print()
        print("Make sure Redis is running:")
        print("  docker compose ps redis")
        print("  docker compose up -d redis")
        return False
    except Exception as e:
        print(f"❌ Error: {e}")
        return False


if __name__ == "__main__":
    print()
    print("=" * 60)
    print("Redis PubSub Subscribers Checker")
    print("=" * 60)
    print()
    
    # Get session_id from command line or generate one
    session_id = sys.argv[1] if len(sys.argv) > 1 else None
    
    success = check_subscribers(session_id)
    
    print("=" * 60)
    print()
    
    sys.exit(0 if success else 1)
