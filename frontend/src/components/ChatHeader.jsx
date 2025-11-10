import React from 'react';

const ChatHeader = ({ connectionStatus, isConnected, onReconnect, onDisconnect }) => {
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
      <div className="connection-controls">
        <div className="connection-status">
          <div className={`status-dot ${connectionStatus}`}></div>
          <span className="status-text">{getConnectionStatusText()}</span>
        </div>
        <div className="connection-buttons">
          <button
            className="ws-control-btn disconnect-btn"
            onClick={onDisconnect}
            disabled={!isConnected}
            title="Ngắt kết nối WebSocket"
          >
            🔌 Disconnect
          </button>
          <button
            className="ws-control-btn reconnect-btn"
            onClick={onReconnect}
            disabled={isConnected}
            title="Kết nối lại WebSocket"
          >
            🔄 Reconnect
          </button>
        </div>
      </div>
    </div>
  );
};

export default ChatHeader;
