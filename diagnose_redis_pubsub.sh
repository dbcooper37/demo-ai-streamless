#!/bin/bash

echo "=================================="
echo "Redis PubSub Diagnostic Tool"
echo "=================================="
echo ""

SESSION_ID="test_diagnose_$(date +%s)"

echo "🔍 Testing session: $SESSION_ID"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Step 1: Check Redis connection${NC}"
echo "=================================="

if command -v docker &> /dev/null; then
    if docker compose exec -T redis redis-cli ping &> /dev/null; then
        echo -e "${GREEN}✅ Redis is running and accessible${NC}"
    else
        echo -e "${RED}❌ Cannot connect to Redis${NC}"
        echo "Make sure services are running: docker compose up -d"
        exit 1
    fi
else
    echo -e "${YELLOW}⚠️  Docker not found, skipping Redis check${NC}"
fi

echo ""
echo -e "${BLUE}Step 2: Test Python publishing to Redis${NC}"
echo "=================================="

# Create a Python script to publish a test message
cat > /tmp/test_publish.py << 'EOF'
import redis
import json
import sys

session_id = sys.argv[1] if len(sys.argv) > 1 else "test_session"
channel = f"chat:stream:{session_id}"

try:
    r = redis.Redis(host='localhost', port=6379, decode_responses=True)
    
    # Test connection
    r.ping()
    print(f"✅ Connected to Redis")
    
    # Publish test message
    message = {
        "message_id": "test_msg_123",
        "session_id": session_id,
        "user_id": "test_user",
        "role": "assistant",
        "content": "Test message from diagnostic script",
        "timestamp": 1234567890,
        "is_complete": False,
        "chunk": "Test"
    }
    
    payload = json.dumps(message)
    subscribers = r.publish(channel, payload)
    
    print(f"📤 Published to channel: {channel}")
    print(f"👥 Subscribers: {subscribers}")
    
    if subscribers == 0:
        print(f"⚠️  WARNING: No subscribers listening to this channel!")
        print(f"   This means Java WebSocket Server is not connected or not subscribed.")
    else:
        print(f"✅ {subscribers} subscriber(s) received the message")
    
    sys.exit(0 if subscribers > 0 else 1)
    
except redis.ConnectionError as e:
    print(f"❌ Redis connection error: {e}")
    print(f"   Make sure Redis is running on localhost:6379")
    sys.exit(1)
except Exception as e:
    print(f"❌ Error: {e}")
    sys.exit(1)
EOF

if command -v python3 &> /dev/null; then
    echo "Running Python publish test..."
    python3 /tmp/test_publish.py "$SESSION_ID"
    PUBLISH_RESULT=$?
    echo ""
else
    echo -e "${YELLOW}⚠️  Python3 not found, skipping publish test${NC}"
    PUBLISH_RESULT=0
fi

echo ""
echo -e "${BLUE}Step 3: Check Java logs for subscription${NC}"
echo "=================================="

if command -v docker &> /dev/null; then
    echo "Searching for subscription logs..."
    SUBSCRIBE_LOGS=$(docker compose logs java-websocket-server 2>/dev/null | grep -i "Subscribed to legacy channel" | tail -5)
    
    if [ -n "$SUBSCRIBE_LOGS" ]; then
        echo -e "${GREEN}✅ Found subscription logs:${NC}"
        echo "$SUBSCRIBE_LOGS"
    else
        echo -e "${RED}❌ No subscription logs found${NC}"
        echo ""
        echo "This could mean:"
        echo "  1. No WebSocket client has connected yet"
        echo "  2. Java service is not running"
        echo "  3. ChatOrchestrator.startStreamingSession() is not being called"
    fi
else
    echo -e "${YELLOW}⚠️  Docker not found, skipping log check${NC}"
fi

echo ""
echo ""
echo -e "${BLUE}Step 4: Monitor Redis PubSub in real-time${NC}"
echo "=================================="
echo ""
echo "To monitor Redis PubSub messages in real-time:"
echo ""
echo -e "${YELLOW}Terminal 1:${NC} Subscribe to all channels"
if command -v docker &> /dev/null; then
    echo "  docker compose exec redis redis-cli"
    echo "  > PSUBSCRIBE chat:stream:*"
else
    echo "  redis-cli"
    echo "  > PSUBSCRIBE chat:stream:*"
fi
echo ""
echo -e "${YELLOW}Terminal 2:${NC} Send a test message via curl"
echo "  curl -X POST http://localhost:8000/chat \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"session_id\":\"$SESSION_ID\",\"user_id\":\"demo_user\",\"message\":\"test\"}'"
echo ""
echo "Terminal 1 should show messages being published!"
echo ""

echo ""
echo -e "${BLUE}Summary${NC}"
echo "=================================="

if [ $PUBLISH_RESULT -eq 0 ]; then
    echo -e "${GREEN}✅ Redis PubSub appears to be working${NC}"
    echo ""
    echo "Next steps to debug streaming:"
    echo "1. Make sure a WebSocket client is connected BEFORE sending messages"
    echo "2. Use: python3 test_streaming_websocket.py"
    echo "3. Check logs: docker compose logs -f java-websocket-server | grep ChatOrchestrator"
else
    echo -e "${RED}❌ Redis PubSub has issues${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "1. Check if services are running: docker compose ps"
    echo "2. Restart services: docker compose restart"
    echo "3. Check Redis logs: docker compose logs redis"
fi

echo ""
echo -e "${BLUE}Quick Test Command:${NC}"
echo "  python3 test_streaming_websocket.py"
echo ""

# Cleanup
rm -f /tmp/test_publish.py
