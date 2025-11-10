import { useState, useEffect, useRef, useCallback } from 'react';

const RECONNECT_DELAY = 2000;
const PING_INTERVAL = 30000;

/**
 * Custom hook for WebSocket connection management
 */
export const useWebSocket = (url, sessionId, onMessage) => {
  const [isConnected, setIsConnected] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState('disconnected');

  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const pingIntervalRef = useRef(null);
  const manualDisconnectRef = useRef(false);
  const onMessageRef = useRef(onMessage);

  // Update the ref when onMessage changes
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  const connect = useCallback(() => {
    // Prevent multiple connections
    if (wsRef.current?.readyState === WebSocket.OPEN ||
        wsRef.current?.readyState === WebSocket.CONNECTING) {
      console.log('WebSocket already connected or connecting');
      return;
    }

    // Reset manual disconnect flag when connecting
    manualDisconnectRef.current = false;

    // Clear any pending reconnection
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    try {
      const ws = new WebSocket(`${url}?session_id=${sessionId}`);

      ws.onopen = () => {
        console.log('WebSocket connected');
        setIsConnected(true);
        setConnectionStatus('connected');

        // Setup ping interval to keep connection alive
        pingIntervalRef.current = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'ping' }));
          }
        }, PING_INTERVAL);
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          onMessageRef.current(data);
        } catch (error) {
          console.error('Error parsing WebSocket message:', error);
        }
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        setConnectionStatus('error');
      };

      ws.onclose = () => {
        console.log('WebSocket disconnected', { manual: manualDisconnectRef.current });
        setIsConnected(false);

        // Clear ping interval
        if (pingIntervalRef.current) {
          clearInterval(pingIntervalRef.current);
          pingIntervalRef.current = null;
        }

        // Clear the websocket ref
        if (wsRef.current === ws) {
          wsRef.current = null;
        }

        // Only auto-reconnect if not manually disconnected
        if (!manualDisconnectRef.current) {
          setConnectionStatus('reconnecting');
          reconnectTimeoutRef.current = setTimeout(() => {
            console.log('Attempting to reconnect...');
            connect();
          }, RECONNECT_DELAY);
        } else {
          setConnectionStatus('disconnected');
          console.log('Manual disconnect - not reconnecting');
        }
      };

      wsRef.current = ws;
    } catch (error) {
      console.error('Error creating WebSocket:', error);
      setConnectionStatus('error');
    }
  }, [url, sessionId]);

  const cleanup = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    if (pingIntervalRef.current) {
      clearInterval(pingIntervalRef.current);
      pingIntervalRef.current = null;
    }

    if (wsRef.current) {
      // Close the connection if it's open
      if (wsRef.current.readyState === WebSocket.OPEN ||
          wsRef.current.readyState === WebSocket.CONNECTING) {
        wsRef.current.close();
      }
      // Don't set wsRef to null immediately to avoid race conditions
      // The onclose handler will handle state updates
    }
  }, []);

  const disconnect = useCallback(() => {
    console.log('Manual disconnect requested');
    // Set manual disconnect flag BEFORE calling cleanup
    manualDisconnectRef.current = true;

    // Update status immediately for better UX
    setConnectionStatus('disconnected');

    // Clean up the connection
    cleanup();
  }, [cleanup]);

  useEffect(() => {
    connect();
    return () => {
      // Cleanup on unmount - don't set manual flag to prevent reconnect attempts
      cleanup();
      // Clear the ref on unmount
      wsRef.current = null;
    };
  }, [connect, cleanup]);

  return {
    isConnected,
    connectionStatus,
    reconnect: connect,
    disconnect
  };
};
