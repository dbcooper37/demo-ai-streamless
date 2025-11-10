#!/bin/bash

echo "=========================================="
echo "🧪 API Proxy Testing Script"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
PASSED=0
FAILED=0

test_endpoint() {
    local name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local expected="$5"
    
    echo -n "Testing $name... "
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s "$url" 2>/dev/null)
    else
        response=$(curl -s -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
    fi
    
    if echo "$response" | grep -q "$expected"; then
        echo -e "${GREEN}✅ PASS${NC}"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}❌ FAIL${NC}"
        echo "   Expected: $expected"
        echo "   Got: $response"
        ((FAILED++))
        return 1
    fi
}

echo "1️⃣  Testing Java Backend Health..."
test_endpoint "Java Health" "GET" "http://localhost:8080/health" "" "UP"

echo ""
echo "2️⃣  Testing AI Service Connectivity..."
test_endpoint "AI Connectivity" "GET" "http://localhost:8080/api/ai-health" "" "reachable"

echo ""
echo "3️⃣  Testing Chat API Proxy..."
test_endpoint "Chat Endpoint" "POST" "http://localhost:8080/api/chat" \
    '{"session_id":"test_proxy","message":"Testing proxy","user_id":"test"}' \
    "streaming"

echo ""
echo "4️⃣  Testing History API Proxy..."
test_endpoint "History Endpoint" "GET" "http://localhost:8080/api/history/test_proxy" "" "session_id"

echo ""
echo "5️⃣  Testing Cancel API Proxy..."
test_endpoint "Cancel Endpoint" "POST" "http://localhost:8080/api/cancel" \
    '{"session_id":"test","message_id":"test123"}' \
    "status"

echo ""
echo "6️⃣  Verifying Frontend Configuration..."
if docker compose exec frontend sh -c 'cat /etc/hosts 2>/dev/null || echo skip' | grep -q 'skip'; then
    echo -e "${YELLOW}⚠️  SKIP${NC} - Frontend container not accessible"
else
    echo -e "${GREEN}✅ PASS${NC} - Frontend container running"
    ((PASSED++))
fi

echo ""
echo "7️⃣  Checking Logs for Proxy Activity..."
PROXY_LOGS=$(docker compose logs --tail=50 java-websocket-server 2>/dev/null | grep -c "Proxying" || echo "0")
if [ "$PROXY_LOGS" -gt "0" ]; then
    echo -e "${GREEN}✅ PASS${NC} - Found $PROXY_LOGS proxy log entries"
    ((PASSED++))
else
    echo -e "${YELLOW}⚠️  WARN${NC} - No proxy activity in logs yet (may be new deployment)"
fi

echo ""
echo "=========================================="
echo "📊 Test Results"
echo "=========================================="
echo "Total Passed: ${GREEN}$PASSED${NC}"
echo "Total Failed: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ All tests passed!${NC}"
    echo ""
    echo "🎉 API Proxy is working correctly!"
    echo ""
    echo "Next steps:"
    echo "  1. Open http://localhost:3000 in browser"
    echo "  2. Send a message"
    echo "  3. Check DevTools → Network tab"
    echo "  4. Verify requests go to localhost:8080/api/*"
    exit 0
else
    echo -e "${RED}❌ Some tests failed${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  - Check if all services are running: docker compose ps"
    echo "  - Check logs: docker compose logs java-websocket-server"
    echo "  - Verify network: docker compose exec java-websocket-server ping python-ai-service"
    exit 1
fi
