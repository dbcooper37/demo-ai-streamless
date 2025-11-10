# Test Checklist - Sender Message Display & Cancel Button

## ✅ Code Verification
- [x] Python syntax check passed
- [x] Frontend build successful
- [x] No compilation errors

## 🧪 Manual Testing Required

### Sender Message Display
- [ ] Open chat application in browser
- [ ] Type a message and press Enter
- [ ] ✅ Verify message appears immediately in the chat
- [ ] ✅ Verify message has correct styling (user message style)
- [ ] ✅ Verify timestamp is displayed
- [ ] Wait for AI response
- [ ] ✅ Verify both messages appear in correct order

### Cancel Button - Basic Functionality
- [ ] Send a message to trigger AI response
- [ ] ✅ Verify "Hủy" (Cancel) button appears while AI is responding
- [ ] ✅ Verify Cancel button has red gradient styling
- [ ] ✅ Verify Cancel button has stop icon ⏹️
- [ ] ✅ Verify input is disabled with placeholder "AI đang trả lời..."
- [ ] Wait for AI to finish
- [ ] ✅ Verify Cancel button disappears
- [ ] ✅ Verify Send button reappears

### Cancel Button - Cancellation Logic
- [ ] Send a message to trigger AI response
- [ ] Click "Hủy" button in the middle of streaming
- [ ] ✅ Verify streaming stops
- [ ] ✅ Verify message shows "[Đã hủy]" suffix
- [ ] ✅ Verify Cancel button disappears immediately
- [ ] ✅ Verify can send new message right away
- [ ] ✅ Verify cancelled message is saved in history

### Cancel Button - Edge Cases
- [ ] Click Cancel multiple times rapidly
- [ ] ✅ Verify no errors occur
- [ ] Send message, cancel, then refresh page
- [ ] ✅ Verify cancelled message appears in history
- [ ] Test with long AI responses
- [ ] ✅ Verify cancel works at different points in streaming

### WebSocket Integration
- [ ] Send message and wait for complete response
- [ ] ✅ Verify optimistic message doesn't duplicate
- [ ] Disconnect WebSocket
- [ ] ✅ Verify appropriate error message
- [ ] Reconnect WebSocket
- [ ] ✅ Verify chat history loads correctly

### Multiple Sessions
- [ ] Open chat in two different browser tabs
- [ ] Send messages from both
- [ ] ✅ Verify messages only appear in correct session
- [ ] Cancel in one session
- [ ] ✅ Verify other session continues normally

### Responsive Design
- [ ] Test on desktop (1920x1080)
- [ ] ✅ Verify Cancel button displays correctly
- [ ] Test on tablet (768px width)
- [ ] ✅ Verify Cancel button is full width
- [ ] Test on mobile (375px width)
- [ ] ✅ Verify Cancel button is full width
- [ ] ✅ Verify text is readable

### Performance
- [ ] Send 10 messages rapidly
- [ ] ✅ Verify UI remains responsive
- [ ] Cancel multiple messages in sequence
- [ ] ✅ Verify no memory leaks (check dev tools)
- [ ] Let AI complete long response
- [ ] ✅ Verify smooth streaming animation

## 🐛 Known Issues (if any)
- None identified during code review

## 📝 Testing Notes
- Use Chrome DevTools Network tab to verify API calls
- Use Console to check for errors or warnings
- Monitor backend logs for error messages
- Test with slow network connection (throttling)

## 🚀 Deployment Checklist
- [ ] All manual tests passed
- [ ] No console errors in browser
- [ ] No backend errors in logs
- [ ] Review code changes one final time
- [ ] Update documentation if needed
- [ ] Deploy to staging environment
- [ ] Smoke test on staging
- [ ] Deploy to production
- [ ] Monitor production logs for 15 minutes

## Environment Setup for Testing

```bash
# Start all services
docker compose up -d

# Watch backend logs
docker compose logs -f python-ai-service

# Watch frontend logs
docker compose logs -f frontend

# Watch WebSocket server logs
docker compose logs -f java-websocket-server
```

## API Testing with curl

```bash
# Test cancel endpoint
curl -X POST http://localhost:8000/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test_session",
    "message_id": "test_message"
  }'

# Test chat endpoint
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test_session",
    "message": "Hello",
    "user_id": "test_user"
  }'
```
