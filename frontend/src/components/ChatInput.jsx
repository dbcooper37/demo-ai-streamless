import React, { useState } from 'react';

const ChatInput = ({ onSend, onCancel, isConnected, isSending, isStreaming }) => {
  const [inputMessage, setInputMessage] = useState('');

  const handleSend = () => {
    if (!inputMessage.trim() || isSending || !isConnected || isStreaming) return;
    
    onSend(inputMessage);
    setInputMessage('');
  };

  const handleCancel = () => {
    if (!isStreaming) return;
    onCancel();
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
              : isStreaming
              ? "AI đang trả lời..."
              : "Nhập tin nhắn của bạn..."
          }
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          onKeyPress={handleKeyPress}
          disabled={!isConnected || isSending || isStreaming}
        />
        {isStreaming ? (
          <button
            className="cancel-button"
            onClick={handleCancel}
            title="Hủy tin nhắn đang streaming"
          >
            <span className="cancel-icon">⏹️</span>
            Hủy
          </button>
        ) : (
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
        )}
      </div>
    </div>
  );
};

export default ChatInput;
