#!/bin/bash

# Simple script to test streaming with wscat (if installed)
# This script requires wscat: npm install -g wscat

SESSION_ID="test_session_$(date +%s)"
echo "================================"
echo "Testing Streaming with wscat"
echo "================================"
echo ""
echo "Session ID: $SESSION_ID"
echo ""

# Check if wscat is installed
if ! command -v wscat &> /dev/null; then
    echo "❌ wscat is not installed"
    echo ""
    echo "Please install wscat:"
    echo "  npm install -g wscat"
    echo ""
    echo "Or use the Python test script instead:"
    echo "  python3 test_streaming_websocket.py"
    exit 1
fi

echo "📡 Instructions:"
echo ""
echo "1. This script will connect to WebSocket"
echo "2. In ANOTHER terminal, run:"
echo ""
echo "   curl -X POST http://localhost:8000/chat \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"session_id\":\"$SESSION_ID\",\"user_id\":\"demo_user\",\"message\":\"xin chào\"}'"
echo ""
echo "3. Watch streaming messages appear in this terminal"
echo ""
echo "Press Enter to connect..."
read

echo "🔌 Connecting to WebSocket..."
echo ""

# Connect to WebSocket
wscat -c "ws://localhost:8080/ws?session_id=$SESSION_ID&user_id=demo_user&token=dev-token"
