import React, { useEffect, useRef } from 'react';
import Message from './Message';

const MessageList = ({ messages, isLoading }) => {
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  if (isLoading) {
    return (
      <div className="chat-messages">
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Đang tải lịch sử chat...</p>
        </div>
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className="chat-messages">
        <div className="empty-state">
          <div className="empty-icon">💬</div>
          <h3>Chào mừng đến với AI Chat!</h3>
          <p>Gửi tin nhắn để bắt đầu cuộc trò chuyện</p>
          <div className="hints">
            <p className="hint">💡 Thử reload trang trong khi AI đang trả lời</p>
            <p className="hint">🔄 Lịch sử chat sẽ được lưu tự động</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="chat-messages">
      {messages.map((msg, index) => {
        // Skip messages with missing required fields
        if (!msg || !msg.message_id) {
          console.warn('Skipping invalid message at index:', index, msg);
          return null;
        }
        return <Message key={`${msg.message_id}-${index}`} message={msg} />;
      })}
      <div ref={messagesEndRef} />
    </div>
  );
};

export default MessageList;
