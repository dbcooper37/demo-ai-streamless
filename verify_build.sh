#!/bin/bash

echo "===================================="
echo "Java Code Verification Script"
echo "===================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

check_passed() {
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    echo -e "${GREEN}✓${NC} $1"
}

check_failed() {
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
    echo -e "${RED}✗${NC} $1"
}

check_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

echo "1. Checking file structure..."
echo "--------------------------------"

# Check new Kafka consumer files
if [ -f "java-websocket-server/src/main/java/com/demo/websocket/consumer/AuditTrailConsumer.java" ]; then
    check_passed "AuditTrailConsumer.java exists"
else
    check_failed "AuditTrailConsumer.java missing"
fi

if [ -f "java-websocket-server/src/main/java/com/demo/websocket/consumer/AnalyticsConsumer.java" ]; then
    check_passed "AnalyticsConsumer.java exists"
else
    check_failed "AnalyticsConsumer.java missing"
fi

if [ -f "java-websocket-server/src/main/java/com/demo/websocket/domain/AuditLog.java" ]; then
    check_passed "AuditLog.java exists"
else
    check_failed "AuditLog.java missing"
fi

if [ -f "java-websocket-server/src/main/java/com/demo/websocket/repository/AuditLogRepository.java" ]; then
    check_passed "AuditLogRepository.java exists"
else
    check_failed "AuditLogRepository.java missing"
fi

if [ -f "java-websocket-server/src/main/java/com/demo/websocket/service/StreamReplayService.java" ]; then
    check_passed "StreamReplayService.java exists"
else
    check_failed "StreamReplayService.java missing"
fi

echo ""
echo "2. Checking Kafka integration in existing files..."
echo "--------------------------------"

# Check ChatOrchestrator
if grep -q "EventPublisher" "java-websocket-server/src/main/java/com/demo/websocket/infrastructure/ChatOrchestrator.java"; then
    check_passed "ChatOrchestrator has EventPublisher integration"
else
    check_failed "ChatOrchestrator missing EventPublisher"
fi

# Check RecoveryService
if grep -q "EventPublisher" "java-websocket-server/src/main/java/com/demo/websocket/infrastructure/RecoveryService.java"; then
    check_passed "RecoveryService has EventPublisher integration"
else
    check_failed "RecoveryService missing EventPublisher"
fi

echo ""
echo "3. Checking Java syntax (basic)..."
echo "--------------------------------"

# Check for basic syntax errors in new files
for file in \
    "java-websocket-server/src/main/java/com/demo/websocket/consumer/AuditTrailConsumer.java" \
    "java-websocket-server/src/main/java/com/demo/websocket/consumer/AnalyticsConsumer.java" \
    "java-websocket-server/src/main/java/com/demo/websocket/domain/AuditLog.java" \
    "java-websocket-server/src/main/java/com/demo/websocket/repository/AuditLogRepository.java" \
    "java-websocket-server/src/main/java/com/demo/websocket/service/StreamReplayService.java"
do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        
        # Check package declaration
        if grep -q "^package com.demo.websocket" "$file"; then
            check_passed "$filename: package declaration OK"
        else
            check_failed "$filename: missing/incorrect package declaration"
        fi
        
        # Check class declaration
        if grep -q "public class\|public interface" "$file"; then
            check_passed "$filename: class/interface declaration OK"
        else
            check_failed "$filename: missing class/interface declaration"
        fi
        
        # Check proper imports
        if grep -q "^import" "$file"; then
            check_passed "$filename: has import statements"
        else
            check_warning "$filename: no imports (may be OK if simple class)"
        fi
        
        # Check for matching braces (simple check)
        open_braces=$(grep -o "{" "$file" | wc -l)
        close_braces=$(grep -o "}" "$file" | wc -l)
        if [ "$open_braces" -eq "$close_braces" ]; then
            check_passed "$filename: braces balanced ($open_braces opening, $close_braces closing)"
        else
            check_failed "$filename: braces unbalanced ($open_braces opening, $close_braces closing)"
        fi
    fi
done

echo ""
echo "4. Checking dependencies in pom.xml..."
echo "--------------------------------"

if grep -q "spring-kafka" "java-websocket-server/pom.xml"; then
    check_passed "Spring Kafka dependency present"
else
    check_failed "Spring Kafka dependency missing"
fi

if grep -q "spring-boot-starter-data-jpa" "java-websocket-server/pom.xml"; then
    check_passed "Spring Data JPA dependency present"
else
    check_failed "Spring Data JPA dependency missing"
fi

if grep -q "jackson-databind" "java-websocket-server/pom.xml"; then
    check_passed "Jackson dependency present"
else
    check_failed "Jackson dependency missing"
fi

if grep -q "lombok" "java-websocket-server/pom.xml"; then
    check_passed "Lombok dependency present"
else
    check_failed "Lombok dependency missing"
fi

echo ""
echo "5. Checking documentation..."
echo "--------------------------------"

if [ -f "docs/KAFKA_MULTI_NODE_ARCHITECTURE.md" ]; then
    check_passed "KAFKA_MULTI_NODE_ARCHITECTURE.md exists"
else
    check_failed "KAFKA_MULTI_NODE_ARCHITECTURE.md missing"
fi

if [ -f "docs/KAFKA_USAGE_GUIDE.md" ]; then
    check_passed "KAFKA_USAGE_GUIDE.md exists"
else
    check_failed "KAFKA_USAGE_GUIDE.md missing"
fi

if [ -f "docs/KAFKA_SUMMARY.md" ]; then
    check_passed "KAFKA_SUMMARY.md exists"
else
    check_failed "KAFKA_SUMMARY.md missing"
fi

if [ -f "docs/README.md" ]; then
    check_passed "docs/README.md exists"
else
    check_failed "docs/README.md missing"
fi

if [ -f "FIXES_SUMMARY.md" ]; then
    check_passed "FIXES_SUMMARY.md exists"
else
    check_failed "FIXES_SUMMARY.md missing"
fi

echo ""
echo "6. Checking UI fix..."
echo "--------------------------------"

if grep -q "existingMessage.content" "frontend/src/hooks/useChat.js"; then
    check_passed "UI content accumulation fix applied"
else
    check_failed "UI fix not applied"
fi

echo ""
echo "===================================="
echo "VERIFICATION SUMMARY"
echo "===================================="
echo -e "Total checks: ${YELLOW}$TOTAL_CHECKS${NC}"
echo -e "Passed: ${GREEN}$PASSED_CHECKS${NC}"
echo -e "Failed: ${RED}$FAILED_CHECKS${NC}"
echo ""

if [ $FAILED_CHECKS -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Run: docker-compose build java-websocket"
    echo "2. Run: docker-compose up -d"
    echo "3. Check logs: docker logs demo-java-websocket | grep 'Kafka'"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Some checks failed. Please review the errors above.${NC}"
    echo ""
    exit 1
fi
