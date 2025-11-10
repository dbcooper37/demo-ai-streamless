import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useWebSocket } from './hooks/useWebSocket';
import { useChat } from './hooks/useChat';
import ChatHeader from './components/ChatHeader';
import MessageList from './components/MessageList';
import ChatInput from './components/ChatInput';

const WEBSOCKET_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws/chat';
const AI_SERVICE_URL = import.meta.env.VITE_API_URL || '/api';

function App() {
  // Session management
  const [sessionId] = useState(() => {
    const saved = localStorage.getItem('chatSessionId');
    return saved || `session_${Date.now()}`;
  });

  // Chat state
  const { messages, handleStreamingMessage, loadHistory } = useChat();
  const [isLoading, setIsLoading] = useState(true);
  const [isSending, setIsSending] = useState(false);

  // Save session ID to localStorage
  useEffect(() => {
    localStorage.setItem('chatSessionId', sessionId);
  }, [sessionId]);

  // WebSocket message handler
  const handleWebSocketMessage = (data) => {
    if (data.type === 'history') {
      console.log('Received history:', data.messages.length, 'messages');
      loadHistory(data.messages);
      setIsLoading(false);
    } else if (data.type === 'message') {
      handleStreamingMessage(data.data);
    } else if (data.type === 'welcome') {
      console.log('Welcome message received');
      setIsLoading(false);
    } else if (data.type === 'chunk') {
      // Handle enhanced streaming chunks
      handleStreamingMessage(data.data);
    } else if (data.type === 'complete') {
      // Handle enhanced complete message
      handleStreamingMessage(data.data);
    } else if (data.type === 'error') {
      console.error('WebSocket error:', data.error);
      setIsLoading(false);
    }
  };

  // WebSocket connection
  const { isConnected, connectionStatus, reconnect, disconnect } = useWebSocket(
    WEBSOCKET_URL,
    sessionId,
    handleWebSocketMessage
  );

  // Send message to AI service
  const sendMessage = async (messageText) => {
    if (!messageText.trim() || isSending) return;

    setIsSending(true);

    try {
      const response = await axios.post(`${AI_SERVICE_URL}/chat`, {
        session_id: sessionId,
        message: messageText,
        user_id: 'demo_user'
      });

      console.log('Message sent:', response.data);
    } catch (error) {
      console.error('Error sending message:', error);
      
      // Show user-friendly error message
      const errorMessage = error.response?.data?.detail || 'Lỗi khi gửi tin nhắn';
      alert(`Lỗi: ${errorMessage}\nVui lòng thử lại.`);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="app">
      <div className="chat-container">
        <ChatHeader
          connectionStatus={connectionStatus}
          isConnected={isConnected}
          onReconnect={reconnect}
          onDisconnect={disconnect}
        />
        
        <div className="session-info">
          <span className="session-label">Session ID:</span>
          <span className="session-id">{sessionId}</span>
          <button 
            className="new-session-btn"
            onClick={() => {
              localStorage.removeItem('chatSessionId');
              window.location.reload();
            }}
            title="Bắt đầu phiên chat mới"
          >
            🔄 New Session
          </button>
        </div>

        <MessageList messages={messages} isLoading={isLoading} />
        
        <ChatInput
          onSend={sendMessage}
          isConnected={isConnected}
          isSending={isSending}
        />
      </div>
    </div>
  );
}

export default App;
