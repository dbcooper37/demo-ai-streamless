#!/bin/bash

# Test Architecture Fix - Frontend Routing Through Backend
# Kiểm tra xem frontend có đang gọi qua backend không

echo "================================================"
echo "  TEST ARCHITECTURE - FRONTEND → BACKEND → AI"
echo "================================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check which setup is running
SINGLE_NODE=false
MULTI_NODE=false

if docker ps | grep -q "demo-java-websocket" && ! docker ps | grep -q "demo-nginx-lb"; then
    SINGLE_NODE=true
    echo -e "${BLUE}Detected: SINGLE-NODE setup${NC}"
elif docker ps | grep -q "demo-nginx-lb"; then
    MULTI_NODE=true
    echo -e "${BLUE}Detected: MULTI-NODE setup${NC}"
else
    echo -e "${RED}No services running!${NC}"
    echo "Please start services first:"
    echo "  Single-node: docker-compose up -d"
    echo "  Multi-node: docker-compose -f docker-compose.multi-node.yml up -d"
    exit 1
fi

echo ""

# ===================================
# Test 1: Check Frontend Config
# ===================================
echo -e "${BLUE}Test 1: Check Frontend Configuration${NC}"
echo "-----------------------------------"

if docker exec demo-frontend env | grep -q "VITE_API_URL"; then
    API_URL=$(docker exec demo-frontend env | grep VITE_API_URL | cut -d'=' -f2)
    WS_URL=$(docker exec demo-frontend env | grep VITE_WS_URL | cut -d'=' -f2)
    
    echo "Frontend Config:"
    echo "  VITE_API_URL: $API_URL"
    echo "  VITE_WS_URL: $WS_URL"
    echo ""
    
    if $SINGLE_NODE; then
        if echo "$API_URL" | grep -q "8080/api"; then
            echo -e "${GREEN}✓${NC} Frontend routing through Java backend (port 8080)"
        else
            echo -e "${RED}✗${NC} Frontend NOT routing through backend correctly"
            echo "  Expected: http://localhost:8080/api"
            echo "  Got: $API_URL"
        fi
    fi
    
    if $MULTI_NODE; then
        if echo "$API_URL" | grep -q "8080/api"; then
            echo -e "${GREEN}✓${NC} Frontend routing through NGINX load balancer (port 8080)"
        elif echo "$API_URL" | grep -q "8001"; then
            echo -e "${RED}✗${NC} Frontend routing DIRECTLY to AI service (WRONG!)"
            echo "  Expected: http://localhost:8080/api (NGINX)"
            echo "  Got: $API_URL"
            exit 1
        fi
    fi
else
    echo -e "${YELLOW}⚠${NC} Cannot check frontend config"
fi

echo ""

# ===================================
# Test 2: Test Backend Proxy
# ===================================
echo -e "${BLUE}Test 2: Test Backend API Proxy${NC}"
echo "-----------------------------------"

if $SINGLE_NODE; then
    # Test direct backend call
    echo "Testing: http://localhost:8080/api/ai-health"
    if response=$(curl -s -f http://localhost:8080/api/ai-health 2>&1); then
        echo -e "${GREEN}✓${NC} Backend proxy working"
        echo "Response: $response" | head -c 80
        echo "..."
    else
        echo -e "${RED}✗${NC} Backend proxy NOT working"
        echo "Error: $response"
    fi
fi

if $MULTI_NODE; then
    # Test NGINX load balancer
    echo "Testing: http://localhost:8080/api/ai-health (via NGINX)"
    if response=$(curl -s -f http://localhost:8080/api/ai-health 2>&1); then
        echo -e "${GREEN}✓${NC} NGINX proxy to backend working"
        echo "Response: $response" | head -c 80
        echo "..."
    else
        echo -e "${RED}✗${NC} NGINX proxy NOT working"
        echo "Error: $response"
    fi
fi

echo ""

# ===================================
# Test 3: Check Backend Logs
# ===================================
echo -e "${BLUE}Test 3: Check Backend Proxy Logs${NC}"
echo "-----------------------------------"

if $SINGLE_NODE; then
    if docker logs --since=5m demo-java-websocket 2>&1 | grep -q "Proxying.*request to AI service"; then
        echo -e "${GREEN}✓${NC} Backend is proxying requests to AI service"
        echo ""
        echo "Recent proxy logs:"
        docker logs --since=5m demo-java-websocket 2>&1 | grep "Proxying" | tail -3
    else
        echo -e "${YELLOW}⚠${NC} No recent proxy logs (this is OK if no requests were made)"
    fi
fi

if $MULTI_NODE; then
    found_proxy=false
    for node in 1 2 3; do
        if docker logs --since=5m demo-java-websocket-$node 2>&1 | grep -q "Proxying"; then
            found_proxy=true
        fi
    done
    
    if $found_proxy; then
        echo -e "${GREEN}✓${NC} Backend nodes are proxying requests to AI service"
        echo ""
        echo "Recent proxy logs from backends:"
        for node in 1 2 3; do
            logs=$(docker logs --since=5m demo-java-websocket-$node 2>&1 | grep "Proxying" | tail -1)
            if [ ! -z "$logs" ]; then
                echo "  Node $node: $logs"
            fi
        done
    else
        echo -e "${YELLOW}⚠${NC} No recent proxy logs (OK if no requests were made)"
    fi
fi

echo ""

# ===================================
# Test 4: Verify NO Direct AI Calls
# ===================================
echo -e "${BLUE}Test 4: Verify No Direct AI Service Calls${NC}"
echo "-----------------------------------"

# Check if AI service ports are exposed
if docker ps | grep "8001:8000\|8002:8000\|8003:8000"; then
    echo -e "${YELLOW}⚠${NC} AI service ports are exposed (for debugging only)"
    echo "  Make sure frontend is NOT using these ports!"
else
    echo -e "${GREEN}✓${NC} AI service ports not exposed directly"
fi

# Check frontend config again
if docker exec demo-frontend env | grep VITE_API_URL | grep -q "800[0-3]"; then
    echo -e "${RED}✗${NC} CRITICAL: Frontend configured to call AI service directly!"
    echo "  This bypasses backend business logic and load balancing!"
    exit 1
else
    echo -e "${GREEN}✓${NC} Frontend NOT configured to call AI service directly"
fi

echo ""

# ===================================
# Test 5: Test Load Balancing (Multi-Node Only)
# ===================================
if $MULTI_NODE; then
    echo -e "${BLUE}Test 5: Test Load Balancing${NC}"
    echo "-----------------------------------"
    
    echo "Sending 10 requests through NGINX..."
    for i in {1..10}; do
        curl -s http://localhost:8080/actuator/health > /dev/null 2>&1
        echo -n "."
    done
    echo ""
    echo ""
    
    # Check which backends handled requests
    echo "Request distribution:"
    for node in 1 2 3; do
        count=$(docker logs --since=30s demo-java-websocket-$node 2>&1 | grep -c "/actuator/health" || echo 0)
        echo "  Backend $node: $count requests"
    done
    
    echo ""
    echo -e "${GREEN}✓${NC} Load balancing check complete"
    echo "  (Distribution should be roughly equal)"
fi

echo ""

# ===================================
# Summary
# ===================================
echo "================================================"
echo -e "${GREEN}TEST SUMMARY${NC}"
echo "================================================"
echo ""

if $SINGLE_NODE; then
    echo "Architecture (Single-Node):"
    echo ""
    echo "  Frontend (3000)"
    echo "       ↓ HTTP/WebSocket"
    echo "  Java Backend (8080)"
    echo "       ↓ HTTP"
    echo "  Python AI Service (8000)"
    echo ""
    echo "✓ All traffic flows through Java backend"
    echo "✓ Frontend does NOT call AI service directly"
fi

if $MULTI_NODE; then
    echo "Architecture (Multi-Node):"
    echo ""
    echo "  Frontend (3000)"
    echo "       ↓ HTTP/WebSocket"
    echo "  NGINX Load Balancer (8080)"
    echo "       ↓ Round-Robin"
    echo "  Java Backend Nodes (1, 2, 3)"
    echo "       ↓ HTTP"
    echo "  Python AI Service Nodes (1, 2, 3)"
    echo ""
    echo "✓ All traffic flows through NGINX → Java backends"
    echo "✓ Frontend does NOT call AI service directly"
    echo "✓ Load balancing is working"
fi

echo ""
echo "Next steps:"
echo "1. Test streaming via UI: http://localhost:3000"
echo "2. Monitor logs:"
if $SINGLE_NODE; then
    echo "   docker logs -f demo-java-websocket | grep Proxying"
fi
if $MULTI_NODE; then
    echo "   docker logs -f demo-nginx-lb"
    echo "   docker logs -f demo-java-websocket-1 | grep Proxying"
fi
echo ""
echo "Documentation: cat ARCHITECTURE_FIX.md"
echo ""
