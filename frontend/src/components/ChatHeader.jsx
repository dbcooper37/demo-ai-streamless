import React from 'react';

const ChatHeader = ({ connectionStatus }) => {
  const getConnectionStatusText = () => {
    switch (connectionStatus) {
      case 'connected':
        return 'Đã kết nối';
      case 'reconnecting':
        return 'Đang kết nối lại...';
      case 'error':
        return 'Lỗi kết nối';
      default:
        return 'Mất kết nối';
    }
  };

  return (
    <div className="chat-header">
      <div className="header-content">
        <h1>
          <span className="icon">🤖</span>
          AI Streaming Chat
        </h1>
        <p className="subtitle">Real-time AI responses with WebSocket & Redis</p>
      </div>
      <div className="connection-status">
        <div className={`status-dot ${connectionStatus}`}></div>
        <span className="status-text">{getConnectionStatusText()}</span>
      </div>
    </div>
  );
};

export default ChatHeader;
