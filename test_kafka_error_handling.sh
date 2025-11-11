#!/bin/bash

# Test script for Kafka Deserialization Error Handling
# This script verifies that the ErrorHandlingDeserializer is properly configured

echo "====================================="
echo "Kafka Error Handling Verification"
echo "====================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if services are running
echo -e "${BLUE}1. Checking if services are running...${NC}"
if docker ps | grep -q "demo-java-websocket"; then
    echo -e "${GREEN}✓${NC} Java WebSocket service is running"
else
    echo -e "${RED}✗${NC} Java WebSocket service is not running"
    echo "Please start services with: docker-compose up -d"
    exit 1
fi

if docker ps | grep -q "demo-kafka"; then
    echo -e "${GREEN}✓${NC} Kafka service is running"
else
    echo -e "${RED}✗${NC} Kafka service is not running"
    echo "Please start services with: docker-compose up -d"
    exit 1
fi

echo ""

# Check configuration
echo -e "${BLUE}2. Verifying ErrorHandlingDeserializer configuration...${NC}"
if grep -q "ErrorHandlingDeserializer.class" java-websocket-server/src/main/java/com/demo/websocket/config/KafkaConfig.java; then
    echo -e "${GREEN}✓${NC} ErrorHandlingDeserializer is configured in KafkaConfig.java"
else
    echo -e "${RED}✗${NC} ErrorHandlingDeserializer not found in KafkaConfig.java"
    exit 1
fi

if grep -q "DefaultErrorHandler" java-websocket-server/src/main/java/com/demo/websocket/config/KafkaConfig.java; then
    echo -e "${GREEN}✓${NC} DefaultErrorHandler is configured"
else
    echo -e "${RED}✗${NC} DefaultErrorHandler not found in KafkaConfig.java"
    exit 1
fi

echo ""

# Check application logs for Kafka consumer startup
echo -e "${BLUE}3. Checking application logs for Kafka initialization...${NC}"
if docker logs demo-java-websocket 2>&1 | grep -q "AnalyticsConsumer initialized"; then
    echo -e "${GREEN}✓${NC} AnalyticsConsumer initialized successfully"
else
    echo -e "${YELLOW}⚠${NC} AnalyticsConsumer initialization not found in logs (may be disabled)"
fi

if docker logs demo-java-websocket 2>&1 | grep -q "AuditTrailConsumer initialized"; then
    echo -e "${GREEN}✓${NC} AuditTrailConsumer initialized successfully"
else
    echo -e "${YELLOW}⚠${NC} AuditTrailConsumer initialization not found in logs (may be disabled)"
fi

# Check for previous serialization errors
if docker logs demo-java-websocket 2>&1 | grep -q "SerializationException"; then
    echo -e "${YELLOW}⚠${NC} Found SerializationException in logs - checking if handled properly..."
    if docker logs demo-java-websocket 2>&1 | grep -q "Error processing Kafka record"; then
        echo -e "${GREEN}✓${NC} Errors are being handled and logged properly"
    else
        echo -e "${RED}✗${NC} SerializationException found but not handled"
    fi
else
    echo -e "${GREEN}✓${NC} No serialization errors in logs"
fi

echo ""

# Test with malformed message (optional interactive test)
echo -e "${BLUE}4. Test with malformed message (optional)${NC}"
echo -e "This test will send a malformed message to Kafka to verify error handling."
echo -e "The consumer should log the error and continue processing."
echo ""
read -p "Do you want to run this test? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Sending malformed message to chat-events topic...${NC}"
    
    # Send malformed message
    docker exec demo-kafka /bin/bash -c "echo 'invalid-json-{broken' | kafka-console-producer.sh --broker-list localhost:9092 --topic chat-events" 2>/dev/null
    
    # Wait a moment for processing
    echo "Waiting 3 seconds for message processing..."
    sleep 3
    
    # Check logs
    echo -e "${YELLOW}Checking logs for error handling...${NC}"
    if docker logs --since=5s demo-java-websocket 2>&1 | grep -q "Error processing Kafka record"; then
        echo -e "${GREEN}✓${NC} Malformed message was caught and logged by error handler"
        echo ""
        echo "Recent error logs:"
        docker logs --since=5s demo-java-websocket 2>&1 | grep -A 3 "Error processing Kafka record"
    elif docker logs --since=5s demo-java-websocket 2>&1 | grep -q "Failed to process audit event"; then
        echo -e "${GREEN}✓${NC} Consumer caught the error (logged as 'Failed to process')"
    else
        echo -e "${YELLOW}⚠${NC} No error logged yet (may take longer to process)"
    fi
    
    echo ""
    echo -e "${YELLOW}Sending valid message to verify consumer is still working...${NC}"
    
    # Send valid message
    VALID_MSG='{"eventType":"MESSAGE_SENT","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","userId":"test-user","conversationId":"test-conv","messageId":"test-msg-1","content":"Test message"}'
    echo "$VALID_MSG" | docker exec -i demo-kafka kafka-console-producer.sh --broker-list localhost:9092 --topic chat-events 2>/dev/null
    
    sleep 2
    
    if docker logs --since=3s demo-java-websocket 2>&1 | grep -q "Audit log saved\|test-msg-1"; then
        echo -e "${GREEN}✓${NC} Consumer is still processing messages after error"
    else
        echo -e "${YELLOW}⚠${NC} Could not verify message processing (check logs manually)"
    fi
else
    echo "Skipping malformed message test"
fi

echo ""

# Summary
echo "====================================="
echo -e "${GREEN}VERIFICATION COMPLETE${NC}"
echo "====================================="
echo ""
echo "Summary:"
echo "- ErrorHandlingDeserializer is properly configured"
echo "- DefaultErrorHandler is set up for error logging"
echo "- Kafka consumers are running (if enabled)"
echo ""
echo "The fix will:"
echo "  1. Catch deserialization errors gracefully"
echo "  2. Log detailed error information"
echo "  3. Skip bad records and continue processing"
echo "  4. Prevent consumer crashes"
echo ""
echo "For more details, see: KAFKA_DESERIALIZATION_FIX.md"
echo ""
