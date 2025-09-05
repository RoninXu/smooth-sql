package com.smoothsql.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoothsql.service.RealTimeCollaborationService;
import com.smoothsql.service.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 协作控制器集成测试
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@ExtendWith(MockitoExtension.class)
@WebMvcTest(CollaborationController.class)
class CollaborationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RealTimeCollaborationService collaborationService;

    @MockBean
    private UserPermissionService permissionService;

    @Autowired
    private ObjectMapper objectMapper;

    private CollaborationController.CreateSessionRequest sessionRequest;
    private RealTimeCollaborationService.CollaborationSession mockSession;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        sessionRequest = new CollaborationController.CreateSessionRequest();
        sessionRequest.setUserId("user1");
        sessionRequest.setWorkspaceId("workspace1");
        sessionRequest.setInitialQuery("SELECT * FROM users");

        mockSession = new RealTimeCollaborationService.CollaborationSession();
        mockSession.setSessionId("session1");
        mockSession.setWorkspaceId("workspace1");
        mockSession.setCreatorId("user1");
        mockSession.setCurrentQuery("SELECT * FROM users");
        mockSession.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testCreateSession_Success() throws Exception {
        // 准备模拟数据
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.createCollaborationSession(anyString(), eq("workspace1"), eq("user1"), eq("SELECT * FROM users")))
            .thenReturn(mockSession);

        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sessionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("协作会话创建成功"))
                .andExpect(jsonPath("$.session.sessionId").value("session1"))
                .andExpect(jsonPath("$.session.workspaceId").value("workspace1"))
                .andExpect(jsonPath("$.session.creatorId").value("user1"));

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
        verify(collaborationService, times(1)).createCollaborationSession(anyString(), eq("workspace1"), eq("user1"), eq("SELECT * FROM users"));
    }

    @Test
    void testCreateSession_NoPermission() throws Exception {
        // 准备模拟数据
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(false);

        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sessionRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权限创建协作会话"));

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
        verify(collaborationService, never()).createCollaborationSession(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testCreateSession_ServiceException() throws Exception {
        // 准备模拟数据
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.createCollaborationSession(anyString(), eq("workspace1"), eq("user1"), eq("SELECT * FROM users")))
            .thenThrow(new RuntimeException("服务异常"));

        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sessionRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("创建协作会话失败: 服务异常"));
    }

    @Test
    void testGetSessionStatus_Success() throws Exception {
        // 准备模拟数据
        RealTimeCollaborationService.CollaborationSessionStatus status = 
            new RealTimeCollaborationService.CollaborationSessionStatus();
        status.setSessionId("session1");
        status.setCurrentQuery("SELECT * FROM users");
        status.setVersion(1);
        status.setParticipantCount(2);
        status.setOnlineParticipants(Arrays.asList("user1", "user2"));
        status.setLastModified(LocalDateTime.now());

        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.getSessionStatus("session1")).thenReturn(status);

        // 执行测试
        mockMvc.perform(get("/api/v3/collaboration/sessions/session1/status")
                .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session1"))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.version").value(1));

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
        verify(collaborationService, times(1)).getSessionStatus("session1");
    }

    @Test
    void testGetSessionStatus_NotFound() throws Exception {
        // 准备模拟数据
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.getSessionStatus("nonexistent")).thenReturn(null);

        // 执行测试
        mockMvc.perform(get("/api/v3/collaboration/sessions/nonexistent/status")
                .param("userId", "user1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateWorkspace_Success() throws Exception {
        // 准备测试数据
        CollaborationController.CreateWorkspaceRequest workspaceRequest = 
            new CollaborationController.CreateWorkspaceRequest();
        workspaceRequest.setName("测试工作空间");
        workspaceRequest.setOwnerId("user1");
        workspaceRequest.setDescription("这是一个测试工作空间");

        RealTimeCollaborationService.Workspace mockWorkspace = new RealTimeCollaborationService.Workspace();
        mockWorkspace.setWorkspaceId("workspace1");
        mockWorkspace.setName("测试工作空间");
        mockWorkspace.setOwnerId("user1");
        mockWorkspace.setDescription("这是一个测试工作空间");
        mockWorkspace.setCreatedAt(LocalDateTime.now());

        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.createWorkspace(anyString(), eq("测试工作空间"), eq("user1"), eq("这是一个测试工作空间")))
            .thenReturn(mockWorkspace);

        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workspaceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("工作空间创建成功"))
                .andExpect(jsonPath("$.workspace.name").value("测试工作空间"));

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
        verify(collaborationService, times(1)).createWorkspace(anyString(), eq("测试工作空间"), eq("user1"), eq("这是一个测试工作空间"));
    }

    @Test
    void testInviteToWorkspace_Success() throws Exception {
        // 准备测试数据
        CollaborationController.WorkspaceInviteRequest inviteRequest = 
            new CollaborationController.WorkspaceInviteRequest();
        inviteRequest.setUserId("user2");
        inviteRequest.setRole("MEMBER");
        inviteRequest.setInviterId("user1");

        RealTimeCollaborationService.WorkspaceInviteResult inviteResult = 
            new RealTimeCollaborationService.WorkspaceInviteResult(true, "邀请成功");

        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.inviteToWorkspace("workspace1", "user2", "MEMBER", "user1"))
            .thenReturn(inviteResult);

        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/workspaces/workspace1/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("邀请成功"));

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
        verify(collaborationService, times(1)).inviteToWorkspace("workspace1", "user2", "MEMBER", "user1");
    }

    @Test
    void testInviteToWorkspace_InsufficientPermission() throws Exception {
        // 准备测试数据
        CollaborationController.WorkspaceInviteRequest inviteRequest = 
            new CollaborationController.WorkspaceInviteRequest();
        inviteRequest.setUserId("user2");
        inviteRequest.setRole("MEMBER");
        inviteRequest.setInviterId("user1");

        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(false);

        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/workspaces/workspace1/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权限邀请用户"));

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
        verify(collaborationService, never()).inviteToWorkspace(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testGetUserCollaborationStats_Success() throws Exception {
        // 准备模拟数据
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);

        // 执行测试
        mockMvc.perform(get("/api/v3/collaboration/users/user1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user1"))
                .andExpect(jsonPath("$.totalSessions").exists())
                .andExpect(jsonPath("$.activeSessions").exists())
                .andExpect(jsonPath("$.totalWorkspaces").exists())
                .andExpect(jsonPath("$.collaborations").exists());

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
    }

    @Test
    void testGetUserCollaborationStats_NoPermission() throws Exception {
        // 准备模拟数据
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(false);

        // 执行测试
        mockMvc.perform(get("/api/v3/collaboration/users/user1/stats"))
                .andExpect(status().isForbidden());

        // 验证服务调用
        verify(permissionService, times(1)).hasPermission("user1", "QUERY_DATA");
    }

    @Test
    void testCreateSession_InvalidRequestBody() throws Exception {
        // 准备无效的请求体
        CollaborationController.CreateSessionRequest invalidRequest = 
            new CollaborationController.CreateSessionRequest();
        // 缺少必需的字段

        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateSession_EmptyRequestBody() throws Exception {
        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateSession_MalformedJson() throws Exception {
        // 执行测试
        mockMvc.perform(post("/api/v3/collaboration/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetSessionStatus_MissingParameter() throws Exception {
        // 执行测试 - 缺少userId参数
        mockMvc.perform(get("/api/v3/collaboration/sessions/session1/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCorsHeaders() throws Exception {
        // 测试CORS头设置
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.getSessionStatus("session1")).thenReturn(null);

        mockMvc.perform(get("/api/v3/collaboration/sessions/session1/status")
                .param("userId", "user1")
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void testContentTypeValidation() throws Exception {
        // 测试不支持的内容类型
        mockMvc.perform(post("/api/v3/collaboration/sessions")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text content"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void testConcurrentSessionCreation() throws Exception {
        // 模拟并发会话创建
        when(permissionService.hasPermission("user1", "QUERY_DATA")).thenReturn(true);
        when(collaborationService.createCollaborationSession(anyString(), eq("workspace1"), eq("user1"), eq("SELECT * FROM users")))
            .thenReturn(mockSession);

        // 并发执行多个请求
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v3/collaboration/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sessionRequest)))
                    .andExpect(status().isOk());
        }

        // 验证服务被调用了预期的次数
        verify(collaborationService, times(5)).createCollaborationSession(anyString(), eq("workspace1"), eq("user1"), eq("SELECT * FROM users"));
    }

    private MockMvc buildMockMvc(WebApplicationContext context) {
        return MockMvcBuilders
                .webAppContextSetup(context)
                .build();
    }
}