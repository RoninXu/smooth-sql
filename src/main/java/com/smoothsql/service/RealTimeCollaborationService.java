package com.smoothsql.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 实时协作服务
 * 
 * 功能包括：
 * 1. WebSocket连接管理
 * 2. 实时查询编辑协作
 * 3. 协作会话管理
 * 4. 用户在线状态跟踪
 * 5. 冲突检测和解决
 * 6. 协作历史记录
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@Service
public class RealTimeCollaborationService {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeCollaborationService.class);

    // 协作会话存储
    private final Map<String, CollaborationSession> collaborationSessions = new ConcurrentHashMap<>();
    
    // 用户连接管理
    private final Map<String, UserConnection> userConnections = new ConcurrentHashMap<>();
    
    // 工作空间管理
    private final Map<String, Workspace> workspaces = new ConcurrentHashMap<>();

    /**
     * 创建协作会话
     * 
     * @param sessionId 会话ID
     * @param workspaceId 工作空间ID
     * @param creatorId 创建者ID
     * @param initialQuery 初始查询
     * @return 协作会话
     */
    public CollaborationSession createCollaborationSession(String sessionId, String workspaceId, 
                                                          String creatorId, String initialQuery) {
        logger.info("创建协作会话 - 会话ID: {}, 工作空间: {}, 创建者: {}", sessionId, workspaceId, creatorId);

        CollaborationSession session = new CollaborationSession();
        session.setSessionId(sessionId);
        session.setWorkspaceId(workspaceId);
        session.setCreatorId(creatorId);
        session.setCurrentQuery(initialQuery);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastModified(LocalDateTime.now());
        session.setStatus("ACTIVE");

        // 添加创建者作为参与者
        CollaborationParticipant creator = new CollaborationParticipant();
        creator.setUserId(creatorId);
        creator.setRole("OWNER");
        creator.setJoinedAt(LocalDateTime.now());
        creator.setStatus("ONLINE");
        session.addParticipant(creator);

        collaborationSessions.put(sessionId, session);
        logger.info("协作会话创建成功 - 会话ID: {}", sessionId);

        return session;
    }

    /**
     * 用户加入协作会话
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param websocketSession WebSocket会话
     * @return 加入结果
     */
    public CollaborationJoinResult joinCollaborationSession(String sessionId, String userId, 
                                                          WebSocketSession websocketSession) {
        logger.info("用户加入协作会话 - 会话ID: {}, 用户ID: {}", sessionId, userId);

        try {
            CollaborationSession session = collaborationSessions.get(sessionId);
            if (session == null) {
                return new CollaborationJoinResult(false, "协作会话不存在", null);
            }

            if (session.getParticipants().size() >= 10) {
                return new CollaborationJoinResult(false, "协作会话人数已满", null);
            }

            // 检查用户是否已在会话中
            boolean alreadyInSession = session.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId));

            CollaborationParticipant participant;
            if (!alreadyInSession) {
                participant = new CollaborationParticipant();
                participant.setUserId(userId);
                participant.setRole("COLLABORATOR");
                participant.setJoinedAt(LocalDateTime.now());
                session.addParticipant(participant);
            } else {
                participant = session.getParticipants().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
            }

            if (participant != null) {
                participant.setStatus("ONLINE");
                participant.setLastActivity(LocalDateTime.now());
            }

            // 注册用户连接
            UserConnection connection = new UserConnection();
            connection.setUserId(userId);
            connection.setSessionId(sessionId);
            connection.setWebSocketSession(websocketSession);
            connection.setConnectedAt(LocalDateTime.now());
            userConnections.put(websocketSession.getId(), connection);

            // 广播用户加入事件
            broadcastUserJoinedEvent(sessionId, userId);

            logger.info("用户成功加入协作会话 - 会话ID: {}, 用户ID: {}, 当前参与者数: {}", 
                       sessionId, userId, session.getParticipants().size());

            return new CollaborationJoinResult(true, "成功加入协作会话", session);

        } catch (Exception e) {
            logger.error("用户加入协作会话异常: {}", e.getMessage(), e);
            return new CollaborationJoinResult(false, "加入协作会话失败: " + e.getMessage(), null);
        }
    }

    /**
     * 处理查询编辑事件
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param operation 操作类型
     * @param position 编辑位置
     * @param content 编辑内容
     * @return 处理结果
     */
    public QueryEditResult processQueryEdit(String sessionId, String userId, String operation, 
                                          int position, String content) {
        logger.debug("处理查询编辑事件 - 会话: {}, 用户: {}, 操作: {}", sessionId, userId, operation);

        try {
            CollaborationSession session = collaborationSessions.get(sessionId);
            if (session == null) {
                return new QueryEditResult(false, "会话不存在", null);
            }

            // 验证用户权限
            if (!hasEditPermission(session, userId)) {
                return new QueryEditResult(false, "无编辑权限", null);
            }

            // 获取当前查询版本
            String currentQuery = session.getCurrentQuery();
            int currentVersion = session.getVersion();

            // 创建编辑操作记录
            QueryEditOperation editOperation = new QueryEditOperation();
            editOperation.setOperationId(UUID.randomUUID().toString());
            editOperation.setUserId(userId);
            editOperation.setOperation(operation);
            editOperation.setPosition(position);
            editOperation.setContent(content);
            editOperation.setTimestamp(LocalDateTime.now());
            editOperation.setVersion(currentVersion);

            // 应用编辑操作
            String newQuery = applyEditOperation(currentQuery, editOperation);
            
            // 冲突检测
            List<ConflictInfo> conflicts = detectConflicts(session, editOperation);
            
            if (!conflicts.isEmpty()) {
                logger.warn("检测到编辑冲突 - 会话: {}, 冲突数: {}", sessionId, conflicts.size());
                return new QueryEditResult(false, "编辑冲突", conflicts);
            }

            // 更新会话状态
            session.setCurrentQuery(newQuery);
            session.setVersion(currentVersion + 1);
            session.setLastModified(LocalDateTime.now());
            session.setLastModifiedBy(userId);
            session.addEditOperation(editOperation);

            // 广播编辑事件
            broadcastQueryEditEvent(sessionId, editOperation, newQuery);

            logger.debug("查询编辑处理完成 - 会话: {}, 新版本: {}", sessionId, session.getVersion());

            return new QueryEditResult(true, "编辑成功", null, newQuery, session.getVersion());

        } catch (Exception e) {
            logger.error("处理查询编辑异常: {}", e.getMessage(), e);
            return new QueryEditResult(false, "编辑失败: " + e.getMessage(), null);
        }
    }

    /**
     * 获取协作会话状态
     * 
     * @param sessionId 会话ID
     * @return 会话状态
     */
    public CollaborationSessionStatus getSessionStatus(String sessionId) {
        CollaborationSession session = collaborationSessions.get(sessionId);
        if (session == null) {
            return null;
        }

        CollaborationSessionStatus status = new CollaborationSessionStatus();
        status.setSessionId(sessionId);
        status.setCurrentQuery(session.getCurrentQuery());
        status.setVersion(session.getVersion());
        status.setParticipantCount(session.getParticipants().size());
        status.setOnlineParticipants(session.getParticipants().stream()
            .filter(p -> "ONLINE".equals(p.getStatus()))
            .map(CollaborationParticipant::getUserId)
            .collect(Collectors.toList()));
        status.setLastModified(session.getLastModified());
        status.setLastModifiedBy(session.getLastModifiedBy());

        return status;
    }

    /**
     * 用户离开协作会话
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    public void leaveCollaborationSession(String sessionId, String userId) {
        logger.info("用户离开协作会话 - 会话ID: {}, 用户ID: {}", sessionId, userId);

        try {
            CollaborationSession session = collaborationSessions.get(sessionId);
            if (session != null) {
                // 更新参与者状态
                session.getParticipants().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .forEach(p -> {
                        p.setStatus("OFFLINE");
                        p.setLastActivity(LocalDateTime.now());
                    });

                // 广播用户离开事件
                broadcastUserLeftEvent(sessionId, userId);

                // 检查是否需要清理会话
                long onlineCount = session.getParticipants().stream()
                    .mapToLong(p -> "ONLINE".equals(p.getStatus()) ? 1 : 0)
                    .sum();

                if (onlineCount == 0) {
                    // 所有用户都离开，设置会话为非活跃状态
                    session.setStatus("INACTIVE");
                    logger.info("协作会话变为非活跃状态 - 会话ID: {}", sessionId);
                }
            }

            // 清理用户连接
            userConnections.values().removeIf(conn -> 
                conn.getSessionId().equals(sessionId) && conn.getUserId().equals(userId));

        } catch (Exception e) {
            logger.error("用户离开协作会话异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建工作空间
     * 
     * @param workspaceId 工作空间ID
     * @param name 工作空间名称
     * @param ownerId 所有者ID
     * @param description 描述
     * @return 工作空间
     */
    public Workspace createWorkspace(String workspaceId, String name, String ownerId, String description) {
        logger.info("创建工作空间 - ID: {}, 名称: {}, 所有者: {}", workspaceId, name, ownerId);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceId(workspaceId);
        workspace.setName(name);
        workspace.setOwnerId(ownerId);
        workspace.setDescription(description);
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setStatus("ACTIVE");

        // 添加所有者为管理员
        WorkspaceMember owner = new WorkspaceMember();
        owner.setUserId(ownerId);
        owner.setRole("ADMIN");
        owner.setJoinedAt(LocalDateTime.now());
        workspace.addMember(owner);

        workspaces.put(workspaceId, workspace);
        logger.info("工作空间创建成功 - ID: {}", workspaceId);

        return workspace;
    }

    /**
     * 邀请用户加入工作空间
     * 
     * @param workspaceId 工作空间ID
     * @param userId 用户ID
     * @param role 角色
     * @param inviterId 邀请者ID
     * @return 邀请结果
     */
    public WorkspaceInviteResult inviteToWorkspace(String workspaceId, String userId, 
                                                 String role, String inviterId) {
        logger.info("邀请用户加入工作空间 - 工作空间: {}, 用户: {}, 角色: {}", workspaceId, userId, role);

        try {
            Workspace workspace = workspaces.get(workspaceId);
            if (workspace == null) {
                return new WorkspaceInviteResult(false, "工作空间不存在");
            }

            // 检查邀请者权限
            if (!hasWorkspaceAdminPermission(workspace, inviterId)) {
                return new WorkspaceInviteResult(false, "无邀请权限");
            }

            // 检查用户是否已在工作空间中
            boolean alreadyMember = workspace.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));

            if (alreadyMember) {
                return new WorkspaceInviteResult(false, "用户已在工作空间中");
            }

            // 添加新成员
            WorkspaceMember member = new WorkspaceMember();
            member.setUserId(userId);
            member.setRole(role);
            member.setJoinedAt(LocalDateTime.now());
            member.setInvitedBy(inviterId);
            workspace.addMember(member);

            logger.info("用户成功加入工作空间 - 工作空间: {}, 用户: {}", workspaceId, userId);
            return new WorkspaceInviteResult(true, "邀请成功");

        } catch (Exception e) {
            logger.error("邀请用户加入工作空间异常: {}", e.getMessage(), e);
            return new WorkspaceInviteResult(false, "邀请失败: " + e.getMessage());
        }
    }

    /**
     * 广播消息到协作会话的所有参与者
     */
    private void broadcastToSession(String sessionId, CollaborationEvent event) {
        userConnections.values().stream()
            .filter(conn -> sessionId.equals(conn.getSessionId()))
            .forEach(conn -> {
                try {
                    // 这里应该通过WebSocket发送消息
                    // websocketHandler.sendMessage(conn.getWebSocketSession(), event);
                    logger.debug("广播消息到用户 - 会话: {}, 用户: {}, 事件: {}", 
                               sessionId, conn.getUserId(), event.getType());
                } catch (Exception e) {
                    logger.error("广播消息异常: {}", e.getMessage(), e);
                }
            });
    }

    private void broadcastUserJoinedEvent(String sessionId, String userId) {
        CollaborationEvent event = new CollaborationEvent();
        event.setType("USER_JOINED");
        event.setSessionId(sessionId);
        event.setUserId(userId);
        event.setTimestamp(LocalDateTime.now());
        broadcastToSession(sessionId, event);
    }

    private void broadcastUserLeftEvent(String sessionId, String userId) {
        CollaborationEvent event = new CollaborationEvent();
        event.setType("USER_LEFT");
        event.setSessionId(sessionId);
        event.setUserId(userId);
        event.setTimestamp(LocalDateTime.now());
        broadcastToSession(sessionId, event);
    }

    private void broadcastQueryEditEvent(String sessionId, QueryEditOperation operation, String newQuery) {
        CollaborationEvent event = new CollaborationEvent();
        event.setType("QUERY_EDITED");
        event.setSessionId(sessionId);
        event.setUserId(operation.getUserId());
        event.setTimestamp(LocalDateTime.now());
        event.setData(Map.of(
            "operation", operation,
            "newQuery", newQuery
        ));
        broadcastToSession(sessionId, event);
    }

    private boolean hasEditPermission(CollaborationSession session, String userId) {
        return session.getParticipants().stream()
            .anyMatch(p -> p.getUserId().equals(userId) && 
                     ("OWNER".equals(p.getRole()) || "COLLABORATOR".equals(p.getRole())));
    }

    private boolean hasWorkspaceAdminPermission(Workspace workspace, String userId) {
        return workspace.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(userId) && "ADMIN".equals(m.getRole()));
    }

    private String applyEditOperation(String currentQuery, QueryEditOperation operation) {
        StringBuilder sb = new StringBuilder(currentQuery);
        
        switch (operation.getOperation()) {
            case "INSERT":
                sb.insert(operation.getPosition(), operation.getContent());
                break;
            case "DELETE":
                int deleteEnd = Math.min(operation.getPosition() + operation.getContent().length(), sb.length());
                sb.delete(operation.getPosition(), deleteEnd);
                break;
            case "REPLACE":
                // 替换操作需要更复杂的逻辑
                break;
        }
        
        return sb.toString();
    }

    private List<ConflictInfo> detectConflicts(CollaborationSession session, QueryEditOperation newOperation) {
        List<ConflictInfo> conflicts = new ArrayList<>();
        
        // 检查是否有同时编辑同一位置的操作
        long recentOperationCount = session.getEditOperations().stream()
            .filter(op -> op.getTimestamp().isAfter(LocalDateTime.now().minusSeconds(5)))
            .filter(op -> !op.getUserId().equals(newOperation.getUserId()))
            .filter(op -> Math.abs(op.getPosition() - newOperation.getPosition()) < 10)
            .count();
        
        if (recentOperationCount > 0) {
            ConflictInfo conflict = new ConflictInfo();
            conflict.setType("CONCURRENT_EDIT");
            conflict.setDescription("检测到同时编辑");
            conflict.setPosition(newOperation.getPosition());
            conflicts.add(conflict);
        }
        
        return conflicts;
    }

    // 内部类定义
    public static class CollaborationSession {
        private String sessionId;
        private String workspaceId;
        private String creatorId;
        private String currentQuery;
        private Integer version = 0;
        private LocalDateTime createdAt;
        private LocalDateTime lastModified;
        private String lastModifiedBy;
        private String status;
        private List<CollaborationParticipant> participants = new ArrayList<>();
        private List<QueryEditOperation> editOperations = new ArrayList<>();

        public void addParticipant(CollaborationParticipant participant) {
            participants.add(participant);
        }

        public void addEditOperation(QueryEditOperation operation) {
            editOperations.add(operation);
            if (editOperations.size() > 1000) {
                editOperations.remove(0);
            }
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
        
        public String getCreatorId() { return creatorId; }
        public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
        
        public String getCurrentQuery() { return currentQuery; }
        public void setCurrentQuery(String currentQuery) { this.currentQuery = currentQuery; }
        
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public LocalDateTime getLastModified() { return lastModified; }
        public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }
        
        public String getLastModifiedBy() { return lastModifiedBy; }
        public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public List<CollaborationParticipant> getParticipants() { return participants; }
        public void setParticipants(List<CollaborationParticipant> participants) { this.participants = participants; }
        
        public List<QueryEditOperation> getEditOperations() { return editOperations; }
        public void setEditOperations(List<QueryEditOperation> editOperations) { this.editOperations = editOperations; }
    }

    public static class CollaborationParticipant {
        private String userId;
        private String role; // OWNER, COLLABORATOR, VIEWER
        private LocalDateTime joinedAt;
        private LocalDateTime lastActivity;
        private String status; // ONLINE, OFFLINE

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        
        public LocalDateTime getJoinedAt() { return joinedAt; }
        public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
        
        public LocalDateTime getLastActivity() { return lastActivity; }
        public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UserConnection {
        private String userId;
        private String sessionId;
        private WebSocketSession webSocketSession;
        private LocalDateTime connectedAt;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public WebSocketSession getWebSocketSession() { return webSocketSession; }
        public void setWebSocketSession(WebSocketSession webSocketSession) { this.webSocketSession = webSocketSession; }
        
        public LocalDateTime getConnectedAt() { return connectedAt; }
        public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }
    }

    public static class Workspace {
        private String workspaceId;
        private String name;
        private String ownerId;
        private String description;
        private LocalDateTime createdAt;
        private String status;
        private List<WorkspaceMember> members = new ArrayList<>();

        public void addMember(WorkspaceMember member) {
            members.add(member);
        }

        // Getters and Setters
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public List<WorkspaceMember> getMembers() { return members; }
        public void setMembers(List<WorkspaceMember> members) { this.members = members; }
    }

    public static class WorkspaceMember {
        private String userId;
        private String role; // ADMIN, MEMBER, VIEWER
        private LocalDateTime joinedAt;
        private String invitedBy;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        
        public LocalDateTime getJoinedAt() { return joinedAt; }
        public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
        
        public String getInvitedBy() { return invitedBy; }
        public void setInvitedBy(String invitedBy) { this.invitedBy = invitedBy; }
    }

    public static class QueryEditOperation {
        private String operationId;
        private String userId;
        private String operation; // INSERT, DELETE, REPLACE
        private Integer position;
        private String content;
        private LocalDateTime timestamp;
        private Integer version;

        // Getters and Setters
        public String getOperationId() { return operationId; }
        public void setOperationId(String operationId) { this.operationId = operationId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        
        public Integer getPosition() { return position; }
        public void setPosition(Integer position) { this.position = position; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

    public static class CollaborationEvent {
        private String type;
        private String sessionId;
        private String userId;
        private LocalDateTime timestamp;
        private Map<String, Object> data;

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }

    public static class CollaborationJoinResult {
        private Boolean success;
        private String message;
        private CollaborationSession session;

        public CollaborationJoinResult(Boolean success, String message, CollaborationSession session) {
            this.success = success;
            this.message = message;
            this.session = session;
        }

        // Getters
        public Boolean getSuccess() { return success; }
        public String getMessage() { return message; }
        public CollaborationSession getSession() { return session; }
    }

    public static class QueryEditResult {
        private Boolean success;
        private String message;
        private List<ConflictInfo> conflicts;
        private String newQuery;
        private Integer newVersion;

        public QueryEditResult(Boolean success, String message, List<ConflictInfo> conflicts) {
            this.success = success;
            this.message = message;
            this.conflicts = conflicts;
        }

        public QueryEditResult(Boolean success, String message, List<ConflictInfo> conflicts, 
                             String newQuery, Integer newVersion) {
            this.success = success;
            this.message = message;
            this.conflicts = conflicts;
            this.newQuery = newQuery;
            this.newVersion = newVersion;
        }

        // Getters
        public Boolean getSuccess() { return success; }
        public String getMessage() { return message; }
        public List<ConflictInfo> getConflicts() { return conflicts; }
        public String getNewQuery() { return newQuery; }
        public Integer getNewVersion() { return newVersion; }
    }

    public static class ConflictInfo {
        private String type;
        private String description;
        private Integer position;

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Integer getPosition() { return position; }
        public void setPosition(Integer position) { this.position = position; }
    }

    public static class CollaborationSessionStatus {
        private String sessionId;
        private String currentQuery;
        private Integer version;
        private Integer participantCount;
        private List<String> onlineParticipants;
        private LocalDateTime lastModified;
        private String lastModifiedBy;

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getCurrentQuery() { return currentQuery; }
        public void setCurrentQuery(String currentQuery) { this.currentQuery = currentQuery; }
        
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        
        public Integer getParticipantCount() { return participantCount; }
        public void setParticipantCount(Integer participantCount) { this.participantCount = participantCount; }
        
        public List<String> getOnlineParticipants() { return onlineParticipants; }
        public void setOnlineParticipants(List<String> onlineParticipants) { this.onlineParticipants = onlineParticipants; }
        
        public LocalDateTime getLastModified() { return lastModified; }
        public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }
        
        public String getLastModifiedBy() { return lastModifiedBy; }
        public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
    }

    public static class WorkspaceInviteResult {
        private Boolean success;
        private String message;

        public WorkspaceInviteResult(Boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        // Getters
        public Boolean getSuccess() { return success; }
        public String getMessage() { return message; }
    }
}