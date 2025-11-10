import React from 'react';

const Message = ({ message }) => {
  const formatTime = (timestamp) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('vi-VN', { 
      hour: '2-digit', 
      minute: '2-digit',
      second: '2-digit'
    });
  };

  return (
    <div className={`message ${message.role}`}>
      <div className="message-avatar">
        {message.role === 'user' ? '👤' : '🤖'}
      </div>
      <div className="message-body">
        <div className="message-header">
          <span className={`message-role ${message.role}`}>
            {message.role === 'user' ? 'Bạn' : 'AI Assistant'}
          </span>
          <span className="message-time">{formatTime(message.timestamp)}</span>
          {!message.is_complete && (
            <span className="streaming-indicator" title="Đang nhận...">
              <span className="dot"></span>
              <span className="dot"></span>
              <span className="dot"></span>
            </span>
          )}
        </div>
        <div className="message-content">
          {message.content}
        </div>
      </div>
    </div>
  );
};

export default Message;
