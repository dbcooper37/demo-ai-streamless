#!/bin/bash

# Deployment script for Kafka Snappy Fix
# Tự động deploy fix cho lỗi Kafka Snappy compression

set -e

echo "=========================================="
echo "🔧 Kafka Snappy Fix Deployment"
echo "=========================================="
echo ""

# Kiểm tra xem đang dùng giải pháp nào
echo "📋 Available solutions:"
echo "  1. Use GZIP compression (Alpine base image) - CURRENT"
echo "  2. Use Debian base image (supports Snappy)"
echo ""

read -p "Choose solution (1 or 2) [default: 1]: " SOLUTION
SOLUTION=${SOLUTION:-1}

if [ "$SOLUTION" = "1" ]; then
    echo ""
    echo "✅ Solution 1: Using GZIP compression with Alpine"
    echo "   - Smaller image size (~150MB)"
    echo "   - Good compression (~50-60%)"
    echo "   - Already configured in application.yml"
    echo ""
    
    # Rebuild và restart
    echo "🔨 Rebuilding Java WebSocket server..."
    docker compose build --no-cache java-websocket-server
    
    echo "🚀 Starting services..."
    docker compose up -d java-websocket-server
    
elif [ "$SOLUTION" = "2" ]; then
    echo ""
    echo "✅ Solution 2: Using Debian base image"
    echo "   - Larger image size (~250MB)"
    echo "   - Supports Snappy compression"
    echo "   - Full glibc support"
    echo ""
    
    # Backup current Dockerfile
    if [ ! -f "java-websocket-server/Dockerfile.alpine.backup" ]; then
        echo "📦 Backing up current Dockerfile..."
        cp java-websocket-server/Dockerfile java-websocket-server/Dockerfile.alpine.backup
    fi
    
    # Swap to Debian Dockerfile
    echo "🔄 Switching to Debian Dockerfile..."
    cp java-websocket-server/Dockerfile.debian java-websocket-server/Dockerfile
    
    # Optional: Change compression back to snappy
    read -p "Do you want to change compression back to 'snappy'? (y/N): " USE_SNAPPY
    if [ "$USE_SNAPPY" = "y" ] || [ "$USE_SNAPPY" = "Y" ]; then
        echo "⚙️  Updating application.yml to use snappy compression..."
        sed -i.bak 's/compression-type: gzip/compression-type: snappy/g' \
            java-websocket-server/src/main/resources/application.yml
        echo "   ✅ Changed to snappy compression"
    fi
    
    # Rebuild với Debian image
    echo "🔨 Rebuilding with Debian base image..."
    docker compose build --no-cache java-websocket-server
    
    echo "🚀 Starting services..."
    docker compose up -d java-websocket-server
    
else
    echo "❌ Invalid solution choice"
    exit 1
fi

echo ""
echo "⏳ Waiting for service to start (10 seconds)..."
sleep 10

echo ""
echo "🔍 Checking service status..."
docker compose ps java-websocket-server

echo ""
echo "📋 Recent logs:"
docker compose logs --tail=30 java-websocket-server | grep -v "DEBUG" | tail -20

echo ""
echo "✅ Testing for Snappy errors..."
if docker compose logs java-websocket-server | grep -i "snappy" | grep -i "error" > /dev/null; then
    echo "   ❌ Still seeing Snappy errors - check logs"
    echo ""
    echo "   View full logs with:"
    echo "   docker compose logs -f java-websocket-server"
else
    echo "   ✅ No Snappy errors found!"
fi

echo ""
echo "=========================================="
echo "✅ Deployment completed!"
echo "=========================================="
echo ""
echo "📝 Next steps:"
echo "   1. Monitor logs: docker compose logs -f java-websocket-server"
echo "   2. Test WebSocket: Open http://localhost:3000"
echo "   3. Check health: curl http://localhost:8080/actuator/health"
echo ""
echo "📚 Documentation:"
echo "   - KAFKA_SNAPPY_FIX.md - Detailed explanation"
echo "   - TEST_CHECKLIST.md - Testing guide"
echo ""

# Optional: Run health check
read -p "Run health check now? (y/N): " RUN_HEALTH
if [ "$RUN_HEALTH" = "y" ] || [ "$RUN_HEALTH" = "Y" ]; then
    echo ""
    echo "🏥 Running health check..."
    curl -s http://localhost:8080/actuator/health | jq '.' || \
    curl -s http://localhost:8080/actuator/health
    echo ""
fi

echo ""
echo "✨ All done! Happy coding! 🚀"
