-- 数据库初始化脚本
-- 创建数据库表结构

-- 数据库模式信息表
CREATE TABLE IF NOT EXISTS database_schema (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名称',
    table_name VARCHAR(100) NOT NULL COMMENT '表名',
    column_name VARCHAR(100) NOT NULL COMMENT '列名',
    column_type VARCHAR(50) COMMENT '列类型',
    data_type VARCHAR(50) COMMENT '数据类型',
    is_nullable BOOLEAN DEFAULT FALSE COMMENT '是否可空',
    column_comment VARCHAR(500) COMMENT '列注释',
    table_comment VARCHAR(500) COMMENT '表注释',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_database_name (database_name),
    INDEX idx_table_name (table_name),
    INDEX idx_column_name (column_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库模式信息表';

-- 查询历史表
CREATE TABLE IF NOT EXISTS query_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    natural_query TEXT NOT NULL COMMENT '自然语言查询',
    generated_sql TEXT COMMENT '生成的SQL',
    database_name VARCHAR(100) COMMENT '数据库名',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：SUCCESS, FAILED, PENDING',
    execution_time BIGINT COMMENT '执行时间(毫秒)',
    result_count INT COMMENT '结果数量',
    is_favorite BOOLEAN DEFAULT FALSE COMMENT '是否收藏',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_database_name (database_name),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询历史表';

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    password VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    role VARCHAR(20) DEFAULT 'USER' COMMENT '角色：ADMIN, USER, VIEWER',
    permissions TEXT COMMENT '权限列表，逗号分隔',
    allowed_databases TEXT COMMENT '允许访问的数据库，逗号分隔，*表示全部',
    max_query_count INT DEFAULT 100 COMMENT '每日最大查询次数',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE',
    last_login_time TIMESTAMP NULL COMMENT '最后登录时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入默认管理员用户 (密码是 admin123 的bcrypt加密)
INSERT IGNORE INTO users (username, email, password, role, permissions, allowed_databases) 
VALUES ('admin', 'admin@smoothsql.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXIGfVTfuvfZSO/tYbmYIgstGW.', 'ADMIN', 'QUERY_DATA,EXPORT_DATA,MANAGE_USERS,VIEW_HISTORY,ADVANCED_QUERY', '*');

-- 插入测试用户
INSERT IGNORE INTO users (username, email, password, role, permissions, allowed_databases) 
VALUES ('testuser', 'test@smoothsql.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXIGfVTfuvfZSO/tYbmYIgstGW.', 'USER', 'QUERY_DATA,EXPORT_DATA,VIEW_HISTORY', '*');