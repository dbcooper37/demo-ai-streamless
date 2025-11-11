# 🚀 Quick Start - Sticky Session Deployment

## TL;DR

```bash
# 1. Checkout branch
git checkout dev_sticky_session

# 2. Deploy (one command!)
./DEPLOY_STICKY_SESSION.sh

# 3. Test
./TEST_STICKY_SESSION.sh

# 4. Open browser
open http://localhost:3000
```

---

## 📋 Prerequisites

- Docker & Docker Compose installed
- At least 4GB RAM available
- Ports available: 3000, 6379, 8080, 8090, 9092

---

## 🎯 What You Get

```
✅ 3 WebSocket Nodes     (load balanced với sticky sessions)
✅ 3 AI Service Nodes    (round-robin load balancing)  
✅ Nginx Load Balancer   (ip_hash cho sticky sessions)
✅ Redis Shared State    (session registry + cache)
✅ Kafka Event Stream    (distributed messaging)
✅ Frontend App          (React + WebSocket)
```

---

## 📊 Service URLs

| Service | URL | Description |
|---------|-----|-------------|
| Frontend | http://localhost:3000 | React application |
| Load Balancer | http://localhost:8080 | Nginx LB entry point |
| WebSocket | ws://localhost:8080/ws/chat | WebSocket endpoint |
| API | http://localhost:8080/api | REST API proxy |
| Health Check | http://localhost:8080/health | LB health status |
| Nginx Stats | http://localhost:8090/nginx-status | Nginx metrics |

---

## 🔧 Common Commands

### Deployment
```bash
# Start all services
./DEPLOY_STICKY_SESSION.sh

# Start manually
docker-compose -f docker-compose.sticky-session.yml up -d --build

# Stop all services
docker-compose -f docker-compose.sticky-session.yml down

# Restart specific service
docker-compose -f docker-compose.sticky-session.yml restart java-websocket-1
```

### Monitoring
```bash
# View all logs
docker-compose -f docker-compose.sticky-session.yml logs -f

# View specific service
docker logs -f sticky-java-ws-1
docker logs -f sticky-nginx-lb
docker logs -f sticky-redis

# Check container status
docker-compose -f docker-compose.sticky-session.yml ps

# Check resource usage
docker stats
```

### Health Checks
```bash
# Load balancer
curl http://localhost:8080/health

# Nginx stats
curl http://localhost:8090/nginx-status

# WebSocket nodes (from inside container)
docker exec sticky-java-ws-1 curl -s http://localhost:8080/actuator/health
docker exec sticky-java-ws-2 curl -s http://localhost:8080/actuator/health
docker exec sticky-java-ws-3 curl -s http://localhost:8080/actuator/health

# Redis
docker exec sticky-redis redis-cli ping
```

### Testing
```bash
# Run full test suite
./TEST_STICKY_SESSION.sh

# Test WebSocket connection (requires wscat)
npm install -g wscat
wscat -c "ws://localhost:8080/ws/chat?session_id=test&user_id=testuser&token=dev-token"

# Test sticky session
for i in {1..5}; do curl -s http://localhost:8080/health; done
docker exec sticky-nginx-lb tail -5 /var/log/nginx/access.log | grep "upstream:"
```

---

## 🐛 Troubleshooting

### Services not starting?
```bash
# Check Docker
docker --version
docker-compose --version

# Check ports
lsof -i :3000
lsof -i :8080
lsof -i :6379

# Check logs
docker-compose -f docker-compose.sticky-session.yml logs
```

### WebSocket not connecting?
```bash
# 1. Check Nginx
curl http://localhost:8080/

# 2. Check backend nodes
docker ps | grep sticky-java-ws

# 3. Check logs
docker logs sticky-nginx-lb
docker logs sticky-java-ws-1
```

### High memory usage?
```bash
# Check current usage
docker stats

# Reduce JVM memory in docker-compose.sticky-session.yml:
# JAVA_OPTS: -Xms256m -Xmx512m

# Restart
docker-compose -f docker-compose.sticky-session.yml restart
```

---

## 🎯 Test Scenarios

### Scenario 1: Verify Sticky Sessions
```bash
# Make 10 requests from same IP
for i in {1..10}; do 
    curl -s http://localhost:8080/health > /dev/null
    echo "Request $i completed"
done

# Check distribution (should all go to same backend)
docker exec sticky-nginx-lb tail -10 /var/log/nginx/access.log | grep -oP 'upstream: \K[^:]+' | sort | uniq -c
```

### Scenario 2: Node Failover
```bash
# Terminal 1: Connect WebSocket
wscat -c "ws://localhost:8080/ws/chat?session_id=failover-test&user_id=test&token=dev-token"

# Terminal 2: Find connected node (check logs)
docker-compose -f docker-compose.sticky-session.yml logs | grep "WebSocket connected" | grep "failover-test"

# Terminal 2: Stop that node
docker stop sticky-java-ws-1

# Terminal 1: Try sending message (should fail)
# Terminal 1: Reconnect (will connect to another node)
# Recovery mechanism should restore any missing data from Redis
```

### Scenario 3: Load Test
```bash
# Install wrk (load testing tool)
# brew install wrk  # macOS
# apt-get install wrk  # Ubuntu

# Run load test
wrk -t4 -c100 -d30s http://localhost:8080/health

# Check distribution
docker exec sticky-nginx-lb cat /var/log/nginx/access.log | grep -oP 'upstream: \K[^:]+' | sort | uniq -c
```

---

## 📈 Performance Tips

### For Development
```yaml
# In docker-compose.sticky-session.yml
environment:
  - JAVA_OPTS=-Xms256m -Xmx512m
  - LOG_LEVEL=DEBUG
  - CACHE_L1_MAX_SIZE=1000
```

### For Production
```yaml
# In docker-compose.sticky-session.yml
environment:
  - JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC
  - LOG_LEVEL=INFO
  - CACHE_L1_MAX_SIZE=10000
```

---

## 🔐 Security Checklist

Before production deployment:

- [ ] Change JWT_SECRET to strong random value
- [ ] Enable Redis password authentication
- [ ] Configure SSL/TLS (wss:// for WebSocket)
- [ ] Set proper CORS origins (not `*`)
- [ ] Enable rate limiting in Nginx
- [ ] Use non-root users in containers
- [ ] Set up firewall rules
- [ ] Enable audit logging
- [ ] Regular security updates

---

## 📚 Next Steps

1. **Explore the app**: http://localhost:3000
2. **Read full docs**: [README.sticky-session.md](./README.sticky-session.md)
3. **Check architecture**: [STICKY_SESSION_SUMMARY.md](./STICKY_SESSION_SUMMARY.md)
4. **Configure for production**: `.env.sticky-session.example`

---

## 🎉 Success Indicators

✅ All containers running:
```bash
docker-compose -f docker-compose.sticky-session.yml ps
# Should show all services as "Up"
```

✅ Health checks passing:
```bash
curl http://localhost:8080/health
# Should return healthy status
```

✅ Frontend accessible:
```bash
curl http://localhost:3000
# Should return HTML
```

✅ WebSocket connecting:
```bash
wscat -c "ws://localhost:8080/ws/chat?session_id=test&user_id=test&token=dev-token"
# Should receive welcome message
```

---

## 💡 Tips

- **Logs are your friend**: Always check logs when debugging
- **Health checks first**: Verify all services are healthy before testing
- **Test incrementally**: Test one component at a time
- **Monitor resources**: Keep an eye on Docker stats
- **Clean up regularly**: Remove unused volumes and images

---

## 🆘 Get Help

1. Run diagnostics: `./TEST_STICKY_SESSION.sh`
2. Check logs: `docker-compose -f docker-compose.sticky-session.yml logs`
3. Review docs: `README.sticky-session.md`
4. Open issue in repository

---

**Happy deploying! 🚀**

Made with ❤️ for multi-node sticky session deployment
