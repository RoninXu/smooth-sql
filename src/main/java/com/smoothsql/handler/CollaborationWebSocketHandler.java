package com.smoothsql.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoothsql.service.RealTimeCollaborationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协作WebSocket处理器
 * 
 * 处理实时协作的WebSocket连接和消息
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@Component
public class CollaborationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(CollaborationWebSocketHandler.class);

    @Autowired
    private RealTimeCollaborationService collaborationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 会话信息存储
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket连接建立 - 会话ID: {}", session.getId());
        
        // 从查询参数获取用户信息
        String userId = getQueryParam(session, "userId");
        String collaborationSessionId = getQueryParam(session, "sessionId");
        
        if (userId == null || collaborationSessionId == null) {
            logger.warn("WebSocket连接缺少必要参数 - userId: {}, sessionId: {}", userId, collaborationSessionId);
            session.close();
            return;
        }

        // 存储会话信息
        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setUserId(userId);
        sessionInfo.setCollaborationSessionId(collaborationSessionId);
        sessionInfo.setConnectedAt(LocalDateTime.now());
        sessions.put(session.getId(), sessionInfo);

        // 加入协作会话
        RealTimeCollaborationService.CollaborationJoinResult joinResult = 
            collaborationService.joinCollaborationSession(collaborationSessionId, userId, session);

        if (!joinResult.getSuccess()) {
            logger.warn("用户加入协作会话失败 - 用户: {}, 会话: {}, 原因: {}", 
                       userId, collaborationSessionId, joinResult.getMessage());
            
            // 发送错误消息并关闭连接
            WebSocketMessage errorMessage = new WebSocketMessage();
            errorMessage.setType("ERROR");
            errorMessage.setData(Map.of("message", joinResult.getMessage()));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
            session.close();
            return;
        }

        // 发送连接成功消息
        WebSocketMessage successMessage = new WebSocketMessage();
        successMessage.setType("CONNECTION_ESTABLISHED");
        successMessage.setData(Map.of(
            "sessionId", collaborationSessionId,
            "userId", userId,
            "message", "成功连接到协作会话"
        ));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(successMessage)));

        logger.info("用户成功连接到协作会话 - 用户: {}, 会话: {}", userId, collaborationSessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.debug("收到WebSocket消息 - 会话: {}, 消息长度: {}", session.getId(), message.getPayload().length());

        try {
            SessionInfo sessionInfo = sessions.get(session.getId());
            if (sessionInfo == null) {
                logger.warn("未找到会话信息 - WebSocket会话: {}", session.getId());
                return;
            }

            // 解析消息
            WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);
            
            // 根据消息类型处理
            switch (wsMessage.getType()) {
                case "QUERY_EDIT":
                    handleQueryEditMessage(session, sessionInfo, wsMessage);
                    break;
                case "CURSOR_POSITION":
                    handleCursorPositionMessage(session, sessionInfo, wsMessage);
                    break;
                case "HEARTBEAT":
                    handleHeartbeatMessage(session, sessionInfo, wsMessage);
                    break;
                default:
                    logger.warn("未知消息类型: {}", wsMessage.getType());
            }

        } catch (Exception e) {
            logger.error("处理WebSocket消息异常: {}", e.getMessage(), e);
            
            // 发送错误响应
            WebSocketMessage errorMessage = new WebSocketMessage();
            errorMessage.setType("ERROR");
            errorMessage.setData(Map.of("message", "消息处理失败: " + e.getMessage()));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket连接关闭 - 会话: {}, 状态: {}", session.getId(), status.toString());

        SessionInfo sessionInfo = sessions.remove(session.getId());
        if (sessionInfo != null) {
            // 通知协作服务用户离开
            collaborationService.leaveCollaborationSession(
                sessionInfo.getCollaborationSessionId(), 
                sessionInfo.getUserId()
            );
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket传输错误 - 会话: {}, 错误: {}", session.getId(), exception.getMessage(), exception);
        
        // 清理会话信息
        sessions.remove(session.getId());
    }

    /**
     * 发送消息给指定会话
     */
    public void sendMessage(WebSocketSession session, Object message) {
        try {
            if (session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (Exception e) {
            logger.error("发送WebSocket消息失败: {}", e.getMessage(), e);
        }
    }

    private void handleQueryEditMessage(WebSocketSession session, SessionInfo sessionInfo, 
                                      WebSocketMessage message) throws Exception {
        logger.debug("处理查询编辑消息 - 用户: {}", sessionInfo.getUserId());

        Map<String, Object> data = message.getData();
        String operation = (String) data.get("operation");
        Integer position = (Integer) data.get("position");
        String content = (String) data.get("content");

        // 处理编辑操作
        RealTimeCollaborationService.QueryEditResult editResult = collaborationService.processQueryEdit(
            sessionInfo.getCollaborationSessionId(),
            sessionInfo.getUserId(),
            operation,
            position != null ? position : 0,
            content != null ? content : ""
        );

        // 发送编辑结果
        WebSocketMessage response = new WebSocketMessage();
        if (editResult.getSuccess()) {
            response.setType("QUERY_EDIT_SUCCESS");
            response.setData(Map.of(
                "newQuery", editResult.getNewQuery(),
                "version", editResult.getNewVersion()
            ));
        } else {
            response.setType("QUERY_EDIT_ERROR");
            response.setData(Map.of(
                "message", editResult.getMessage(),
                "conflicts", editResult.getConflicts() != null ? editResult.getConflicts() : Map.of()
            ));
        }

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleCursorPositionMessage(WebSocketSession session, SessionInfo sessionInfo, 
                                           WebSocketMessage message) throws Exception {
        logger.debug("处理光标位置消息 - 用户: {}", sessionInfo.getUserId());

        // 广播光标位置给其他协作者
        // 这里可以通过协作服务来广播
        Map<String, Object> cursorData = message.getData();
        cursorData.put("userId", sessionInfo.getUserId());
        
        WebSocketMessage broadcastMessage = new WebSocketMessage();
        broadcastMessage.setType("CURSOR_POSITION_UPDATE");
        broadcastMessage.setData(cursorData);
        
        // 通过协作服务广播（实际实现中需要获取其他连接）
        logger.debug("广播光标位置更新 - 用户: {}, 位置: {}", 
                    sessionInfo.getUserId(), cursorData.get("position"));
    }

    private void handleHeartbeatMessage(WebSocketSession session, SessionInfo sessionInfo, 
                                      WebSocketMessage message) throws Exception {
        logger.debug("处理心跳消息 - 用户: {}", sessionInfo.getUserId());

        // 更新最后活跃时间
        sessionInfo.setLastHeartbeat(LocalDateTime.now());

        // 发送心跳响应
        WebSocketMessage response = new WebSocketMessage();
        response.setType("HEARTBEAT_RESPONSE");
        response.setData(Map.of("timestamp", LocalDateTime.now().toString()));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private String getQueryParam(WebSocketSession session, String paramName) {
        try {
            String query = session.getUri().getQuery();
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                        return keyValue[1];
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析查询参数异常: {}", e.getMessage());
        }
        return null;
    }

    // 内部类
    public static class WebSocketMessage {
        private String type;
        private Map<String, Object> data;
        private LocalDateTime timestamp = LocalDateTime.now();

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class SessionInfo {
        private String userId;
        private String collaborationSessionId;
        private LocalDateTime connectedAt;
        private LocalDateTime lastHeartbeat;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getCollaborationSessionId() { return collaborationSessionId; }
        public void setCollaborationSessionId(String collaborationSessionId) { 
            this.collaborationSessionId = collaborationSessionId; 
        }
        
        public LocalDateTime getConnectedAt() { return connectedAt; }
        public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }
        
        public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    }
}