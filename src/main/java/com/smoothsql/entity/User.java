package com.smoothsql.entity;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
public class User {
    private Long id;
    private String username;
    private String email;
    private String password; // 加密后的密码
    private String role; // USER, ADMIN, VIEWER
    private String status; // ACTIVE, INACTIVE, LOCKED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private String permissions; // JSON格式存储权限列表
    private Integer maxQueryCount; // 每日最大查询次数限制
    private String allowedDatabases; // 允许访问的数据库列表，逗号分隔

    public User() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = "ACTIVE";
        this.role = "USER";
        this.maxQueryCount = 100; // 默认每日100次查询
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public Integer getMaxQueryCount() {
        return maxQueryCount;
    }

    public void setMaxQueryCount(Integer maxQueryCount) {
        this.maxQueryCount = maxQueryCount;
    }

    public String getAllowedDatabases() {
        return allowedDatabases;
    }

    public void setAllowedDatabases(String allowedDatabases) {
        this.allowedDatabases = allowedDatabases;
    }
}