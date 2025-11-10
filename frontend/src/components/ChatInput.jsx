import React, { useState } from 'react';

const ChatInput = ({ onSend, isConnected, isSending }) => {
  const [inputMessage, setInputMessage] = useState('');

  const handleSend = () => {
    if (!inputMessage.trim() || isSending || !isConnected) return;
    
    onSend(inputMessage);
    setInputMessage('');
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="chat-input-container">
      <div className="chat-input-wrapper">
        <input
          type="text"
          className="chat-input"
          placeholder={
            !isConnected 
              ? "Đang kết nối..." 
              : "Nhập tin nhắn của bạn..."
          }
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          onKeyPress={handleKeyPress}
          disabled={!isConnected || isSending}
        />
        <button
          className="send-button"
          onClick={handleSend}
          disabled={!isConnected || isSending || !inputMessage.trim()}
        >
          {isSending ? (
            <>
              <span className="button-spinner"></span>
              Đang gửi...
            </>
          ) : (
            <>
              <span className="send-icon">📤</span>
              Gửi
            </>
          )}
        </button>
      </div>
    </div>
  );
};

export default ChatInput;
