package com.demo.websocket.config;

import com.demo.websocket.handler.ChatWebSocketHandler;
import com.demo.websocket.handler.EnhancedChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final EnhancedChatWebSocketHandler enhancedChatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                          @Qualifier("enhancedChatWebSocketHandler")
                          EnhancedChatWebSocketHandler enhancedChatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.enhancedChatWebSocketHandler = enhancedChatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Legacy handler for backward compatibility
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOrigins("*"); // In production, specify exact origins

        // New enhanced handler with recovery mechanism
        registry.addHandler(enhancedChatWebSocketHandler, "/ws/chat/v2")
                .setAllowedOrigins("*"); // In production, specify exact origins
    }
}
