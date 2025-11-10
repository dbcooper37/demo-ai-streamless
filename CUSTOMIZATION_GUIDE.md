# 🎨 Customization Guide

This guide shows you how to customize the AI Streaming Chat system for your specific needs.

---

## Table of Contents

1. [Quick Customizations](#quick-customizations)
2. [Feature Toggles](#feature-toggles)
3. [Performance Tuning](#performance-tuning)
4. [Security Hardening](#security-hardening)
5. [Custom Integrations](#custom-integrations)
6. [Advanced Scenarios](#advanced-scenarios)

---

## Quick Customizations

### 1. Adjust Cache Sizes

**Small Memory Environment (< 2GB RAM):**
```yaml
# application.yml
cache:
  caffeine:
    max-size: 1000          # Reduce from 10000
    expire-after-write-minutes: 2
    expire-after-access-minutes: 1
  redis:
    default-ttl-minutes: 5
```

**High Traffic Environment (> 8GB RAM):**
```yaml
# application.yml
cache:
  caffeine:
    max-size: 50000         # Increase to 50k
    expire-after-write-minutes: 10
    expire-after-access-minutes: 5
  redis:
    default-ttl-minutes: 30
```

**Environment Variables:**
```bash
export CACHE_L1_MAX_SIZE=50000
export CACHE_L1_EXPIRE_WRITE=10
export CACHE_L1_EXPIRE_ACCESS=5
export CACHE_L2_TTL=30
```

---

### 2. Adjust Stream Backpressure

**High Throughput (Fast AI responses):**
```yaml
stream:
  max-pending-chunks: 5000      # Higher threshold
  backpressure-delay-ms: 5      # Lower delay
  recovery-timeout-minutes: 10
```

**Low Resource (Slow connections):**
```yaml
stream:
  max-pending-chunks: 500       # Lower threshold
  backpressure-delay-ms: 20     # Higher delay
  recovery-timeout-minutes: 3
```

---

### 3. Customize JWT Token Expiration

**Short-lived tokens (high security):**
```yaml
security:
  jwt:
    expiration-ms: 900000  # 15 minutes
```

**Long-lived tokens (convenience):**
```yaml
security:
  jwt:
    expiration-ms: 86400000  # 24 hours
```

**Custom token generation:**
```java
@Service
public class CustomTokenService {
    
    private final SecurityValidator securityValidator;
    
    public String generateCustomToken(String userId, Map<String, Object> claims) {
        return Jwts.builder()
            .subject(userId)
            .claims(claims)  // Add custom claims
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(secretKey)
            .compact();
    }
}
```

---

## Feature Toggles

### Enable/Disable Kafka

**Disable Kafka (default):**
```yaml
spring:
  kafka:
    enabled: false
```

**Enable Kafka:**
```yaml
spring:
  kafka:
    enabled: true
    bootstrap-servers: kafka:9092
    topics:
      chat-events: chat-events
      stream-events: stream-events
```

Or via environment:
```bash
export KAFKA_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

---

### Customize Kafka Topics

```yaml
kafka:
  topics:
    chat-events: my-chat-events          # Custom topic names
    stream-events: my-stream-events
    recovery-events: my-recovery-events  # Add new topics
```

**Publish to custom topic:**
```java
@Service
public class CustomEventPublisher extends EventPublisher {
    
    public void publishCustomEvent(String topic, Object event) {
        kafkaTemplate.send(topic, event);
    }
}
```

---

### Add Custom Metrics

**Create custom metric:**
```java
@Service
public class MyService {
    
    private final MetricsService metricsService;
    
    public void processRequest() {
        Timer.Sample sample = metricsService.startTimer();
        
        try {
            // Your logic here
            metricsService.incrementCounter("my_custom_metric", 
                Tags.of("type", "success"));
        } finally {
            metricsService.stopTimer(sample, "my_operation_duration");
        }
    }
}
```

**Query in Prometheus:**
```promql
rate(my_custom_metric_total[5m])
histogram_quantile(0.95, rate(my_operation_duration_bucket[5m]))
```

---

## Performance Tuning

### 1. Redis Connection Pool

**High Concurrency:**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50      # More connections
          max-idle: 20
          min-idle: 10
          max-wait: 5000ms
```

**Low Concurrency:**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 10      # Fewer connections
          max-idle: 5
          min-idle: 2
          max-wait: 2000ms
```

---

### 2. JVM Tuning

**In docker-compose.yml:**

**Small deployment (< 2GB):**
```yaml
java-websocket:
  environment:
    - JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
```

**Medium deployment (2-4GB):**
```yaml
java-websocket:
  environment:
    - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

**Large deployment (8GB+):**
```yaml
java-websocket:
  environment:
    - JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled
```

---

### 3. Kafka Performance Tuning

**High Throughput:**
```yaml
spring:
  kafka:
    producer:
      batch-size: 32768        # 32KB batches
      linger-ms: 20            # Wait 20ms
      compression-type: lz4    # Better compression
      buffer-memory: 67108864  # 64MB buffer
```

**Low Latency:**
```yaml
spring:
  kafka:
    producer:
      batch-size: 8192         # Smaller batches
      linger-ms: 0             # Don't wait
      compression-type: snappy
      buffer-memory: 33554432  # 32MB buffer
```

---

## Security Hardening

### 1. Strong JWT Configuration

**Generate strong secret:**
```bash
# Generate 256-bit secret
openssl rand -base64 32

# Or use UUID
uuidgen | shasum -a 256 | cut -c1-64
```

**Set in production:**
```bash
export JWT_SECRET="your-super-secret-key-minimum-256-bits-generated-above"
```

---

### 2. Redis Authentication

**Enable Redis password:**
```yaml
# docker-compose.yml
redis:
  command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}

java-websocket:
  environment:
    - SPRING_DATA_REDIS_PASSWORD=${REDIS_PASSWORD}
```

**In .env:**
```bash
REDIS_PASSWORD=your-strong-redis-password
```

---

### 3. Rate Limiting

**Implement rate limiting in SecurityValidator:**

```java
@Service
public class SecurityValidator {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public boolean checkRateLimit(String userId) {
        String key = "ratelimit:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        
        // Allow 100 requests per minute
        return count <= 100;
    }
}
```

**Use in WebSocketHandler:**
```java
if (!securityValidator.checkRateLimit(userId)) {
    log.warn("Rate limit exceeded: userId={}", userId);
    wsSession.close(CloseStatus.POLICY_VIOLATION);
    return;
}
```

---

### 4. CORS Configuration

**For WebSocket:**
```java
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
            .setAllowedOrigins("https://yourdomain.com", "https://www.yourdomain.com")
            .setAllowedOriginPatterns("https://*.yourdomain.com");
    }
}
```

---

## Custom Integrations

### 1. Custom AI Provider

**Create custom AI client:**

```java
@Service
public class CustomAIClient {
    
    private final WebClient webClient;
    private final EventPublisher eventPublisher;
    
    public Flux<StreamChunk> streamFromCustomAI(String prompt, String sessionId) {
        return webClient.post()
            .uri("https://your-ai-api.com/stream")
            .bodyValue(Map.of("prompt", prompt))
            .retrieve()
            .bodyToFlux(String.class)
            .map(chunk -> StreamChunk.builder()
                .messageId(sessionId)
                .content(chunk)
                .type(StreamChunk.ChunkType.TEXT)
                .timestamp(Instant.now())
                .build())
            .doOnNext(chunk -> eventPublisher.publishChunkReceived(sessionId, chunk));
    }
}
```

---

### 2. Database Persistence

**Add JPA Entity:**

```java
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String conversationId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    // Getters and setters
}
```

**Add Repository:**

```java
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    
    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    
    @Query("SELECT c FROM ChatMessageEntity c WHERE c.userId = :userId ORDER BY c.createdAt DESC")
    List<ChatMessageEntity> findRecentByUser(@Param("userId") String userId, Pageable pageable);
}
```

**Update pom.xml:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

---

### 3. Email Notifications

**Add email service:**

```java
@Service
public class NotificationService {
    
    private final JavaMailSender mailSender;
    private final EventPublisher eventPublisher;
    
    @KafkaListener(topics = "stream-events")
    public void handleStreamEvent(StreamEvent event) {
        if (event.getType() == EventType.STREAM_COMPLETED) {
            sendCompletionEmail(event.getUserId(), event.getSessionId());
        }
    }
    
    private void sendCompletionEmail(String userId, String sessionId) {
        // Send email notification
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(getUserEmail(userId));
        message.setSubject("Your AI chat is complete");
        message.setText("Your conversation " + sessionId + " has completed.");
        mailSender.send(message);
    }
}
```

---

### 4. Webhook Integration

**Call external webhook on events:**

```java
@Service
public class WebhookService {
    
    private final WebClient webClient;
    
    @Value("${webhook.url}")
    private String webhookUrl;
    
    public void sendWebhook(String event, Map<String, Object> data) {
        webClient.post()
            .uri(webhookUrl)
            .bodyValue(Map.of(
                "event", event,
                "data", data,
                "timestamp", Instant.now()
            ))
            .retrieve()
            .bodyToMono(Void.class)
            .subscribe(
                success -> log.info("Webhook sent: {}", event),
                error -> log.error("Webhook failed", error)
            );
    }
}
```

---

## Advanced Scenarios

### 1. Multi-Tenant Support

**Add tenant identification:**

```java
@Service
public class TenantContext {
    
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    public static void setTenant(String tenantId) {
        currentTenant.set(tenantId);
    }
    
    public static String getTenant() {
        return currentTenant.get();
    }
    
    public static void clear() {
        currentTenant.remove();
    }
}
```

**Extract tenant from JWT:**
```java
@Override
public void afterConnectionEstablished(WebSocketSession wsSession) {
    String token = extractToken(wsSession);
    Claims claims = securityValidator.extractAllClaims(token);
    String tenantId = claims.get("tenant", String.class);
    
    TenantContext.setTenant(tenantId);
    // Continue with connection setup
}
```

**Use tenant in cache keys:**
```java
public void put(String sessionId, ChatSession session) {
    String tenantId = TenantContext.getTenant();
    String key = tenantId + ":" + sessionId;
    l1Cache.put(key, session);
    redisTemplate.opsForValue().set(key, session);
}
```

---

### 2. A/B Testing Framework

**Feature flag service:**

```java
@Service
public class FeatureFlagService {
    
    public boolean isFeatureEnabled(String userId, String feature) {
        // Simple hash-based A/B testing
        int hash = userId.hashCode();
        int bucket = Math.abs(hash % 100);
        
        return switch (feature) {
            case "new_cache_strategy" -> bucket < 50;  // 50% rollout
            case "enhanced_recovery" -> bucket < 10;   // 10% rollout
            default -> false;
        };
    }
}
```

**Use in code:**
```java
if (featureFlagService.isFeatureEnabled(userId, "new_cache_strategy")) {
    // Use new cache strategy
    return newCacheManager.get(sessionId);
} else {
    // Use old cache strategy
    return oldCacheManager.get(sessionId);
}
```

---

### 3. Circuit Breaker Pattern

**Add Resilience4j:**
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
```

**Configure circuit breaker:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      aiService:
        registerHealthIndicator: true
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10000
        permittedNumberOfCallsInHalfOpenState: 3
```

**Use in service:**
```java
@Service
public class AIService {
    
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackResponse")
    public Flux<StreamChunk> streamFromAI(String prompt) {
        // Call AI service
        return aiClient.stream(prompt);
    }
    
    public Flux<StreamChunk> fallbackResponse(String prompt, Exception e) {
        log.error("AI service unavailable, using fallback", e);
        return Flux.just(StreamChunk.builder()
            .content("Service temporarily unavailable. Please try again.")
            .build());
    }
}
```

---

### 4. Custom Metrics Dashboard

**Export custom metrics:**

```java
@Service
public class BusinessMetrics {
    
    private final MeterRegistry registry;
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void recordBusinessMetrics() {
        // Active users
        int activeUsers = sessionManager.getActiveUserCount();
        Gauge.builder("business.active_users", () -> activeUsers)
            .register(registry);
        
        // Revenue per stream (example)
        double revenue = calculateStreamRevenue();
        Gauge.builder("business.stream_revenue", () -> revenue)
            .register(registry);
        
        // Average satisfaction score
        double satisfaction = calculateSatisfaction();
        Gauge.builder("business.satisfaction_score", () -> satisfaction)
            .register(registry);
    }
}
```

---

## Quick Reference

### Environment Variables Priority

1. Command line: `--security.jwt.secret=xxx`
2. Environment: `JWT_SECRET=xxx`
3. `.env` file: `JWT_SECRET=xxx`
4. `application.yml`: `security.jwt.secret: xxx`

### Common Configurations

| Use Case | Cache Size | Backpressure | JVM Heap |
|----------|-----------|--------------|----------|
| Development | 1000 | 500 | 512MB |
| Small Production | 10000 | 1000 | 1GB |
| Medium Production | 25000 | 2000 | 2GB |
| Large Production | 50000 | 5000 | 4GB+ |

### Monitoring Checklist

- [ ] Prometheus scraping Java app
- [ ] Grafana dashboards imported
- [ ] Alerts configured
- [ ] Log aggregation enabled
- [ ] Error tracking setup
- [ ] Performance baselines established

---

## Need Help?

1. Check `IMPL_v2.md` for architecture details
2. See `IMPLEMENTATION_SUMMARY.md` for features
3. Read `MIGRATION_GUIDE.md` for upgrade path
4. Review logs in `logs/websocket-server.log`
5. Check metrics at `http://localhost:8080/actuator/prometheus`

---

**Happy Customizing! 🎨**
