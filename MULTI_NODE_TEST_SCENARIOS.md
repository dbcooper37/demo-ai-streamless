# 🧪 Multi-Node Test Scenarios

Test scenarios để verify khả năng hoạt động của hệ thống phân tán.

---

## 🎯 Test Environment Setup

### Architecture
```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
┌──────▼──────┐
│    NGINX    │  (Load Balancer)
│   :8080     │
└──────┬──────┘
       │
   ┌───┴────┬─────────┬─────────┐
   │        │         │         │
┌──▼───┐ ┌─▼────┐ ┌─▼────┐  ┌─▼────┐
│Node1 │ │Node2 │ │Node3 │  │Redis │
│:8081 │ │:8082 │ │:8083 │  │:6379 │
└──────┘ └──────┘ └──────┘  └──────┘
```

### Start Multi-Node Setup
```bash
# Start all services
docker-compose -f docker-compose.multi-node.yml up -d

# Check all services are running
docker-compose -f docker-compose.multi-node.yml ps

# View logs
docker-compose -f docker-compose.multi-node.yml logs -f
```

---

## 📋 Test Scenarios

### Test 1: Basic Multi-Node Connectivity ✅

**Objective**: Verify all nodes can receive connections

**Steps**:
1. Start multi-node setup
2. Open browser and connect to http://localhost:3000
3. Send a message
4. Check logs of all 3 nodes

**Expected Result**:
- Client connects successfully via NGINX
- Message routed to one of the nodes
- All nodes can see session in Redis
- Response received correctly

**Verification**:
```bash
# Check which node received the connection
docker logs demo-java-websocket-1 | grep "WebSocket connected"
docker logs demo-java-websocket-2 | grep "WebSocket connected"
docker logs demo-java-websocket-3 | grep "WebSocket connected"

# Check Redis for session
docker exec demo-redis-multi redis-cli HGETALL "sessions:active"
```

**Success Criteria**:
- ✅ One node shows "WebSocket connected"
- ✅ Session exists in Redis
- ✅ Message delivered to client

---

### Test 2: Sticky Session Behavior ✅

**Objective**: Verify NGINX ip_hash keeps client on same node

**Steps**:
1. Connect client to WebSocket
2. Send 10 messages
3. Check logs to see which nodes handle messages

**Expected Result**:
- All messages handled by same node (sticky session)
- No reconnections between messages

**Verification**:
```bash
# Count connections per node
docker logs demo-java-websocket-1 | grep "WebSocket connected" | wc -l
docker logs demo-java-websocket-2 | grep "WebSocket connected" | wc -l
docker logs demo-java-websocket-3 | grep "WebSocket connected" | wc -l

# Should see all from same node
```

**Success Criteria**:
- ✅ All messages from same session handled by same node
- ✅ No unexpected disconnects

---

### Test 3: Cross-Node Message Broadcasting 🔄

**Objective**: Verify messages published via PubSub reach all nodes

**Steps**:
1. Open 3 browser tabs (will connect to different nodes)
2. Send message from tab 1
3. Verify all tabs receive the message

**Expected Result**:
- Message published to Redis PubSub
- All nodes subscribed to channel receive message
- All connected clients receive message

**Verification**:
```bash
# Monitor Redis PubSub
docker exec demo-redis-multi redis-cli SUBSCRIBE "chat:stream:*"

# Check message receipt in logs
docker logs demo-java-websocket-1 | grep "Received message"
docker logs demo-java-websocket-2 | grep "Received message"
docker logs demo-java-websocket-3 | grep "Received message"
```

**Success Criteria**:
- ✅ All nodes receive PubSub message
- ✅ All clients receive message regardless of node

---

### Test 4: Session Persistence & Recovery ✅

**Objective**: Verify session survives node reconnection

**Steps**:
1. Connect to WebSocket (note which node)
2. Send message and start receiving streaming response
3. Disconnect client mid-stream
4. Reconnect within 30 seconds
5. Send recovery request

**Expected Result**:
- Session metadata preserved in Redis
- Missing chunks retrieved from cache
- Stream continues from last received chunk

**Verification**:
```bash
# Check session still in Redis
docker exec demo-redis-multi redis-cli HGET "sessions:active" "session_12345"

# Check stream chunks in cache
docker exec demo-redis-multi redis-cli LRANGE "stream:chunks:message-id" 0 -1

# Check recovery logs
docker logs demo-java-websocket-1 | grep "Recovery requested"
```

**Success Criteria**:
- ✅ Session persists in Redis
- ✅ Chunks cached correctly
- ✅ Recovery successful
- ✅ No duplicate chunks

---

### Test 5: Node Failover (Crash & Recover) 🔥

**Objective**: Verify system continues working when one node crashes

**Steps**:
1. Connect 3 clients (one per node)
2. Kill node 1: `docker stop demo-java-websocket-1`
3. Client 1 disconnects, reconnects via NGINX (routed to node 2/3)
4. Verify client 1 can continue chatting
5. Restart node 1: `docker start demo-java-websocket-1`
6. Verify new connections can use node 1

**Expected Result**:
- Client 1 detects disconnect
- Client 1 reconnects to healthy node
- Session recovered from Redis
- Clients 2 & 3 unaffected
- Node 1 rejoins cluster cleanly

**Verification**:
```bash
# Kill node 1
docker stop demo-java-websocket-1

# Wait 5 seconds, check other nodes still working
docker logs demo-java-websocket-2 | tail -20
docker logs demo-java-websocket-3 | tail -20

# Restart node 1
docker start demo-java-websocket-1

# Verify it's healthy
curl http://localhost:8081/health/distributed
```

**Success Criteria**:
- ✅ Other nodes unaffected
- ✅ Client reconnects successfully
- ✅ Session state preserved
- ✅ Node 1 rejoins without issues

---

### Test 6: Redis Failure (Critical) 💥

**Objective**: Verify system behavior when Redis fails

**Steps**:
1. Connect clients
2. Stop Redis: `docker stop demo-redis-multi`
3. Try to send message
4. Restart Redis: `docker start demo-redis-multi`
5. Verify system recovers

**Expected Result**:
- Operations fail gracefully
- Clients receive error messages
- After Redis restart, system recovers
- New connections work normally

**Verification**:
```bash
# Stop Redis
docker stop demo-redis-multi

# Check node logs for errors
docker logs demo-java-websocket-1 | grep -i "redis"

# Restart Redis
docker start demo-redis-multi

# Wait 5 seconds, test new connection
curl http://localhost:8080/health
```

**Success Criteria**:
- ✅ No crashes (fail gracefully)
- ✅ Error messages logged
- ✅ System recovers after Redis restart
- ✅ No data corruption

---

### Test 7: Concurrent Connections Load Test 📈

**Objective**: Test system under load with many concurrent connections

**Steps**:
1. Use load testing tool (k6, artillery, or custom script)
2. Create 1000 concurrent WebSocket connections
3. Send messages at 10 msg/sec per connection
4. Monitor for 5 minutes

**Load Test Script** (k6):
```javascript
import ws from 'k6/ws';
import { check } from 'k6';

export let options = {
  stages: [
    { duration: '1m', target: 333 },  // Ramp up to 333 per node
    { duration: '3m', target: 1000 }, // Stay at 1000 total
    { duration: '1m', target: 0 },    // Ramp down
  ],
};

export default function () {
  const url = 'ws://localhost:8080/ws/chat?session_id=load_test_' + __VU;
  const response = ws.connect(url, function (socket) {
    socket.on('open', () => {
      console.log('Connected');
      socket.setInterval(() => {
        socket.send('Test message');
      }, 100); // 10 msg/sec
    });

    socket.on('message', (data) => {
      console.log('Received:', data);
    });

    socket.setTimeout(() => {
      socket.close();
    }, 60000); // 1 minute
  });

  check(response, { 'status is 101': (r) => r && r.status === 101 });
}
```

**Monitoring**:
```bash
# Monitor CPU/Memory
docker stats

# Monitor active connections
watch -n 1 'curl -s http://localhost:8081/health/stats | jq'

# Monitor Redis
docker exec demo-redis-multi redis-cli INFO stats
```

**Success Criteria**:
- ✅ All 1000 connections established
- ✅ Message delivery < 100ms latency
- ✅ No connection drops
- ✅ Memory usage stable
- ✅ CPU usage < 80%

---

### Test 8: Distributed Lock Ordering 🔒

**Objective**: Verify chunks maintain order under concurrent writes

**Steps**:
1. Connect to same session from 3 clients on different nodes
2. Trigger streaming response
3. Verify chunks arrive in correct order
4. Check Redis cache has correct sequence

**Expected Result**:
- Distributed locks prevent race conditions
- Chunks stored in correct order
- No gaps in chunk indices
- Clients receive chunks in order

**Verification**:
```bash
# Check chunk order in Redis
docker exec demo-redis-multi redis-cli LRANGE "stream:chunks:message-id" 0 -1

# Parse and verify indices are sequential
# Should be: 0, 1, 2, 3, 4, ...
```

**Success Criteria**:
- ✅ Chunks in sequential order
- ✅ No duplicate indices
- ✅ No missing chunks
- ✅ Lock acquisition logs show proper ordering

---

### Test 9: Session Cleanup & TTL ⏰

**Objective**: Verify stale sessions are cleaned up

**Steps**:
1. Connect client and send message
2. Disconnect without cleanup
3. Wait 60 seconds (cleanup interval)
4. Check Redis for stale session

**Expected Result**:
- Stale session detected by heartbeat check
- Session removed from local map
- Session removed from Redis after cleanup
- No memory leaks

**Verification**:
```bash
# Check initial state
docker exec demo-redis-multi redis-cli HGETALL "sessions:active"

# Wait 60 seconds after disconnect

# Check cleanup happened
docker logs demo-java-websocket-1 | grep "Cleaning up"

# Verify session removed
docker exec demo-redis-multi redis-cli HGETALL "sessions:active"
```

**Success Criteria**:
- ✅ Stale session detected within 30s (heartbeat)
- ✅ Session cleaned within 60s (cleanup job)
- ✅ Redis entry removed
- ✅ Memory released

---

### Test 10: Multi-User Concurrent Streams 👥

**Objective**: Test multiple users streaming simultaneously

**Steps**:
1. Connect 10 clients with different session IDs
2. Send message from each client
3. All receive AI streaming responses simultaneously
4. Verify no cross-contamination

**Expected Result**:
- Each session isolated
- No messages leak between sessions
- All streams complete successfully
- Correct message routing

**Verification**:
```bash
# Monitor PubSub channels
docker exec demo-redis-multi redis-cli PUBSUB CHANNELS

# Should see multiple chat:stream:{sessionId} channels

# Check each session receives only its messages
# View client consoles for verification
```

**Success Criteria**:
- ✅ All 10 streams complete
- ✅ No cross-contamination
- ✅ Correct session isolation
- ✅ All chunks delivered to correct clients

---

## 🔧 Helper Scripts

### Check System Health
```bash
#!/bin/bash
echo "=== System Health Check ==="

echo -e "\n1. Redis Health:"
docker exec demo-redis-multi redis-cli ping

echo -e "\n2. Node Health:"
for i in 1 2 3; do
  echo "Node $i:"
  curl -s http://localhost:808$i/health/distributed | jq '.status, .sessions'
done

echo -e "\n3. Active Sessions:"
docker exec demo-redis-multi redis-cli HGETALL "sessions:active"

echo -e "\n4. Container Status:"
docker-compose -f docker-compose.multi-node.yml ps
```

### Monitor Load
```bash
#!/bin/bash
watch -n 1 '
echo "=== Load Monitor ===";
echo "";
echo "Containers:";
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}";
echo "";
echo "Active Sessions:";
docker exec demo-redis-multi redis-cli HLEN "sessions:active";
echo "";
echo "PubSub Channels:";
docker exec demo-redis-multi redis-cli PUBSUB NUMSUB chat:stream:*;
'
```

### Clean Everything
```bash
#!/bin/bash
echo "Stopping all containers..."
docker-compose -f docker-compose.multi-node.yml down

echo "Removing volumes..."
docker volume rm $(docker volume ls -q | grep demo)

echo "Cleaning Redis data..."
docker exec demo-redis-multi redis-cli FLUSHALL

echo "Done!"
```

---

## 📊 Performance Benchmarks

### Target Metrics

| Metric | Target | Good | Acceptable |
|--------|--------|------|------------|
| Connection Time | < 50ms | < 100ms | < 200ms |
| Message Latency | < 50ms | < 100ms | < 200ms |
| Recovery Time | < 1s | < 2s | < 5s |
| Failover Time | < 5s | < 10s | < 30s |
| Concurrent Connections | 10K+ | 5K+ | 1K+ |
| Messages/sec | 100K+ | 50K+ | 10K+ |
| Memory per connection | < 10KB | < 20KB | < 50KB |
| CPU usage (1K conn) | < 50% | < 70% | < 90% |

### Load Test Results Template

```
Test Date: _______________
Environment: 3 nodes, 1 Redis
Duration: 5 minutes

Connections: _____ concurrent
Message Rate: _____ msg/sec
Total Messages: _____

Results:
- Success Rate: _____%
- Avg Latency: _____ms
- P95 Latency: _____ms
- P99 Latency: _____ms
- Errors: _____
- Reconnects: _____

Resource Usage:
- Node 1 CPU: _____%
- Node 2 CPU: _____%
- Node 3 CPU: _____%
- Redis CPU: _____%
- Total Memory: _____MB

Issues Found:
- _____________________
- _____________________

Recommendations:
- _____________________
- _____________________
```

---

## ✅ Test Checklist

Before Production Deployment:

### Functionality
- [ ] Test 1: Basic Connectivity
- [ ] Test 2: Sticky Sessions
- [ ] Test 3: Cross-Node Broadcasting
- [ ] Test 4: Session Recovery
- [ ] Test 5: Node Failover
- [ ] Test 6: Redis Failure
- [ ] Test 7: Load Test (1000 concurrent)
- [ ] Test 8: Lock Ordering
- [ ] Test 9: Session Cleanup
- [ ] Test 10: Multi-User Streams

### Performance
- [ ] Latency < 100ms
- [ ] 1000+ concurrent connections
- [ ] CPU < 70% under load
- [ ] Memory stable (no leaks)
- [ ] No dropped messages

### Reliability
- [ ] Graceful failover
- [ ] Session persistence
- [ ] Stale cleanup working
- [ ] Error handling proper
- [ ] Logs informative

### Monitoring
- [ ] Health endpoints working
- [ ] Metrics available
- [ ] Alerts configured
- [ ] Dashboard created

---

## 🎯 Expected Results Summary

| Test | Status | Critical | Notes |
|------|--------|----------|-------|
| Basic Connectivity | ✅ | Yes | Should work out of box |
| Sticky Sessions | ✅ | Yes | NGINX ip_hash configured |
| Cross-Node Broadcast | ✅ | Yes | Redis PubSub working |
| Session Recovery | ✅ | Yes | Cache & recovery service ready |
| Node Failover | ✅ | Yes | Auto-reconnect working |
| Redis Failure | ⚠️ | Yes | Fail gracefully, no circuit breaker yet |
| Load Test | ✅ | Medium | Should handle 1K+ connections |
| Lock Ordering | ✅ | High | Redisson locks working |
| Session Cleanup | ✅ | Medium | 60s interval, working |
| Multi-User | ✅ | High | Session isolation working |

---

## 📝 Notes

- All tests should pass before production deployment
- Document any failures and fixes
- Re-run tests after any code changes
- Keep performance benchmarks updated
- Monitor production metrics regularly

---

## 🔗 References

- Docker Compose: `docker-compose.multi-node.yml`
- NGINX Config: `nginx-lb.conf`
- Health Endpoints: `/health`, `/health/distributed`, `/health/ready`
- Distributed Analysis: `DISTRIBUTED_SYSTEM_ANALYSIS.md`
