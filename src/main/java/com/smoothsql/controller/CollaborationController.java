package com.smoothsql.controller;

import com.smoothsql.service.RealTimeCollaborationService;
import com.smoothsql.service.UserPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

/**
 * 实时协作控制器
 * 
 * 提供协作相关的REST API接口
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@RestController
@RequestMapping("/api/v3/collaboration")
@CrossOrigin(origins = "*")
public class CollaborationController {

    private static final Logger logger = LoggerFactory.getLogger(CollaborationController.class);

    @Autowired
    private RealTimeCollaborationService collaborationService;

    @Autowired
    private UserPermissionService permissionService;

    /**
     * 创建协作会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        logger.info("创建协作会话请求 - 用户: {}, 工作空间: {}", request.getUserId(), request.getWorkspaceId());

        try {
            // 权限验证
            if (!permissionService.hasPermission(request.getUserId(), "QUERY_DATA")) {
                return ResponseEntity.status(403).body(new CreateSessionResponse(
                    false, "无权限创建协作会话", null
                ));
            }

            String sessionId = UUID.randomUUID().toString();
            
            RealTimeCollaborationService.CollaborationSession session = collaborationService.createCollaborationSession(
                sessionId,
                request.getWorkspaceId(),
                request.getUserId(),
                request.getInitialQuery()
            );

            logger.info("协作会话创建成功 - 会话ID: {}", sessionId);

            return ResponseEntity.ok(new CreateSessionResponse(true, "协作会话创建成功", session));

        } catch (Exception e) {
            logger.error("创建协作会话异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new CreateSessionResponse(
                false, "创建协作会话失败: " + e.getMessage(), null
            ));
        }
    }

    /**
     * 获取协作会话状态
     */
    @GetMapping("/sessions/{sessionId}/status")
    public ResponseEntity<RealTimeCollaborationService.CollaborationSessionStatus> getSessionStatus(
            @PathVariable String sessionId,
            @RequestParam String userId) {
        
        logger.debug("获取协作会话状态 - 会话: {}, 用户: {}", sessionId, userId);

        try {
            // 权限验证
            if (!permissionService.hasPermission(userId, "QUERY_DATA")) {
                return ResponseEntity.status(403).build();
            }

            RealTimeCollaborationService.CollaborationSessionStatus status = 
                collaborationService.getSessionStatus(sessionId);

            if (status == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("获取协作会话状态异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 创建工作空间
     */
    @PostMapping("/workspaces")
    public ResponseEntity<CreateWorkspaceResponse> createWorkspace(@Valid @RequestBody CreateWorkspaceRequest request) {
        logger.info("创建工作空间请求 - 用户: {}, 名称: {}", request.getOwnerId(), request.getName());

        try {
            // 权限验证
            if (!permissionService.hasPermission(request.getOwnerId(), "QUERY_DATA")) {
                return ResponseEntity.status(403).body(new CreateWorkspaceResponse(
                    false, "无权限创建工作空间", null
                ));
            }

            String workspaceId = UUID.randomUUID().toString();
            
            RealTimeCollaborationService.Workspace workspace = collaborationService.createWorkspace(
                workspaceId,
                request.getName(),
                request.getOwnerId(),
                request.getDescription()
            );

            logger.info("工作空间创建成功 - 工作空间ID: {}", workspaceId);

            return ResponseEntity.ok(new CreateWorkspaceResponse(true, "工作空间创建成功", workspace));

        } catch (Exception e) {
            logger.error("创建工作空间异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new CreateWorkspaceResponse(
                false, "创建工作空间失败: " + e.getMessage(), null
            ));
        }
    }

    /**
     * 邀请用户加入工作空间
     */
    @PostMapping("/workspaces/{workspaceId}/invite")
    public ResponseEntity<RealTimeCollaborationService.WorkspaceInviteResult> inviteToWorkspace(
            @PathVariable String workspaceId,
            @Valid @RequestBody WorkspaceInviteRequest request) {
        
        logger.info("邀请用户加入工作空间 - 工作空间: {}, 被邀请用户: {}, 邀请者: {}", 
                   workspaceId, request.getUserId(), request.getInviterId());

        try {
            // 权限验证
            if (!permissionService.hasPermission(request.getInviterId(), "QUERY_DATA")) {
                return ResponseEntity.status(403).body(new RealTimeCollaborationService.WorkspaceInviteResult(
                    false, "无权限邀请用户"
                ));
            }

            RealTimeCollaborationService.WorkspaceInviteResult result = collaborationService.inviteToWorkspace(
                workspaceId,
                request.getUserId(),
                request.getRole(),
                request.getInviterId()
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("邀请用户加入工作空间异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new RealTimeCollaborationService.WorkspaceInviteResult(
                false, "邀请失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取用户的协作统计信息
     */
    @GetMapping("/users/{userId}/stats")
    public ResponseEntity<CollaborationStats> getUserCollaborationStats(@PathVariable String userId) {
        logger.debug("获取用户协作统计 - 用户: {}", userId);

        try {
            // 权限验证
            if (!permissionService.hasPermission(userId, "QUERY_DATA")) {
                return ResponseEntity.status(403).build();
            }

            CollaborationStats stats = new CollaborationStats();
            stats.setUserId(userId);
            // 这里应该从实际数据中计算统计信息
            stats.setTotalSessions(5);
            stats.setActiveSessions(2);
            stats.setTotalWorkspaces(3);
            stats.setCollaborations(12);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("获取用户协作统计异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    // 请求和响应DTO类
    public static class CreateSessionRequest {
        private String userId;
        private String workspaceId;
        private String initialQuery;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
        
        public String getInitialQuery() { return initialQuery; }
        public void setInitialQuery(String initialQuery) { this.initialQuery = initialQuery; }
    }

    public static class CreateSessionResponse {
        private Boolean success;
        private String message;
        private RealTimeCollaborationService.CollaborationSession session;

        public CreateSessionResponse(Boolean success, String message, RealTimeCollaborationService.CollaborationSession session) {
            this.success = success;
            this.message = message;
            this.session = session;
        }

        // Getters
        public Boolean getSuccess() { return success; }
        public String getMessage() { return message; }
        public RealTimeCollaborationService.CollaborationSession getSession() { return session; }
    }

    public static class CreateWorkspaceRequest {
        private String name;
        private String ownerId;
        private String description;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class CreateWorkspaceResponse {
        private Boolean success;
        private String message;
        private RealTimeCollaborationService.Workspace workspace;

        public CreateWorkspaceResponse(Boolean success, String message, RealTimeCollaborationService.Workspace workspace) {
            this.success = success;
            this.message = message;
            this.workspace = workspace;
        }

        // Getters
        public Boolean getSuccess() { return success; }
        public String getMessage() { return message; }
        public RealTimeCollaborationService.Workspace getWorkspace() { return workspace; }
    }

    public static class WorkspaceInviteRequest {
        private String userId;
        private String role;
        private String inviterId;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        
        public String getInviterId() { return inviterId; }
        public void setInviterId(String inviterId) { this.inviterId = inviterId; }
    }

    public static class CollaborationStats {
        private String userId;
        private Integer totalSessions;
        private Integer activeSessions;
        private Integer totalWorkspaces;
        private Integer collaborations;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public Integer getTotalSessions() { return totalSessions; }
        public void setTotalSessions(Integer totalSessions) { this.totalSessions = totalSessions; }
        
        public Integer getActiveSessions() { return activeSessions; }
        public void setActiveSessions(Integer activeSessions) { this.activeSessions = activeSessions; }
        
        public Integer getTotalWorkspaces() { return totalWorkspaces; }
        public void setTotalWorkspaces(Integer totalWorkspaces) { this.totalWorkspaces = totalWorkspaces; }
        
        public Integer getCollaborations() { return collaborations; }
        public void setCollaborations(Integer collaborations) { this.collaborations = collaborations; }
    }
}