package com.smoothsql.service;

import com.smoothsql.entity.User;
import com.smoothsql.mapper.UserMapper;
import com.smoothsql.mapper.QueryHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户权限管理服务
 * 
 * 功能包括：
 * 1. 用户认证和授权
 * 2. 角色和权限管理
 * 3. 数据库访问权限控制
 * 4. 查询频率限制
 * 5. 用户状态管理
 * 6. 安全审计日志
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
@Service
public class UserPermissionService {

    private static final Logger logger = LoggerFactory.getLogger(UserPermissionService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private QueryHistoryMapper queryHistoryMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     * 
     * @param username 用户名
     * @param email 邮箱
     * @param password 明文密码
     * @param role 用户角色
     * @return 注册结果
     */
    public UserRegistrationResult registerUser(String username, String email, String password, String role) {
        try {
            logger.info("开始用户注册 - 用户名: {}, 邮箱: {}, 角色: {}", username, email, role);

            // 检查用户名是否已存在
            if (userMapper.existsByUsername(username)) {
                return new UserRegistrationResult(false, "用户名已存在", null);
            }

            // 检查邮箱是否已存在
            if (userMapper.existsByEmail(email)) {
                return new UserRegistrationResult(false, "邮箱已被注册", null);
            }

            // 创建新用户
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(role != null ? role : "USER");
            
            // 设置默认权限
            user.setPermissions(generateDefaultPermissions(user.getRole()));
            user.setAllowedDatabases("*"); // 默认允许访问所有数据库

            int inserted = userMapper.insert(user);

            if (inserted > 0) {
                logger.info("用户注册成功 - 用户ID: {}", user.getId());
                return new UserRegistrationResult(true, "注册成功", user.getId());
            } else {
                return new UserRegistrationResult(false, "注册失败", null);
            }

        } catch (Exception e) {
            logger.error("用户注册异常: {}", e.getMessage(), e);
            return new UserRegistrationResult(false, "注册异常: " + e.getMessage(), null);
        }
    }

    /**
     * 用户登录认证
     * 
     * @param username 用户名
     * @param password 明文密码
     * @return 登录结果
     */
    public UserLoginResult authenticateUser(String username, String password) {
        try {
            logger.debug("用户登录认证 - 用户名: {}", username);

            User user = userMapper.selectByUsername(username);
            if (user == null) {
                logger.warn("登录失败 - 用户不存在: {}", username);
                return new UserLoginResult(false, "用户名或密码错误", null, null);
            }

            // 检查用户状态
            if (!"ACTIVE".equals(user.getStatus())) {
                logger.warn("登录失败 - 用户状态异常: {}, 状态: {}", username, user.getStatus());
                return new UserLoginResult(false, "账户已被禁用", null, null);
            }

            // 验证密码
            if (!passwordEncoder.matches(password, user.getPassword())) {
                logger.warn("登录失败 - 密码错误: {}", username);
                return new UserLoginResult(false, "用户名或密码错误", null, null);
            }

            // 更新最后登录时间
            userMapper.updateLastLoginTime(user.getId(), LocalDateTime.now());

            logger.info("用户登录成功 - 用户名: {}, 用户ID: {}", username, user.getId());
            
            return new UserLoginResult(true, "登录成功", user.getId(), user.getRole());

        } catch (Exception e) {
            logger.error("用户登录认证异常: {}", e.getMessage(), e);
            return new UserLoginResult(false, "登录异常", null, null);
        }
    }

    /**
     * 检查用户对数据库的访问权限
     * 
     * @param userId 用户ID
     * @param databaseName 数据库名
     * @return 是否有权限
     */
    public boolean hasAccessToDatabase(String userId, String databaseName) {
        try {
            logger.debug("检查数据库访问权限 - 用户: {}, 数据库: {}", userId, databaseName);

            User user = userMapper.selectByUserId(userId);
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                logger.warn("用户不存在或状态异常 - 用户: {}", userId);
                return false;
            }

            // 管理员有所有权限
            if ("ADMIN".equals(user.getRole())) {
                return true;
            }

            // 检查允许的数据库列表
            String allowedDatabases = user.getAllowedDatabases();
            if ("*".equals(allowedDatabases)) {
                return true; // 允许访问所有数据库
            }

            if (allowedDatabases != null) {
                List<String> allowed = Arrays.asList(allowedDatabases.split(","));
                boolean hasAccess = allowed.contains(databaseName);
                
                if (!hasAccess) {
                    logger.warn("用户无数据库访问权限 - 用户: {}, 数据库: {}", userId, databaseName);
                }
                
                return hasAccess;
            }

            return false;

        } catch (Exception e) {
            logger.error("检查数据库访问权限异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查用户查询频率限制
     * 
     * @param userId 用户ID
     * @return 检查结果
     */
    public QueryLimitResult checkQueryLimit(String userId) {
        try {
            logger.debug("检查查询频率限制 - 用户: {}", userId);

            User user = userMapper.selectByUserId(userId);
            if (user == null) {
                return new QueryLimitResult(false, "用户不存在", 0, 0);
            }

            // 获取今日查询次数
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            
            long todayQueryCount = queryHistoryMapper.countByUserIdAndDateRange(userId, startOfDay, endOfDay);
            int maxAllowed = user.getMaxQueryCount() != null ? user.getMaxQueryCount() : 100;

            boolean allowed = todayQueryCount < maxAllowed;
            
            if (!allowed) {
                logger.warn("用户查询次数超限 - 用户: {}, 今日查询: {}, 限制: {}", 
                           userId, todayQueryCount, maxAllowed);
            }

            return new QueryLimitResult(allowed, 
                                      allowed ? "可以查询" : "今日查询次数已达上限", 
                                      (int)todayQueryCount, 
                                      maxAllowed);

        } catch (Exception e) {
            logger.error("检查查询频率限制异常: {}", e.getMessage(), e);
            return new QueryLimitResult(false, "检查异常", 0, 0);
        }
    }

    /**
     * 检查用户特定权限
     * 
     * @param userId 用户ID
     * @param permission 权限名称
     * @return 是否有权限
     */
    public boolean hasPermission(String userId, String permission) {
        try {
            logger.debug("检查用户权限 - 用户: {}, 权限: {}", userId, permission);

            User user = userMapper.selectByUserId(userId);
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                return false;
            }

            // 管理员有所有权限
            if ("ADMIN".equals(user.getRole())) {
                return true;
            }

            // 检查具体权限
            String permissions = user.getPermissions();
            if (permissions != null) {
                return permissions.contains(permission);
            }

            return false;

        } catch (Exception e) {
            logger.error("检查用户权限异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新用户权限
     * 
     * @param userId 用户ID
     * @param permissions 新权限列表
     * @param operatorId 操作员ID
     * @return 是否成功
     */
    public boolean updateUserPermissions(String userId, List<String> permissions, String operatorId) {
        try {
            logger.info("更新用户权限 - 用户: {}, 权限: {}, 操作员: {}", userId, permissions, operatorId);

            // 检查操作权限
            if (!hasPermission(operatorId, "MANAGE_USERS")) {
                logger.warn("权限不足 - 操作员: {} 尝试更新用户权限", operatorId);
                return false;
            }

            String permissionStr = String.join(",", permissions);
            int updated = userMapper.updatePermissions(userId, permissionStr);

            boolean success = updated > 0;
            if (success) {
                logger.info("用户权限更新成功 - 用户: {}", userId);
            } else {
                logger.warn("用户权限更新失败 - 用户: {}", userId);
            }

            return success;

        } catch (Exception e) {
            logger.error("更新用户权限异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取用户信息
     * 
     * @param userId 用户ID
     * @return 用户信息
     */
    public User getUserInfo(String userId) {
        try {
            User user = userMapper.selectByUserId(userId);
            if (user != null) {
                // 清除敏感信息
                user.setPassword(null);
            }
            return user;
        } catch (Exception e) {
            logger.error("获取用户信息异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 禁用用户
     * 
     * @param userId 用户ID
     * @param operatorId 操作员ID
     * @param reason 禁用原因
     * @return 是否成功
     */
    public boolean disableUser(String userId, String operatorId, String reason) {
        try {
            logger.info("禁用用户 - 用户: {}, 操作员: {}, 原因: {}", userId, operatorId, reason);

            if (!hasPermission(operatorId, "MANAGE_USERS")) {
                return false;
            }

            int updated = userMapper.updateStatus(userId, "INACTIVE");
            
            if (updated > 0) {
                logger.info("用户禁用成功 - 用户: {}", userId);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("禁用用户异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private String generateDefaultPermissions(String role) {
        switch (role.toUpperCase()) {
            case "ADMIN":
                return "QUERY_DATA,EXPORT_DATA,MANAGE_USERS,VIEW_HISTORY,ADVANCED_QUERY";
            case "USER":
                return "QUERY_DATA,EXPORT_DATA,VIEW_HISTORY";
            case "VIEWER":
                return "QUERY_DATA,VIEW_HISTORY";
            default:
                return "QUERY_DATA";
        }
    }

    // 内部类：用户注册结果
    public static class UserRegistrationResult {
        private boolean success;
        private String message;
        private Long userId;

        public UserRegistrationResult(boolean success, String message, Long userId) {
            this.success = success;
            this.message = message;
            this.userId = userId;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Long getUserId() { return userId; }
    }

    // 内部类：用户登录结果
    public static class UserLoginResult {
        private boolean success;
        private String message;
        private Long userId;
        private String role;

        public UserLoginResult(boolean success, String message, Long userId, String role) {
            this.success = success;
            this.message = message;
            this.userId = userId;
            this.role = role;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Long getUserId() { return userId; }
        public String getRole() { return role; }
    }

    // 内部类：查询限制结果
    public static class QueryLimitResult {
        private boolean allowed;
        private String message;
        private int currentCount;
        private int maxAllowed;

        public QueryLimitResult(boolean allowed, String message, int currentCount, int maxAllowed) {
            this.allowed = allowed;
            this.message = message;
            this.currentCount = currentCount;
            this.maxAllowed = maxAllowed;
        }

        // Getters
        public boolean isAllowed() { return allowed; }
        public String getMessage() { return message; }
        public int getCurrentCount() { return currentCount; }
        public int getMaxAllowed() { return maxAllowed; }
    }
}