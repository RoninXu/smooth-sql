package com.smoothsql.config;

import com.smoothsql.handler.CollaborationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 
 * 配置实时协作功能的WebSocket端点
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private CollaborationWebSocketHandler collaborationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册协作WebSocket端点
        registry.addHandler(collaborationWebSocketHandler, "/api/v3/collaboration/websocket")
                .setAllowedOrigins("*"); // 生产环境中应该限制具体域名
    }
}