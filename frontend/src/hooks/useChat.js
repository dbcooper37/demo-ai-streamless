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
            updated[index] = {
              ...message,
              is_complete: true
            };
            return updated;
          } else {
            // Add new message (shouldn't happen normally)
            return [...prev, message];
          }
        });
      } else {
        // Streaming chunk - use accumulated content from server
        // NOTE: The server already sends accumulated content
        setMessages((prev) => {
          // IMPORTANT: Find by message_id to update the CORRECT message
          // DO NOT update if not found - this prevents replacing wrong messages
          const index = prev.findIndex(m => m.message_id === message.message_id);
          
          if (index >= 0) {
            // Update existing streaming message
            const updated = [...prev];
            updated[index] = {
              ...updated[index], // Keep existing properties
              content: message.content || '',
              chunk: message.chunk || '',
              timestamp: message.timestamp,
              is_complete: false
            };
            streamingMessagesRef.current.set(message.message_id, updated[index]);
            return updated;
          } else {
            // First chunk - add new streaming message
            const newMessage = {
              ...message,
              content: message.content || '',
              chunk: message.chunk || '',
              is_complete: false
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

  const addUserMessage = useCallback((messageId, content, sessionId, userId) => {
    const userMessage = {
      message_id: messageId,
      session_id: sessionId,
      user_id: userId,
      role: 'user',
      content: content,
      timestamp: Date.now(),
      is_complete: true
    };
    
    setMessages((prev) => [...prev, userMessage]);
  }, []);

  return {
    messages,
    handleStreamingMessage,
    loadHistory,
    clearMessages,
    addUserMessage
  };
};
