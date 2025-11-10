import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useWebSocket } from './hooks/useWebSocket';
import { useChat } from './hooks/useChat';
import ChatHeader from './components/ChatHeader';
import MessageList from './components/MessageList';
import ChatInput from './components/ChatInput';

const WEBSOCKET_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws/chat';
// All API calls now go through Java Backend (port 8080), not Python AI service directly
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

function App() {
  // Session management
  const [sessionId] = useState(() => {
    const saved = localStorage.getItem('chatSessionId');
    return saved || `session_${Date.now()}`;
  });

  // Chat state
  const { messages, handleStreamingMessage, loadHistory, addUserMessage } = useChat();
  const [isLoading, setIsLoading] = useState(true);
  const [isSending, setIsSending] = useState(false);
  const [streamingMessageId, setStreamingMessageId] = useState(null);

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
      // Track streaming message ID for cancel functionality
      if (data.data.role === 'assistant' && !data.data.is_complete) {
        setStreamingMessageId(data.data.message_id);
      } else if (data.data.is_complete) {
        setStreamingMessageId(null);
        setIsSending(false);
      }
    } else if (data.type === 'welcome') {
      console.log('Welcome message received');
      setIsLoading(false);
    } else if (data.type === 'chunk') {
      // Handle enhanced streaming chunks
      handleStreamingMessage(data.data);
      if (data.data.role === 'assistant') {
        setStreamingMessageId(data.data.message_id);
      }
    } else if (data.type === 'complete') {
      // Handle enhanced complete message
      handleStreamingMessage(data.data);
      setStreamingMessageId(null);
      setIsSending(false);
    } else if (data.type === 'cancelled') {
      console.log('Streaming cancelled:', data.message_id);
      setStreamingMessageId(null);
      setIsSending(false);
    } else if (data.type === 'error') {
      console.error('WebSocket error:', data.error);
      setIsLoading(false);
      setStreamingMessageId(null);
      setIsSending(false);
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
      // Generate a temporary message ID for optimistic UI update
      const tempMessageId = `temp_${Date.now()}`;
      
      // Immediately add user message to UI (optimistic update)
      addUserMessage(tempMessageId, messageText, sessionId, 'demo_user');
      
      const response = await axios.post(`${API_URL}/chat`, {
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
      setIsSending(false);
    }
  };

  // Cancel streaming message
  const cancelMessage = async () => {
    if (!streamingMessageId) return;

    try {
      await axios.post(`${API_URL}/cancel`, {
        session_id: sessionId,
        message_id: streamingMessageId
      });
      
      console.log('Cancel request sent for message:', streamingMessageId);
    } catch (error) {
      console.error('Error cancelling message:', error);
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
          onCancel={cancelMessage}
          isConnected={isConnected}
          isSending={isSending}
          isStreaming={streamingMessageId !== null}
        />
      </div>
    </div>
  );
}

export default App;
