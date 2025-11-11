import { useState, useRef, useCallback } from 'react';

/**
 * Custom hook for managing chat messages and streaming
 */
export const useChat = () => {
  const [messages, setMessages] = useState([]);
  const streamingMessagesRef = useRef(new Map());
  const pendingUserMessageIdsRef = useRef([]);
  const pendingUserReplacementsRef = useRef(new Map());
  const replacePendingUserMessage = useCallback((currentMessages, incomingMessage) => {
    if (!currentMessages || currentMessages.length === 0) {
      return null;
    }

    const pendingIds = pendingUserMessageIdsRef.current;

    for (let i = 0; i < pendingIds.length; i++) {
      const tempId = pendingIds[i];
      const index = currentMessages.findIndex(m => m?.message_id === tempId);
      if (index !== -1) {
        const updated = [...currentMessages];
        updated[index] = {
          ...updated[index],
          ...incomingMessage,
          is_complete: true
        };

        pendingIds.splice(i, 1);
        pendingUserReplacementsRef.current.delete(tempId);
        return updated;
      }
    }

    const fallbackIndex = currentMessages.findIndex(
      (m) =>
        m?.role === 'user' &&
        typeof m?.message_id === 'string' &&
        m.message_id.startsWith('temp_')
    );

    if (fallbackIndex !== -1) {
      const updated = [...currentMessages];
      const fallbackId = updated[fallbackIndex].message_id;

      updated[fallbackIndex] = {
        ...updated[fallbackIndex],
        ...incomingMessage,
        is_complete: true
      };

      const queueIndex = pendingIds.indexOf(fallbackId);
      if (queueIndex !== -1) {
        pendingIds.splice(queueIndex, 1);
      }
      pendingUserReplacementsRef.current.delete(fallbackId);
      return updated;
    }

    return null;
  }, []);

  const handleStreamingMessage = useCallback((message) => {
    if (message.role === 'user') {
      // User message - replace pending optimistic message or append if unmatched
      setMessages((prev) => {
        if (!message?.message_id) {
          return prev;
        }

        // Update if the authoritative message already exists
        const existingIndex = prev.findIndex(m => m?.message_id === message.message_id);
        if (existingIndex >= 0) {
          const updatedExisting = [...prev];
          updatedExisting[existingIndex] = {
            ...updatedExisting[existingIndex],
            ...message,
            is_complete: true
          };
          return updatedExisting;
        }

        const replaced = replacePendingUserMessage(prev, message);
        if (replaced) {
          return replaced;
        }

        // Optimistic entry not yet in state - store for later replacement
        if (pendingUserMessageIdsRef.current.length > 0) {
          const tempId = pendingUserMessageIdsRef.current[0];
          pendingUserReplacementsRef.current.set(tempId, message);
          return prev;
        }

        // No pending optimistic message, append as new
        return [...prev, { ...message, is_complete: true }];
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
  }, [replacePendingUserMessage]);

  const loadHistory = useCallback((historyMessages) => {
    setMessages(historyMessages);
    streamingMessagesRef.current.clear();
    pendingUserMessageIdsRef.current = [];
    pendingUserReplacementsRef.current = new Map();
  }, []);

  const clearMessages = useCallback(() => {
    setMessages([]);
    streamingMessagesRef.current.clear();
    pendingUserMessageIdsRef.current = [];
    pendingUserReplacementsRef.current = new Map();
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
    pendingUserMessageIdsRef.current.push(messageId);

    setMessages((prev) => {
      const updated = [...prev, userMessage];
      const replacement = pendingUserReplacementsRef.current.get(messageId);

      if (replacement) {
        const index = updated.findIndex(m => m.message_id === messageId);
        if (index >= 0) {
          updated[index] = {
            ...updated[index],
            ...replacement,
            is_complete: true
          };
        }
        pendingUserReplacementsRef.current.delete(messageId);

        if (pendingUserMessageIdsRef.current[0] === messageId) {
          pendingUserMessageIdsRef.current.shift();
        } else {
          const pendingIndex = pendingUserMessageIdsRef.current.indexOf(messageId);
          if (pendingIndex >= 0) {
            pendingUserMessageIdsRef.current.splice(pendingIndex, 1);
          }
        }
      }

      return updated;
    });
  }, []);

  return {
    messages,
    handleStreamingMessage,
    loadHistory,
    clearMessages,
    addUserMessage
  };
};
