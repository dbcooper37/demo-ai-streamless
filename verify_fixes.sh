#!/bin/bash

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=================================="
echo "Verifying All Fixes"
echo "=================================="
echo ""

# Check if services are running
echo "1. Checking if services are running..."
if docker ps | grep -q "demo-redis"; then
    echo -e "${GREEN}✓${NC} Redis is running"
else
    echo -e "${RED}✗${NC} Redis is NOT running"
fi

if docker ps | grep -q "demo-kafka"; then
    echo -e "${GREEN}✓${NC} Kafka is running"
else
    echo -e "${RED}✗${NC} Kafka is NOT running"
fi

if docker ps | grep -q "demo-python-ai"; then
    echo -e "${GREEN}✓${NC} Python AI Service is running"
else
    echo -e "${RED}✗${NC} Python AI Service is NOT running"
fi

if docker ps | grep -q "demo-java-websocket"; then
    echo -e "${GREEN}✓${NC} Java WebSocket Server is running"
else
    echo -e "${RED}✗${NC} Java WebSocket Server is NOT running"
fi

if docker ps | grep -q "demo-frontend"; then
    echo -e "${GREEN}✓${NC} Frontend is running"
else
    echo -e "${RED}✗${NC} Frontend is NOT running"
fi

echo ""
echo "2. Checking Kafka configuration..."
if docker logs demo-java-websocket 2>&1 | grep -q "Kafka EventPublisher enabled"; then
    echo -e "${GREEN}✓${NC} Kafka EventPublisher is enabled"
else
    echo -e "${YELLOW}⚠${NC} Kafka EventPublisher might not be enabled (check logs)"
fi

echo ""
echo "3. Checking Redis PubSub listener..."
if docker logs demo-java-websocket 2>&1 | grep -q "RedisMessageListenerContainer"; then
    echo -e "${GREEN}✓${NC} Redis MessageListenerContainer initialized"
else
    echo -e "${YELLOW}⚠${NC} Redis MessageListenerContainer status unknown"
fi

echo ""
echo "4. Checking service health..."

# Python AI Service
PYTHON_HEALTH=$(curl -s http://localhost:8000/health 2>/dev/null)
if echo "$PYTHON_HEALTH" | grep -q "healthy"; then
    echo -e "${GREEN}✓${NC} Python AI Service is healthy"
else
    echo -e "${RED}✗${NC} Python AI Service health check failed"
fi

# Java WebSocket Server
JAVA_HEALTH=$(curl -s http://localhost:8080/actuator/health 2>/dev/null)
if echo "$JAVA_HEALTH" | grep -q "UP"; then
    echo -e "${GREEN}✓${NC} Java WebSocket Server is healthy"
else
    echo -e "${RED}✗${NC} Java WebSocket Server health check failed"
fi

echo ""
echo "=================================="
echo "Configuration Verification"
echo "=================================="
echo ""

echo "Checking docker-compose.yml for correct environment variables..."

# Check Kafka config
if grep -q "SPRING_KAFKA_ENABLED=true" docker-compose.yml; then
    echo -e "${GREEN}✓${NC} SPRING_KAFKA_ENABLED is correctly set"
else
    echo -e "${RED}✗${NC} SPRING_KAFKA_ENABLED is missing or incorrect"
fi

if grep -q "SPRING_KAFKA_BOOTSTRAP_SERVERS" docker-compose.yml; then
    echo -e "${GREEN}✓${NC} SPRING_KAFKA_BOOTSTRAP_SERVERS is correctly set"
else
    echo -e "${RED}✗${NC} SPRING_KAFKA_BOOTSTRAP_SERVERS is missing"
fi

echo ""
echo "=================================="
echo "Manual Test Instructions"
echo "=================================="
echo ""
echo "1. Open http://localhost:3000 in your browser"
echo "2. Send a test message"
echo "3. Verify:"
echo "   - User message appears immediately"
echo "   - AI response streams word-by-word"
echo "   - NO duplicated or overlapping text"
echo "   - Final message is complete and correct"
echo ""
echo "To monitor Redis PubSub in real-time:"
echo "  docker exec -it demo-redis redis-cli"
echo "  > PSUBSCRIBE chat:stream:*"
echo ""
echo "To check Kafka events (if enabled with --profile debug):"
echo "  Open http://localhost:8090"
echo "  Check topics: chat-events, stream-events"
echo ""
echo "=================================="
echo "View detailed logs:"
echo "=================================="
echo ""
echo "Python AI Service:"
echo "  docker logs -f demo-python-ai"
echo ""
echo "Java WebSocket Server:"
echo "  docker logs -f demo-java-websocket"
echo ""
echo "Redis:"
echo "  docker logs -f demo-redis"
echo ""
echo "Kafka:"
echo "  docker logs -f demo-kafka"
echo ""
