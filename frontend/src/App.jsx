import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';

const WEBSOCKET_URL = 'ws://localhost:8080/ws/chat';
const AI_SERVICE_URL = '/api';

function App() {
  const [sessionId] = useState(() => {
    // Use existing session or create new one
    const saved = localStorage.getItem('chatSessionId');
    return saved || `session_${Date.now()}`;
  });

  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [isConnected, setIsConnected] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState('disconnected');
  const [isLoading, setIsLoading] = useState(true);
  const [isSending, setIsSending] = useState(false);

  const wsRef = useRef(null);
  const messagesEndRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const streamingMessagesRef = useRef(new Map());

  // Save session ID
  useEffect(() => {
    localStorage.setItem('chatSessionId', sessionId);
  }, [sessionId]);

  // Scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // WebSocket connection
  useEffect(() => {
    connectWebSocket();

    return () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [sessionId]);

  const connectWebSocket = () => {
    try {
      const ws = new WebSocket(`${WEBSOCKET_URL}?session_id=${sessionId}`);

      ws.onopen = () => {
        console.log('WebSocket connected');
        setIsConnected(true);
        setConnectionStatus('connected');
        setIsLoading(false);

        // Send ping periodically to keep connection alive
        const pingInterval = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send('ping');
          }
        }, 30000);

        ws.pingInterval = pingInterval;
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);

          if (data.type === 'history') {
            // Load chat history
            console.log('Received history:', data.messages.length, 'messages');
            setMessages(data.messages);
          } else if (data.type === 'message') {
            // Streaming message
            handleStreamingMessage(data.data);
          }
        } catch (error) {
          console.error('Error parsing message:', error);
        }
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        setConnectionStatus('disconnected');
      };

      ws.onclose = () => {
        console.log('WebSocket disconnected');
        setIsConnected(false);
        setConnectionStatus('reconnecting');

        // Clear ping interval
        if (ws.pingInterval) {
          clearInterval(ws.pingInterval);
        }

        // Attempt to reconnect after 2 seconds
        reconnectTimeoutRef.current = setTimeout(() => {
          console.log('Attempting to reconnect...');
          connectWebSocket();
        }, 2000);
      };

      wsRef.current = ws;
    } catch (error) {
      console.error('Error creating WebSocket:', error);
      setConnectionStatus('disconnected');
    }
  };

  const handleStreamingMessage = (message) => {
    if (message.role === 'user') {
      // User message - add directly
      setMessages((prev) => {
        const exists = prev.some(m => m.message_id === message.message_id);
        if (!exists) {
          return [...prev, message];
        }
        return prev;
      });
    } else if (message.role === 'assistant') {
      // Assistant message - handle streaming
      if (message.is_complete) {
        // Final complete message
        streamingMessagesRef.current.delete(message.message_id);

        setMessages((prev) => {
          const index = prev.findIndex(m => m.message_id === message.message_id);
          if (index >= 0) {
            // Update existing message
            const updated = [...prev];
            updated[index] = message;
            return updated;
          } else {
            // Add new message
            return [...prev, message];
          }
        });
      } else {
        // Streaming chunk
        streamingMessagesRef.current.set(message.message_id, message);

        setMessages((prev) => {
          const index = prev.findIndex(m => m.message_id === message.message_id);
          if (index >= 0) {
            // Update existing streaming message
            const updated = [...prev];
            updated[index] = message;
            return updated;
          } else {
            // Add new streaming message
            return [...prev, message];
          }
        });
      }
    }
  };

  const sendMessage = async () => {
    if (!inputMessage.trim() || isSending) return;

    setIsSending(true);

    try {
      // Send message to Python AI service
      await axios.post(`${AI_SERVICE_URL}/chat`, {
        session_id: sessionId,
        message: inputMessage,
        user_id: 'demo_user'
      });

      setInputMessage('');
    } catch (error) {
      console.error('Error sending message:', error);
      alert('Lỗi khi gửi tin nhắn. Vui lòng thử lại.');
    } finally {
      setIsSending(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  const formatTime = (timestamp) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  };

  const getConnectionStatusText = () => {
    switch (connectionStatus) {
      case 'connected':
        return 'Đã kết nối';
      case 'reconnecting':
        return 'Đang kết nối lại...';
      default:
        return 'Mất kết nối';
    }
  };

  return (
    <div className="app">
      <div className="chat-container">
        <div className="chat-header">
          <h1>🤖 AI Streaming Chat Demo</h1>
          <div className="connection-status">
            <div className={`status-dot ${connectionStatus}`}></div>
            <span>{getConnectionStatusText()}</span>
          </div>
        </div>

        <div className="session-info">
          Session ID: <span className="session-id">{sessionId}</span>
        </div>

        <div className="chat-messages">
          {isLoading ? (
            <div className="loading-history">Đang tải lịch sử chat...</div>
          ) : messages.length === 0 ? (
            <div className="loading-history">
              Gửi tin nhắn để bắt đầu chat!
              <br />
              <small>Thử reload trang trong khi AI đang trả lời để thấy tính năng persist history</small>
            </div>
          ) : (
            messages.map((msg, index) => (
              <div key={`${msg.message_id}-${index}`} className={`message ${msg.role}`}>
                <div className="message-header">
                  <span className={`message-role ${msg.role}`}>
                    {msg.role === 'user' ? '👤 Bạn' : '🤖 AI'}
                  </span>
                  <span className="message-time">{formatTime(msg.timestamp)}</span>
                  {!msg.is_complete && (
                    <span className="streaming-indicator"></span>
                  )}
                </div>
                <div className="message-content">
                  {msg.content}
                </div>
              </div>
            ))
          )}
          <div ref={messagesEndRef} />
        </div>

        <div className="chat-input-container">
          <div className="chat-input-wrapper">
            <input
              type="text"
              className="chat-input"
              placeholder="Nhập tin nhắn của bạn..."
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyPress={handleKeyPress}
              disabled={!isConnected || isSending}
            />
            <button
              className="send-button"
              onClick={sendMessage}
              disabled={!isConnected || isSending || !inputMessage.trim()}
            >
              {isSending ? 'Đang gửi...' : 'Gửi'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
