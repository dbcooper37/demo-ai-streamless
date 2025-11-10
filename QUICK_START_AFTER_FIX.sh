#!/bin/bash

echo "=========================================="
echo "🚀 Quick Start - After Kafka Snappy Fix"
echo "=========================================="
echo ""

# Start all services
echo "📦 Starting all services..."
docker compose up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 15

echo ""
echo "🔍 Checking service status..."
docker compose ps

echo ""
echo "✅ Testing Java WebSocket Server..."
curl -s http://localhost:8080/actuator/health | jq '.status' || echo "Java service starting..."

echo ""
echo "✅ Testing Python AI Service..."
curl -s http://localhost:8000/health | jq '.status' || echo "Python service starting..."

echo ""
echo "📋 Quick health check..."
echo "   Java WebSocket: http://localhost:8080/actuator/health"
echo "   Python AI: http://localhost:8000/health"
echo "   Frontend: http://localhost:3000"
echo ""

echo "🔍 Checking for Kafka Snappy errors..."
if docker compose logs java-websocket-server | grep -i "snappy" | grep -i "error" > /dev/null 2>&1; then
    echo "   ⚠️  Found Snappy errors - may need to rebuild"
    echo "   Run: ./DEPLOY_KAFKA_FIX.sh"
else
    echo "   ✅ No Snappy errors!"
fi

echo ""
echo "=========================================="
echo "✅ All services started!"
echo "=========================================="
echo ""
echo "🌐 Open your browser:"
echo "   http://localhost:3000"
echo ""
echo "📊 Monitor logs:"
echo "   docker compose logs -f"
echo ""
echo "🛑 Stop services:"
echo "   docker compose down"
echo ""
