import { useState, useRef, useCallback } from 'react';

/**
 * Custom hook for managing chat messages and streaming
 */
export const useChat = () => {
  const [messages, setMessages] = useState([]);
  const streamingMessagesRef = useRef(new Map());

  const handleStreamingMessage = useCallback((message) => {
    if (message.role === 'user') {
      // User message - add directly if not exists
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
  }, []);

  const loadHistory = useCallback((historyMessages) => {
    setMessages(historyMessages);
  }, []);

  const clearMessages = useCallback(() => {
    setMessages([]);
    streamingMessagesRef.current.clear();
  }, []);

  return {
    messages,
    handleStreamingMessage,
    loadHistory,
    clearMessages
  };
};
