#!/usr/bin/env python3
"""
Test script to verify streaming is working with WebSocket + HTTP
Usage: python3 test_streaming_websocket.py
"""
import asyncio
import websockets
import json
import aiohttp
import sys
from datetime import datetime

# Configuration
WEBSOCKET_URL = "ws://localhost:8080/ws"
HTTP_URL = "http://localhost:8000/chat"
SESSION_ID = f"test_session_{int(datetime.now().timestamp())}"
USER_ID = "demo_user"
TOKEN = "dev-token"

class Colors:
    GREEN = '\033[92m'
    BLUE = '\033[94m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

async def test_streaming():
    """Test streaming functionality with WebSocket + HTTP"""
    
    print(f"\n{Colors.BOLD}🧪 Testing Streaming Functionality{Colors.RESET}")
    print(f"{Colors.BLUE}Session ID: {SESSION_ID}{Colors.RESET}")
    print(f"{Colors.BLUE}WebSocket URL: {WEBSOCKET_URL}{Colors.RESET}")
    print(f"{Colors.BLUE}HTTP URL: {HTTP_URL}{Colors.RESET}\n")
    
    # Build WebSocket URL with parameters
    ws_url = f"{WEBSOCKET_URL}?session_id={SESSION_ID}&user_id={USER_ID}&token={TOKEN}"
    
    try:
        # Connect to WebSocket
        print(f"{Colors.YELLOW}📡 Connecting to WebSocket...{Colors.RESET}")
        async with websockets.connect(ws_url) as websocket:
            print(f"{Colors.GREEN}✅ Connected to WebSocket{Colors.RESET}\n")
            
            # Create task to receive messages
            received_messages = []
            streaming_started = False
            streaming_complete = False
            
            async def receive_messages():
                nonlocal streaming_started, streaming_complete
                try:
                    while True:
                        message = await websocket.recv()
                        data = json.loads(message)
                        received_messages.append(data)
                        
                        msg_type = data.get('type')
                        
                        if msg_type == 'welcome':
                            print(f"{Colors.BLUE}📨 Welcome message received{Colors.RESET}")
                        elif msg_type == 'message':
                            msg_data = data.get('data', {})
                            role = msg_data.get('role')
                            content = msg_data.get('content', '')
                            is_complete = msg_data.get('is_complete', False)
                            
                            if role == 'user':
                                print(f"{Colors.BLUE}📨 User message: {content}{Colors.RESET}")
                            elif role == 'assistant':
                                if not streaming_started:
                                    streaming_started = True
                                    print(f"{Colors.YELLOW}🔄 AI streaming started...{Colors.RESET}")
                                
                                if is_complete:
                                    streaming_complete = True
                                    print(f"{Colors.GREEN}✅ Streaming complete: {content}{Colors.RESET}\n")
                                else:
                                    print(f"{Colors.YELLOW}⏩ Chunk: {content[:50]}...{Colors.RESET}", end='\r')
                        else:
                            print(f"{Colors.BLUE}📨 Message type: {msg_type}{Colors.RESET}")
                            
                except websockets.exceptions.ConnectionClosed:
                    print(f"{Colors.RED}❌ WebSocket connection closed{Colors.RESET}")
                except Exception as e:
                    print(f"{Colors.RED}❌ Error receiving messages: {e}{Colors.RESET}")
            
            # Start receiving messages in background
            receive_task = asyncio.create_task(receive_messages())
            
            # Wait a bit for connection to stabilize
            await asyncio.sleep(1)
            
            # Send HTTP request to trigger streaming
            print(f"{Colors.YELLOW}📤 Sending HTTP POST request to /chat...{Colors.RESET}")
            async with aiohttp.ClientSession() as session:
                payload = {
                    "session_id": SESSION_ID,
                    "user_id": USER_ID,
                    "message": "xin chào"
                }
                
                async with session.post(HTTP_URL, json=payload) as response:
                    if response.status == 200:
                        result = await response.json()
                        print(f"{Colors.GREEN}✅ HTTP Response: {result}{Colors.RESET}")
                        print(f"{Colors.BLUE}   Status: {result.get('status')}{Colors.RESET}")
                        print(f"{Colors.BLUE}   Message ID: {result.get('message_id')}{Colors.RESET}\n")
                    else:
                        print(f"{Colors.RED}❌ HTTP Error: {response.status}{Colors.RESET}")
                        return
            
            # Wait for streaming to complete (max 30 seconds)
            print(f"{Colors.YELLOW}⏳ Waiting for streaming to complete...{Colors.RESET}\n")
            for i in range(30):
                await asyncio.sleep(1)
                if streaming_complete:
                    break
            
            # Cancel receive task
            receive_task.cancel()
            
            # Print results
            print(f"\n{Colors.BOLD}📊 Test Results:{Colors.RESET}")
            print(f"{Colors.BLUE}Total messages received: {len(received_messages)}{Colors.RESET}")
            print(f"{Colors.BLUE}Streaming started: {Colors.GREEN if streaming_started else Colors.RED}{streaming_started}{Colors.RESET}")
            print(f"{Colors.BLUE}Streaming completed: {Colors.GREEN if streaming_complete else Colors.RED}{streaming_complete}{Colors.RESET}")
            
            if streaming_complete:
                print(f"\n{Colors.GREEN}{Colors.BOLD}✅ TEST PASSED - Streaming is working!{Colors.RESET}")
                return 0
            elif streaming_started:
                print(f"\n{Colors.YELLOW}{Colors.BOLD}⚠️  TEST PARTIAL - Streaming started but did not complete in time{Colors.RESET}")
                return 1
            else:
                print(f"\n{Colors.RED}{Colors.BOLD}❌ TEST FAILED - Streaming did not start{Colors.RESET}")
                print(f"\n{Colors.YELLOW}Troubleshooting:{Colors.RESET}")
                print(f"1. Check Python logs: docker compose logs python-ai-service | grep 'Published.*subscribers'")
                print(f"2. Check Java logs: docker compose logs java-websocket-server | grep 'ChatOrchestrator'")
                print(f"3. Verify services are running: docker compose ps")
                return 1
                
    except websockets.exceptions.WebSocketException as e:
        print(f"\n{Colors.RED}❌ WebSocket connection failed: {e}{Colors.RESET}")
        print(f"\n{Colors.YELLOW}Make sure Java WebSocket Server is running:{Colors.RESET}")
        print(f"docker compose ps java-websocket-server")
        return 1
    except aiohttp.ClientError as e:
        print(f"\n{Colors.RED}❌ HTTP request failed: {e}{Colors.RESET}")
        print(f"\n{Colors.YELLOW}Make sure Python AI Service is running:{Colors.RESET}")
        print(f"docker compose ps python-ai-service")
        return 1
    except Exception as e:
        print(f"\n{Colors.RED}❌ Unexpected error: {e}{Colors.RESET}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    print(f"\n{Colors.BOLD}{'='*60}{Colors.RESET}")
    print(f"{Colors.BOLD}WebSocket + HTTP Streaming Test{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*60}{Colors.RESET}")
    
    try:
        exit_code = asyncio.run(test_streaming())
        sys.exit(exit_code)
    except KeyboardInterrupt:
        print(f"\n{Colors.YELLOW}Test interrupted by user{Colors.RESET}")
        sys.exit(130)
