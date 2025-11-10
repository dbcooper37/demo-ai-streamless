#!/bin/bash

# Deployment script for API Proxy Architecture
# All API calls now go through Java Backend

set -e

echo "=========================================="
echo "🚀 API Proxy Architecture Deployment"
echo "=========================================="
echo ""

echo "📋 Changes:"
echo "   - Created ChatController in Java Backend"
echo "   - Updated Frontend to call Java Backend"
echo "   - All REST APIs now proxied through Java"
echo ""

# Confirm deployment
read -p "Continue with deployment? (y/N): " CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo "❌ Deployment cancelled"
    exit 0
fi

echo ""
echo "🔨 Step 1: Building Java Backend..."
docker compose build --no-cache java-websocket-server

echo ""
echo "🔨 Step 2: Building Frontend..."
docker compose build --no-cache frontend

echo ""
echo "🛑 Step 3: Stopping old containers..."
docker compose stop java-websocket-server frontend

echo ""
echo "🚀 Step 4: Starting services..."
docker compose up -d java-websocket-server frontend python-ai-service redis

echo ""
echo "⏳ Waiting for services to be ready (15 seconds)..."
sleep 15

echo ""
echo "🔍 Step 5: Checking service status..."
docker compose ps java-websocket-server frontend python-ai-service

echo ""
echo "✅ Step 6: Running health checks..."

# Check Java Backend
echo "   Testing Java Backend..."
JAVA_HEALTH=$(curl -s http://localhost:8080/health | jq -r '.status' 2>/dev/null || echo "ERROR")
if [ "$JAVA_HEALTH" = "UP" ]; then
    echo "   ✅ Java Backend: UP"
else
    echo "   ⚠️  Java Backend: $JAVA_HEALTH"
fi

# Check Python AI Service connectivity from Java
echo "   Testing AI Service connectivity..."
AI_HEALTH=$(curl -s http://localhost:8080/api/ai-health | jq -r '.ai_service' 2>/dev/null || echo "ERROR")
if [ "$AI_HEALTH" = "reachable" ]; then
    echo "   ✅ AI Service: Reachable from Java Backend"
else
    echo "   ⚠️  AI Service: $AI_HEALTH"
fi

# Check Frontend
echo "   Testing Frontend..."
FRONTEND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null || echo "000")
if [ "$FRONTEND_STATUS" = "200" ]; then
    echo "   ✅ Frontend: UP"
else
    echo "   ⚠️  Frontend: Status $FRONTEND_STATUS"
fi

echo ""
echo "🧪 Step 7: Testing API proxy..."

# Test chat endpoint
echo "   Testing POST /api/chat..."
CHAT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test","message":"Hello","user_id":"test"}' \
  2>/dev/null || echo '{"error":"failed"}')

CHAT_STATUS=$(echo "$CHAT_RESPONSE" | jq -r '.status' 2>/dev/null || echo "ERROR")
if [ "$CHAT_STATUS" = "streaming" ]; then
    MESSAGE_ID=$(echo "$CHAT_RESPONSE" | jq -r '.message_id')
    echo "   ✅ Chat API working! (message_id: ${MESSAGE_ID:0:8}...)"
else
    echo "   ⚠️  Chat API: $CHAT_STATUS"
    echo "   Response: $CHAT_RESPONSE"
fi

echo ""
echo "📊 Step 8: Checking logs..."
echo ""
echo "Java Backend logs (last 10 lines):"
docker compose logs --tail=10 java-websocket-server | grep -v "DEBUG" || true
echo ""

echo "=========================================="
echo "✅ Deployment Complete!"
echo "=========================================="
echo ""
echo "📝 Summary:"
echo "   ✅ Java Backend: Proxy endpoints created"
echo "   ✅ Frontend: Updated to use Java Backend"
echo "   ✅ API Flow: Frontend → Java (8080) → Python (8000)"
echo ""
echo "🌐 Access Points:"
echo "   Frontend:     http://localhost:3000"
echo "   Java Backend: http://localhost:8080"
echo "   API Docs:     See API_PROXY_SUMMARY.md"
echo ""
echo "🧪 Test Commands:"
echo "   # Test chat via proxy"
echo "   curl -X POST http://localhost:8080/api/chat \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"session_id\":\"test\",\"message\":\"Hi\",\"user_id\":\"test\"}'"
echo ""
echo "   # Check AI connectivity"
echo "   curl http://localhost:8080/api/ai-health"
echo ""
echo "   # Monitor proxy logs"
echo "   docker compose logs -f java-websocket-server | grep Proxying"
echo ""
echo "📚 Documentation:"
echo "   - API_PROXY_SUMMARY.md - Complete architecture guide"
echo "   - Test the app at http://localhost:3000"
echo ""

# Optional: Open browser
read -p "Open browser now? (y/N): " OPEN_BROWSER
if [ "$OPEN_BROWSER" = "y" ] || [ "$OPEN_BROWSER" = "Y" ]; then
    echo "🌐 Opening browser..."
    if command -v xdg-open > /dev/null; then
        xdg-open http://localhost:3000
    elif command -v open > /dev/null; then
        open http://localhost:3000
    else
        echo "   Please open http://localhost:3000 manually"
    fi
fi

echo ""
echo "✨ All done! API proxy is now active! 🎉"
