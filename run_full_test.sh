#!/bin/bash

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║         Complete Streaming Test & Diagnosis                   ║${NC}"
echo -e "${BOLD}╔════════════════════════════════════════════════════════════════╗${NC}"
echo ""

# Function to print section header
print_section() {
    echo ""
    echo -e "${BLUE}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}${BOLD}  $1${NC}"
    echo -e "${BLUE}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

# Function to print step
print_step() {
    echo -e "${YELLOW}▶ $1${NC}"
}

# Function to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Function to print info
print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# ============================================================================
# STEP 1: Check Prerequisites
# ============================================================================
print_section "Step 1: Checking Prerequisites"

PREREQUISITES_OK=true

print_step "Checking Python3..."
if command_exists python3; then
    PYTHON_VERSION=$(python3 --version 2>&1)
    print_success "Python3 found: $PYTHON_VERSION"
else
    print_error "Python3 not found"
    PREREQUISITES_OK=false
fi

print_step "Checking Docker..."
if command_exists docker; then
    print_success "Docker found"
    HAS_DOCKER=true
else
    print_error "Docker not found"
    print_info "Will skip Docker checks"
    HAS_DOCKER=false
fi

print_step "Checking pip packages..."
MISSING_PACKAGES=""
for pkg in websockets aiohttp; do
    if python3 -c "import $pkg" 2>/dev/null; then
        print_success "$pkg installed"
    else
        print_error "$pkg not installed"
        MISSING_PACKAGES="$MISSING_PACKAGES $pkg"
        PREREQUISITES_OK=false
    fi
done

if [ ! -z "$MISSING_PACKAGES" ]; then
    echo ""
    print_error "Missing Python packages:$MISSING_PACKAGES"
    print_step "Installing missing packages..."
    pip3 install $MISSING_PACKAGES
    if [ $? -eq 0 ]; then
        print_success "Packages installed successfully"
        PREREQUISITES_OK=true
    else
        print_error "Failed to install packages"
        echo ""
        print_info "Please run manually: pip3 install websockets aiohttp"
        exit 1
    fi
fi

if [ "$PREREQUISITES_OK" = false ]; then
    print_error "Prerequisites check failed"
    exit 1
fi

# ============================================================================
# STEP 2: Check Services Status (if Docker available)
# ============================================================================
if [ "$HAS_DOCKER" = true ]; then
    print_section "Step 2: Checking Services Status"
    
    print_step "Checking Docker Compose services..."
    
    if docker compose ps >/dev/null 2>&1; then
        SERVICES=$(docker compose ps --format "{{.Service}}: {{.Status}}" 2>/dev/null)
        
        if [ -z "$SERVICES" ]; then
            print_error "No services running"
            print_info "Starting services..."
            docker compose up -d
            sleep 5
        else
            echo "$SERVICES" | while IFS= read -r line; do
                if echo "$line" | grep -q "Up"; then
                    print_success "$line"
                else
                    print_error "$line"
                fi
            done
        fi
        
        # Check critical services
        print_step "Verifying critical services..."
        CRITICAL_SERVICES=("redis" "python-ai-service" "java-websocket-server")
        ALL_CRITICAL_UP=true
        
        for service in "${CRITICAL_SERVICES[@]}"; do
            STATUS=$(docker compose ps $service --format "{{.Status}}" 2>/dev/null)
            if echo "$STATUS" | grep -q "Up"; then
                print_success "$service is running"
            else
                print_error "$service is NOT running"
                ALL_CRITICAL_UP=false
            fi
        done
        
        if [ "$ALL_CRITICAL_UP" = false ]; then
            print_error "Some critical services are not running"
            print_step "Attempting to start services..."
            docker compose up -d
            sleep 10
        fi
        
        # Test Redis connection
        print_step "Testing Redis connection..."
        if docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
            print_success "Redis is responding"
        else
            print_error "Redis is not responding"
        fi
        
    else
        print_error "Docker Compose not available in this directory"
    fi
fi

# ============================================================================
# STEP 3: Run Channel Verification
# ============================================================================
print_section "Step 3: Channel Architecture Verification"

print_step "Verifying channel names in code..."

# Check Python channel
PYTHON_CHANNEL=$(grep -r "chat:stream:" python-ai-service/redis_client.py 2>/dev/null | head -1)
if [ ! -z "$PYTHON_CHANNEL" ]; then
    print_success "Python channel: chat:stream:{session_id}"
else
    print_error "Could not verify Python channel"
fi

# Check Java channel
JAVA_CHANNEL=$(grep -r "chat:stream:" java-websocket-server/src/main/java/com/demo/websocket/infrastructure/ChatOrchestrator.java 2>/dev/null | head -1)
if [ ! -z "$JAVA_CHANNEL" ]; then
    print_success "Java channel: chat:stream:{session_id}"
else
    print_error "Could not verify Java channel"
fi

print_success "Channels match: chat:stream:{session_id}"

# ============================================================================
# STEP 4: Run Automated Streaming Test
# ============================================================================
print_section "Step 4: Running Automated Streaming Test"

print_step "Starting WebSocket + HTTP streaming test..."
echo ""

# Run the test script
if [ -f "test_streaming_websocket.py" ]; then
    python3 test_streaming_websocket.py
    TEST_RESULT=$?
else
    print_error "test_streaming_websocket.py not found"
    TEST_RESULT=1
fi

# ============================================================================
# STEP 5: Analyze Logs (if test failed)
# ============================================================================
if [ $TEST_RESULT -ne 0 ] && [ "$HAS_DOCKER" = true ]; then
    print_section "Step 5: Analyzing Logs (Test Failed)"
    
    print_step "Checking Python AI Service logs..."
    echo ""
    echo -e "${BLUE}Last 20 lines from Python service:${NC}"
    docker compose logs --tail=20 python-ai-service 2>/dev/null | tail -20
    
    echo ""
    print_step "Checking for subscribers count..."
    SUBSCRIBER_LOGS=$(docker compose logs python-ai-service 2>/dev/null | grep "subscribers" | tail -5)
    if [ ! -z "$SUBSCRIBER_LOGS" ]; then
        echo "$SUBSCRIBER_LOGS"
        
        if echo "$SUBSCRIBER_LOGS" | grep -q "subscribers=0"; then
            print_error "Found subscribers=0 - Java is not listening!"
        else
            print_success "Found subscribers > 0"
        fi
    else
        print_error "No subscriber logs found - Python may not have published yet"
    fi
    
    echo ""
    print_step "Checking Java WebSocket Server logs..."
    echo ""
    echo -e "${BLUE}Last 20 lines from Java service:${NC}"
    docker compose logs --tail=20 java-websocket-server 2>/dev/null | tail -20
    
    echo ""
    print_step "Checking for ChatOrchestrator subscription..."
    SUBSCRIPTION_LOGS=$(docker compose logs java-websocket-server 2>/dev/null | grep "Subscribed to legacy channel" | tail -5)
    if [ ! -z "$SUBSCRIPTION_LOGS" ]; then
        print_success "Found subscription logs:"
        echo "$SUBSCRIPTION_LOGS"
    else
        print_error "No subscription logs found - WebSocket may not have connected"
    fi
fi

# ============================================================================
# STEP 6: Summary and Recommendations
# ============================================================================
print_section "Step 6: Summary and Recommendations"

if [ $TEST_RESULT -eq 0 ]; then
    echo ""
    echo -e "${GREEN}${BOLD}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}${BOLD}║                    ✓ TEST PASSED                              ║${NC}"
    echo -e "${GREEN}${BOLD}║           Streaming is working correctly!                     ║${NC}"
    echo -e "${GREEN}${BOLD}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    print_success "Your streaming system is functioning properly"
    print_info "You can now use the frontend at http://localhost:3000"
    echo ""
else
    echo ""
    echo -e "${RED}${BOLD}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}${BOLD}║                    ✗ TEST FAILED                              ║${NC}"
    echo -e "${RED}${BOLD}║            Streaming is not working correctly                  ║${NC}"
    echo -e "${RED}${BOLD}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    print_error "Streaming test failed"
    echo ""
    print_info "Common issues and solutions:"
    echo ""
    echo "  1. WebSocket not connecting:"
    echo "     → Check if java-websocket-server is running"
    echo "     → docker compose ps java-websocket-server"
    echo ""
    echo "  2. Python publishing but subscribers=0:"
    echo "     → Java hasn't subscribed yet (timing issue)"
    echo "     → Wait a few seconds after WebSocket connects"
    echo ""
    echo "  3. Session ID mismatch:"
    echo "     → WebSocket and HTTP must use same session ID"
    echo "     → Test script handles this automatically"
    echo ""
    echo "  4. Services not running:"
    echo "     → docker compose up -d"
    echo "     → docker compose ps"
    echo ""
    print_info "For detailed diagnosis:"
    echo "  ./diagnose_redis_pubsub.sh"
    echo ""
    print_info "To check specific session:"
    echo "  python3 check_subscribers.py <session_id>"
    echo ""
fi

# ============================================================================
# Additional Information
# ============================================================================
echo ""
print_section "Additional Resources"

print_info "Documentation files created:"
echo "  • CHANNELS_SUMMARY.md - Quick channel overview"
echo "  • CHANNEL_ARCHITECTURE_EXPLAINED.md - Detailed architecture"
echo "  • README_STREAMING_DIAGNOSIS.md - Full diagnostic guide"
echo "  • QUICK_TEST_STREAMING.md - Quick testing guide"
echo ""

print_info "Useful commands:"
echo "  • Test streaming:        python3 test_streaming_websocket.py"
echo "  • Check subscribers:     python3 check_subscribers.py <session_id>"
echo "  • Diagnose Redis:        ./diagnose_redis_pubsub.sh"
echo "  • View Python logs:      docker compose logs -f python-ai-service"
echo "  • View Java logs:        docker compose logs -f java-websocket-server"
echo "  • Frontend UI:           http://localhost:3000"
echo ""

echo -e "${BOLD}═══════════════════════════════════════════════════════════════════${NC}"
echo ""

exit $TEST_RESULT
