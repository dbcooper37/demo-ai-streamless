# Migration Guide - IMPL_v2 Updates

## Overview
This guide helps you migrate from the basic implementation to the enterprise-grade implementation with IMPL_v2 features.

## Breaking Changes

### 1. WebSocket Connection - JWT Authentication Required

**Before:**
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/chat?session_id=123&user_id=user1');
```

**After:**
```javascript
const token = getJWTToken(); // Get JWT token from your auth system
const ws = new WebSocket(`ws://localhost:8080/ws/chat?session_id=123&user_id=user1&token=${token}`);
```

**Development Mode:**
If you don't have a token, the system will accept "dev-token" in development (will be validated but allowed).

### 2. Application Configuration

**Required Updates to `application.yml`:**

```yaml
# NEW: Security configuration (REQUIRED)
security:
  jwt:
    secret: "your-secret-key-minimum-256-bits"  # CHANGE THIS!
    expiration-ms: 3600000

# NEW: Cache configuration (OPTIONAL - has defaults)
cache:
  caffeine:
    max-size: 10000
    expire-after-write-minutes: 5
    expire-after-access-minutes: 2
  redis:
    default-ttl-minutes: 10

# NEW: Kafka configuration (OPTIONAL - disabled by default)
spring:
  kafka:
    enabled: false  # Set to true to enable event sourcing
    bootstrap-servers: localhost:9092
```

### 3. Environment Variables

**New Required Variables:**
```bash
# Production JWT Secret (REQUIRED in production)
export JWT_SECRET="your-production-secret-key-change-this-minimum-256-bits"

# Optional: Enable Kafka
export KAFKA_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Optional: Cache tuning
export CACHE_L1_MAX_SIZE=10000
export CACHE_L2_TTL=10
```

### 4. Docker Compose Updates

**Update `docker-compose.yml` to include JWT secret:**

```yaml
services:
  java-websocket-server:
    environment:
      - JWT_SECRET=${JWT_SECRET:-default-secret-key-change-this-in-production-minimum-256-bits}
      - KAFKA_ENABLED=${KAFKA_ENABLED:-false}
      - KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}
```

**Optional: Add Kafka to `docker-compose.yml`:**

```yaml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  java-websocket-server:
    depends_on:
      - redis
      - kafka  # Add this dependency
    environment:
      - KAFKA_ENABLED=true
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

## New Features Available

### 1. Metrics and Monitoring

**Prometheus Metrics Endpoint:**
```
GET http://localhost:8080/actuator/prometheus
```

**Health Check:**
```
GET http://localhost:8080/actuator/health
```

**Available Metrics:**
- `websocket_active_connections` - Current connections
- `stream_active_sessions` - Ongoing streams
- `cache_hits_total{level="L1|L2"}` - Cache performance
- `stream_duration_seconds` - Stream latency
- `authentication_attempts_total` - Auth stats

### 2. Enhanced Error Handling

The WebSocket handler now provides detailed error responses:

```javascript
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  switch(data.type) {
    case 'error':
      console.error('Error:', data.error);
      // Handle authentication failure, security errors, etc.
      break;
    case 'welcome':
      console.log('Connected:', data.sessionId);
      break;
    case 'chunk':
      // Handle streaming chunk
      break;
    case 'complete':
      // Handle stream completion
      break;
    case 'recovery_status':
      // Handle recovery status
      break;
  }
};
```

### 3. Client-Side Token Management

**Example JWT Token Generation (Server-side):**

```java
import com.demo.websocket.service.SecurityValidator;

@RestController
public class AuthController {
    
    private final SecurityValidator securityValidator;
    
    @PostMapping("/api/auth/token")
    public TokenResponse generateToken(@RequestBody AuthRequest request) {
        // Validate user credentials
        String userId = authenticateUser(request);
        
        // Generate JWT token
        String token = securityValidator.generateToken(userId);
        
        return new TokenResponse(token);
    }
}
```

**Client-side token usage:**

```javascript
// 1. Get token from auth endpoint
const response = await fetch('http://localhost:8080/api/auth/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'user', password: 'pass' })
});

const { token } = await response.json();

// 2. Connect with token
const ws = new WebSocket(
  `ws://localhost:8080/ws/chat?session_id=${sessionId}&user_id=${userId}&token=${token}`
);
```

## Testing the Migration

### 1. Test Basic Connectivity (Development Mode)

```bash
# Use development token
curl -i -N -H "Connection: Upgrade" \
     -H "Upgrade: websocket" \
     -H "Sec-WebSocket-Version: 13" \
     -H "Sec-WebSocket-Key: test" \
     "http://localhost:8080/ws/chat?session_id=test&user_id=testuser&token=dev-token"
```

### 2. Test Metrics Endpoint

```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus
```

### 3. Test Health Check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    ...
  }
}
```

### 4. Verify Cache Performance

Monitor cache hit rates in logs:
```bash
docker logs java-websocket-server-1 | grep "L1 Cache Stats"
```

Expected output:
```
L1 Cache Stats - Size: 150, Hits: 1200, Misses: 45, Hit Rate: 96.39%
```

## Performance Tuning

### 1. Cache Configuration

**For high-traffic scenarios:**
```yaml
cache:
  caffeine:
    max-size: 50000  # Increase L1 cache size
    expire-after-write-minutes: 10  # Longer TTL
    expire-after-access-minutes: 5
  redis:
    default-ttl-minutes: 30  # Longer Redis TTL
```

**For low-memory scenarios:**
```yaml
cache:
  caffeine:
    max-size: 1000  # Reduce L1 cache size
    expire-after-write-minutes: 2  # Shorter TTL
    expire-after-access-minutes: 1
```

### 2. Stream Backpressure

**For high-throughput:**
```yaml
stream:
  max-pending-chunks: 5000  # Higher threshold
  backpressure-delay-ms: 5  # Lower delay
```

**For low-resource scenarios:**
```yaml
stream:
  max-pending-chunks: 500  # Lower threshold
  backpressure-delay-ms: 20  # Higher delay
```

### 3. Redis Connection Pool

**For high-concurrency:**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50  # More connections
          max-idle: 20
          min-idle: 10
```

## Rollback Plan

If you need to rollback to the previous implementation:

### 1. Disable New Features

```yaml
# In application.yml
security:
  jwt:
    secret: "dev-secret"  # Use simple secret

kafka:
  enabled: false  # Disable Kafka

# In ChatWebSocketHandler
# Comment out securityValidator.validateToken() check temporarily
```

### 2. Use Git to Revert

```bash
# Revert to previous commit
git log --oneline  # Find commit before IMPL_v2 changes
git revert <commit-hash>

# Or create a rollback branch
git checkout -b rollback-impl-v2
git revert HEAD~5..HEAD  # Revert last 5 commits
```

### 3. Remove New Dependencies

In `pom.xml`, remove:
- Spring Kafka dependencies
- Caffeine cache
- Micrometer/Prometheus
- JJWT dependencies

Then rebuild:
```bash
mvn clean install -DskipTests
```

## Support and Troubleshooting

### Common Issues

#### Issue 1: "Authentication failed"
**Cause:** Invalid or missing JWT token
**Solution:**
- Check token is included in WebSocket URL
- Verify JWT_SECRET is consistent
- Use "dev-token" for local development
- Check token expiration

#### Issue 2: "Connection refused" to Redis
**Cause:** Redis not running or wrong configuration
**Solution:**
```bash
# Check Redis is running
docker ps | grep redis

# Test Redis connection
docker exec -it redis-container redis-cli ping
# Should return: PONG

# Verify environment variables
echo $SPRING_DATA_REDIS_HOST
echo $SPRING_DATA_REDIS_PORT
```

#### Issue 3: High memory usage
**Cause:** L1 cache too large
**Solution:**
```yaml
cache:
  caffeine:
    max-size: 1000  # Reduce from 10000
```

#### Issue 4: Kafka connection errors (if enabled)
**Cause:** Kafka not available
**Solution:**
```bash
# Disable Kafka temporarily
export KAFKA_ENABLED=false

# Or fix Kafka connection
docker ps | grep kafka
docker logs kafka-container
```

### Getting Help

1. **Check Logs:**
   ```bash
   docker logs java-websocket-server-1 --tail 100 -f
   ```

2. **Check Metrics:**
   ```bash
   curl http://localhost:8080/actuator/metrics/errors
   curl http://localhost:8080/actuator/metrics/websocket.connections
   ```

3. **Review Documentation:**
   - IMPL_v2.md - Detailed architecture
   - IMPLEMENTATION_SUMMARY.md - Feature overview
   - README.md - Basic usage

## Conclusion

The migration adds enterprise-grade features while maintaining backward compatibility (with JWT auth requirement). Follow this guide to ensure a smooth transition.

**Recommended Migration Steps:**
1. ✅ Update pom.xml dependencies (already done)
2. ✅ Update application.yml configuration
3. ✅ Set JWT_SECRET environment variable
4. ✅ Update Docker Compose with new env vars
5. ✅ Test authentication with dev-token
6. ✅ Monitor metrics and logs
7. ✅ Gradually enable Kafka if needed
8. ✅ Load test before production deployment

**Production Checklist:**
- [ ] Set strong JWT secret
- [ ] Enable HTTPS/WSS
- [ ] Configure Prometheus scraping
- [ ] Set up log aggregation
- [ ] Configure alerts
- [ ] Load test with production load
- [ ] Document incident response
- [ ] Train operations team
