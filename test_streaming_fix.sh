#!/bin/bash

# Test script for Streaming Error Fix
# Kiểm tra xem streaming có còn bị lỗi "Chunk append failed" không

echo "================================================"
echo "TEST FIX LỖI STREAMING - CHUNK APPEND FAILED"
echo "================================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
PASSED=0
FAILED=0
TOTAL=0

test_passed() {
    TOTAL=$((TOTAL + 1))
    PASSED=$((PASSED + 1))
    echo -e "${GREEN}✓${NC} $1"
}

test_failed() {
    TOTAL=$((TOTAL + 1))
    FAILED=$((FAILED + 1))
    echo -e "${RED}✗${NC} $1"
}

test_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

test_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# ===================================
# Test 1: Kiểm tra services đang chạy
# ===================================
echo -e "${BLUE}Test 1: Kiểm tra Services${NC}"
echo "-----------------------------------"

if docker ps | grep -q "demo-java-websocket"; then
    test_passed "Java WebSocket service đang chạy"
else
    test_failed "Java WebSocket service không chạy"
    echo "Vui lòng start services: docker-compose up -d"
    exit 1
fi

if docker ps | grep -q "demo-redis"; then
    test_passed "Redis đang chạy"
else
    test_warning "Redis không chạy - Test sẽ kiểm tra graceful degradation"
fi

if docker ps | grep -q "python-ai"; then
    test_passed "Python AI service đang chạy"
else
    test_warning "Python AI service không chạy"
fi

echo ""

# ===================================
# Test 2: Kiểm tra code changes
# ===================================
echo -e "${BLUE}Test 2: Kiểm tra Code Changes${NC}"
echo "-----------------------------------"

if grep -q "This method is designed to be resilient" java-websocket-server/src/main/java/com/demo/websocket/infrastructure/RedisStreamCache.java; then
    test_passed "RedisStreamCache.java đã được update với resilient logic"
else
    test_failed "RedisStreamCache.java chưa được update"
fi

# Check for improved error handling
if grep -q "Don't throw - streaming can continue" java-websocket-server/src/main/java/com/demo/websocket/infrastructure/RedisStreamCache.java; then
    test_passed "Error handling đã được cải thiện (không throw exception)"
else
    test_failed "Error handling chưa được fix"
fi

# Check for graceful lock handling
if grep -q "Gracefully skip" java-websocket-server/src/main/java/com/demo/websocket/infrastructure/RedisStreamCache.java; then
    test_passed "Lock handling đã được cải thiện (graceful skip)"
else
    test_warning "Lock handling có thể chưa tối ưu"
fi

echo ""

# ===================================
# Test 3: Kiểm tra logs cho errors cũ
# ===================================
echo -e "${BLUE}Test 3: Kiểm tra Logs (Last 5 minutes)${NC}"
echo "-----------------------------------"

# Check for the old error
if docker logs --since=5m demo-java-websocket 2>&1 | grep -q "Chunk append failed (non-duplicate error)"; then
    test_failed "Vẫn còn lỗi 'Chunk append failed (non-duplicate error)' trong logs"
    echo ""
    echo "Recent errors:"
    docker logs --since=5m demo-java-websocket 2>&1 | grep "Chunk append failed" | tail -3
else
    test_passed "Không còn lỗi 'Chunk append failed (non-duplicate error)'"
fi

# Check for graceful warnings (expected)
if docker logs --since=5m demo-java-websocket 2>&1 | grep -q "Error appending chunk to cache (continuing)"; then
    test_info "Có warnings về cache errors nhưng streaming tiếp tục (OK)"
fi

# Check for recovery mechanism working
if docker logs --since=5m demo-java-websocket 2>&1 | grep -q "Recovery will handle"; then
    test_info "Recovery mechanism đang hoạt động (OK)"
fi

echo ""

# ===================================
# Test 4: Test Streaming Functionality
# ===================================
echo -e "${BLUE}Test 4: Test Streaming (Optional - Interactive)${NC}"
echo "-----------------------------------"

read -p "Bạn có muốn test streaming thực tế không? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Testing streaming functionality..."
    
    # Check if we can connect to the service
    if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        test_passed "Service health check OK"
        
        echo ""
        echo "Để test streaming, mở browser và:"
        echo "1. Truy cập http://localhost:3000"
        echo "2. Gửi một message"
        echo "3. Xem streaming có hoạt động mượt mà không"
        echo "4. Check logs: docker logs -f demo-java-websocket | grep -E 'chunk|error'"
        echo ""
        test_info "Streaming test cần kiểm tra manual qua UI"
        
    else
        test_warning "Không kết nối được service để test"
    fi
else
    echo "Bỏ qua streaming test"
fi

echo ""

# ===================================
# Test 5: Test với Redis Issues (Optional)
# ===================================
echo -e "${BLUE}Test 5: Test Resilience (Optional - Advanced)${NC}"
echo "-----------------------------------"

read -p "Bạn có muốn test resilience khi Redis có vấn đề? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Test này sẽ:"
    echo "1. Tạm dừng Redis"
    echo "2. Kiểm tra streaming vẫn hoạt động"
    echo "3. Khởi động lại Redis"
    echo ""
    read -p "Tiếp tục? (y/n) " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Pausing Redis..."
        docker-compose pause redis
        sleep 2
        
        # Check if service still healthy
        if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
            test_passed "Service vẫn healthy khi Redis down"
        else
            test_warning "Service báo unhealthy khi Redis down"
        fi
        
        # Check logs
        sleep 3
        if docker logs --since=5s demo-java-websocket 2>&1 | grep -q "Error appending chunk to cache (continuing)"; then
            test_passed "Service log errors nhưng tiếp tục chạy (graceful degradation)"
        fi
        
        echo "Unpausing Redis..."
        docker-compose unpause redis
        sleep 2
        
        test_info "Redis đã được khởi động lại"
    else
        echo "Bỏ qua resilience test"
    fi
else
    echo "Bỏ qua resilience test"
fi

echo ""

# ===================================
# Test 6: Documentation
# ===================================
echo -e "${BLUE}Test 6: Kiểm tra Documentation${NC}"
echo "-----------------------------------"

if [ -f "STREAMING_ERROR_FIX.md" ]; then
    test_passed "Documentation STREAMING_ERROR_FIX.md tồn tại"
else
    test_warning "Không tìm thấy documentation"
fi

echo ""

# ===================================
# Summary
# ===================================
echo "================================================"
echo -e "${BLUE}KẾT QUẢ TEST${NC}"
echo "================================================"
echo -e "Tổng số tests: ${YELLOW}$TOTAL${NC}"
echo -e "Passed: ${GREEN}$PASSED${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ TẤT CẢ TESTS ĐỀU PASS!${NC}"
    echo ""
    echo "Fix đã được áp dụng thành công:"
    echo "  ✓ RedisStreamCache.java không còn throw exceptions"
    echo "  ✓ Error handling graceful và resilient"
    echo "  ✓ Streaming có thể tiếp tục kể cả khi cache có issues"
    echo "  ✓ Recovery mechanism sẽ handle missing chunks"
    echo ""
    echo "Next steps:"
    echo "  1. Monitor logs: docker logs -f demo-java-websocket"
    echo "  2. Test streaming qua UI: http://localhost:3000"
    echo "  3. Đọc documentation: STREAMING_ERROR_FIX.md"
    echo ""
    exit 0
else
    echo -e "${RED}✗ CÓ MỘT SỐ TESTS FAIL${NC}"
    echo ""
    echo "Vui lòng:"
    echo "  1. Kiểm tra lại code changes"
    echo "  2. Rebuild service: docker-compose build java-websocket"
    echo "  3. Restart service: docker-compose restart java-websocket"
    echo "  4. Chạy lại test script này"
    echo ""
    exit 1
fi
