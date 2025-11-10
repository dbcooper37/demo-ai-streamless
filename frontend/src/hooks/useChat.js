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
            // Update existing message with final content
            const updated = [...prev];
            updated[index] = message;
            return updated;
          } else {
            // Add new message
            return [...prev, message];
          }
        });
      } else {
        // Streaming chunk - use accumulated content from server
        // NOTE: The server (Python AI service and ChatOrchestrator) already sends
        // accumulated content in the 'content' field. We should NOT accumulate again
        // on the client side to avoid duplicate/overlapping text.
        setMessages((prev) => {
          const index = prev.findIndex(m => m.message_id === message.message_id);
          if (index >= 0) {
            // Update message with latest accumulated content from server
            const updated = [...prev];
            updated[index] = {
              ...message,
              content: message.content || '',
              chunk: message.chunk || ''
            };
            streamingMessagesRef.current.set(message.message_id, updated[index]);
            return updated;
          } else {
            // Add new streaming message with initial content
            const newMessage = {
              ...message,
              content: message.content || '',
              chunk: message.chunk || ''
            };
            streamingMessagesRef.current.set(message.message_id, newMessage);
            return [...prev, newMessage];
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
