# 🚀 Quick Start Guide - Full Stack with Kafka & Monitoring

This guide helps you get the complete system running with all enterprise features enabled.

---

## 📋 Prerequisites

- Docker & Docker Compose installed
- At least 4GB RAM available
- Ports available: 3000, 8000, 8080, 6379, 9090, 9092, 3001, 8090

---

## 🎯 Option 1: Basic Setup (No Kafka)

**Perfect for:** Development, testing, learning

### Step 1: Start Basic Services

```bash
# Use the original docker-compose.yml
docker-compose up --build
```

### Step 2: Access the Application

- **Frontend:** http://localhost:3000
- **Python API:** http://localhost:8000
- **WebSocket Server:** ws://localhost:8080/ws/chat
- **Health Check:** http://localhost:8080/actuator/health

### Step 3: Test Basic Functionality

1. Open http://localhost:3000
2. Send a message
3. Watch AI streaming response
4. Reload page to see persistent history

**Note:** Uses default JWT token for development.

---

## 🎯 Option 2: Full Stack (Kafka + Monitoring)

**Perfect for:** Production simulation, advanced features, full monitoring

### Step 1: Setup Environment

```bash
# Copy environment template
cp .env.example .env

# Edit .env and set JWT secret
nano .env
```

**Minimal .env:**
```bash
JWT_SECRET="your-generated-secret-minimum-256-bits"
KAFKA_ENABLED=true
GRAFANA_PASSWORD=admin
```

**Generate JWT Secret:**
```bash
openssl rand -base64 32
```

### Step 2: Start All Services

```bash
# Use the full docker-compose with Kafka and monitoring
docker-compose -f docker-compose.full.yml up --build
```

**Wait 2-3 minutes** for all services to start. Watch for:
- ✅ Zookeeper is healthy
- ✅ Kafka is healthy
- ✅ Redis is healthy
- ✅ Java WebSocket server connected to Kafka

### Step 3: Verify Services

```bash
# Check all services are running
docker-compose -f docker-compose.full.yml ps

# Expected output: All services "Up (healthy)"
```

### Step 4: Access All Endpoints

| Service | URL | Credentials |
|---------|-----|-------------|
| **Frontend** | http://localhost:3000 | - |
| **Python API** | http://localhost:8000 | - |
| **WebSocket** | ws://localhost:8080/ws/chat | JWT required |
| **Health Check** | http://localhost:8080/actuator/health | - |
| **Prometheus** | http://localhost:9090 | - |
| **Grafana** | http://localhost:3001 | admin / admin |
| **Kafka UI** | http://localhost:8090 | - |

---

## 🎨 Grafana Dashboard Setup

### Step 1: Login to Grafana

1. Open http://localhost:3001
2. Login with `admin` / `admin` (or your password from .env)
3. Skip password change (or set new password)

### Step 2: Import Dashboard

1. Click "+" → "Import dashboard"
2. Upload file: `monitoring/grafana/dashboards/websocket-dashboard.json`
3. Select Prometheus data source
4. Click "Import"

### Step 3: View Metrics

You should see:
- Active WebSocket Connections
- Active Streaming Sessions
- L1 and L2 Cache Hit Rates
- Stream Duration (Latency)
- Connection Rates
- Error Rates

**Tip:** Set auto-refresh to 10s in top-right corner.

---

## 🧪 Testing Features

### Test 1: Basic Streaming

```bash
# Send a message via API
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test123",
    "message": "Hello, tell me about streaming",
    "user_id": "user1"
  }'
```

Watch the streaming response in the frontend.

### Test 2: JWT Authentication

**Generate token (for testing):**

Create a simple token generator script:

```bash
# token-generator.sh
curl -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test"}'
```

**Or use the development token:**
```javascript
// In frontend
const token = "dev-token";  // Accepted in development mode
```

### Test 3: Cache Performance

```bash
# Monitor cache metrics
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/cache.hits | jq'

# Or check Grafana dashboard for cache hit rates
```

### Test 4: Kafka Events

```bash
# View Kafka UI
open http://localhost:8090

# Navigate to Topics → stream-events
# You should see events: SESSION_STARTED, CHUNK_RECEIVED, STREAM_COMPLETED
```

### Test 5: Recovery Mechanism

1. Start a long streaming conversation
2. Refresh the page mid-stream
3. Watch automatic recovery with missing chunks

### Test 6: Prometheus Queries

Open http://localhost:9090 and try these queries:

```promql
# Active connections
websocket_active_connections

# Cache hit rate
rate(cache_hits_total{level="L1"}[5m]) / 
  (rate(cache_hits_total{level="L1"}[5m]) + rate(cache_misses_total{level="L1"}[5m])) * 100

# Stream latency p95
histogram_quantile(0.95, rate(stream_duration_seconds_bucket[5m]))

# Error rate
rate(errors_total[5m])
```

---

## 🔧 Troubleshooting

### Issue: Services not starting

```bash
# Check logs
docker-compose -f docker-compose.full.yml logs

# Restart specific service
docker-compose -f docker-compose.full.yml restart java-websocket

# Full restart
docker-compose -f docker-compose.full.yml down
docker-compose -f docker-compose.full.yml up --build
```

### Issue: "Authentication failed"

**Solution:** Check JWT configuration

```bash
# View Java logs
docker logs demo-java-websocket | grep JWT

# Verify environment variable is set
docker exec demo-java-websocket env | grep JWT_SECRET
```

### Issue: Kafka connection errors

```bash
# Check Kafka is healthy
docker exec demo-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# View Kafka logs
docker logs demo-kafka

# Restart Kafka stack
docker-compose -f docker-compose.full.yml restart zookeeper kafka
```

### Issue: High memory usage

**Solution:** Reduce cache sizes

```bash
# Add to .env
CACHE_L1_MAX_SIZE=1000
JAVA_OPTS=-Xms256m -Xmx512m

# Restart
docker-compose -f docker-compose.full.yml restart java-websocket
```

### Issue: Grafana dashboard not showing data

**Check Prometheus is scraping:**

```bash
# Open Prometheus targets
open http://localhost:9090/targets

# All targets should be "UP"
```

**Check Grafana data source:**

1. Open Grafana → Configuration → Data Sources
2. Click "Prometheus"
3. Click "Test" button at bottom
4. Should show "Data source is working"

---

## 📊 Monitoring Best Practices

### 1. Set Up Alerts

Create `monitoring/alerts.yml`:

```yaml
groups:
  - name: websocket_alerts
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: rate(errors_total[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          
      - alert: LowCacheHitRate
        expr: rate(cache_hits_total[5m]) / (rate(cache_hits_total[5m]) + rate(cache_misses_total[5m])) < 0.7
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Cache hit rate below 70%"
```

### 2. Log Aggregation

View logs in real-time:

```bash
# All logs
docker-compose -f docker-compose.full.yml logs -f

# Specific service
docker-compose -f docker-compose.full.yml logs -f java-websocket

# Filter for errors
docker-compose -f docker-compose.full.yml logs java-websocket | grep ERROR
```

### 3. Performance Baselines

After running load tests, document:

| Metric | Baseline | Alert Threshold |
|--------|----------|----------------|
| Active Connections | 100-500 | > 1000 |
| L1 Cache Hit Rate | > 95% | < 80% |
| Stream Latency p95 | < 100ms | > 500ms |
| Error Rate | < 1/min | > 10/min |

---

## 🎯 Production Deployment

### Pre-Production Checklist

- [ ] Set strong JWT_SECRET (minimum 256 bits)
- [ ] Set Redis password
- [ ] Enable Kafka (KAFKA_ENABLED=true)
- [ ] Configure proper log retention
- [ ] Set up Prometheus scraping
- [ ] Configure Grafana alerts
- [ ] Test disaster recovery
- [ ] Document incident response
- [ ] Load test with expected traffic
- [ ] Security audit completed

### Docker Compose for Production

Create `docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  java-websocket:
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JWT_SECRET=${JWT_SECRET}
      - REDIS_PASSWORD=${REDIS_PASSWORD}
      - KAFKA_ENABLED=true
      - LOG_LEVEL=WARN
      - JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '2'
          memory: 4G
```

### Environment Variables for Production

```bash
# .env.production
JWT_SECRET="$(openssl rand -base64 32)"
REDIS_PASSWORD="$(openssl rand -base64 16)"
KAFKA_ENABLED=true
LOG_LEVEL=WARN
ENVIRONMENT=production

# Cache settings
CACHE_L1_MAX_SIZE=50000
CACHE_L2_TTL=30

# Stream settings
STREAM_MAX_PENDING_CHUNKS=5000

# Monitoring
GRAFANA_PASSWORD="$(openssl rand -base64 12)"
```

---

## 📚 Next Steps

### Learn More

1. **Architecture:** Read `IMPL_v2.md` (comprehensive design document)
2. **Features:** See `IMPLEMENTATION_SUMMARY.md`
3. **Customization:** Check `CUSTOMIZATION_GUIDE.md`
4. **Migration:** Review `MIGRATION_GUIDE.md`

### Advanced Topics

1. **Multi-Node Deployment:**
   - Read `README.multi-node.md`
   - Use `docker-compose.multi-node.yml`

2. **Custom Integrations:**
   - Add your own AI provider
   - Integrate with your auth system
   - Connect to your database

3. **Performance Tuning:**
   - Load test with k6 or JMeter
   - Optimize cache sizes
   - Tune JVM parameters

---

## 🎉 Success Indicators

Your system is working correctly if:

✅ Frontend loads at http://localhost:3000
✅ Messages stream character by character
✅ Page reload preserves history
✅ Metrics appear in Grafana dashboard
✅ Kafka events visible in Kafka UI (if enabled)
✅ Cache hit rate > 90% in Grafana
✅ Health check returns "UP" status
✅ No errors in `docker-compose logs`

---

## 🆘 Getting Help

### Check Status

```bash
# Health checks
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health

# View metrics
curl http://localhost:8080/actuator/metrics

# Check logs
docker-compose -f docker-compose.full.yml logs --tail=100
```

### Common Commands

```bash
# Stop all services
docker-compose -f docker-compose.full.yml down

# Stop and remove volumes (fresh start)
docker-compose -f docker-compose.full.yml down -v

# Restart single service
docker-compose -f docker-compose.full.yml restart java-websocket

# View resource usage
docker stats

# Clean up
docker system prune -a
```

### Debug Mode

Enable debug logging:

```bash
# In .env
LOG_LEVEL=DEBUG

# Restart
docker-compose -f docker-compose.full.yml restart java-websocket
```

---

## 🎊 You're All Set!

The system is now running with:
- ⚡ Real-time streaming
- 🔐 JWT authentication
- 📊 Full monitoring with Grafana
- 📝 Event sourcing with Kafka
- 💾 Multi-level caching
- 🔄 Automatic recovery

**Enjoy your enterprise-grade AI streaming chat system!** 🚀
