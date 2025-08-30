-- 查询历史表
CREATE TABLE IF NOT EXISTS query_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    natural_query TEXT NOT NULL COMMENT '自然语言查询',
    generated_sql TEXT NOT NULL COMMENT '生成的SQL语句',
    database_name VARCHAR(100) COMMENT '数据库名称',
    execution_time BIGINT COMMENT '执行时间(毫秒)',
    result_count INT COMMENT '结果数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS, FAILED, PENDING',
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_database_name (database_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查询历史表';

-- 数据库结构表
CREATE TABLE IF NOT EXISTS database_schema (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名称',
    table_name VARCHAR(100) NOT NULL COMMENT '表名',
    column_name VARCHAR(100) NOT NULL COMMENT '列名',
    column_type VARCHAR(100) NOT NULL COMMENT '列类型',
    is_nullable BOOLEAN DEFAULT FALSE COMMENT '是否可为空',
    column_comment TEXT COMMENT '列注释',
    UNIQUE KEY uk_schema (database_name, table_name, column_name),
    INDEX idx_database_name (database_name),
    INDEX idx_table_name (database_name, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库结构表';

-- 插入一些示例数据
INSERT IGNORE INTO database_schema (database_name, table_name, column_name, column_type, is_nullable, column_comment) VALUES
('demo', 'users', 'id', 'BIGINT', FALSE, '用户ID'),
('demo', 'users', 'username', 'VARCHAR(50)', FALSE, '用户名'),
('demo', 'users', 'email', 'VARCHAR(100)', TRUE, '邮箱'),
('demo', 'users', 'created_at', 'DATETIME', FALSE, '创建时间'),
('demo', 'orders', 'id', 'BIGINT', FALSE, '订单ID'),
('demo', 'orders', 'user_id', 'BIGINT', FALSE, '用户ID'),
('demo', 'orders', 'total_amount', 'DECIMAL(10,2)', FALSE, '订单总金额'),
('demo', 'orders', 'status', 'VARCHAR(20)', FALSE, '订单状态'),
('demo', 'orders', 'created_at', 'DATETIME', FALSE, '创建时间'),
('demo', 'products', 'id', 'BIGINT', FALSE, '产品ID'),
('demo', 'products', 'name', 'VARCHAR(200)', FALSE, '产品名称'),
('demo', 'products', 'price', 'DECIMAL(10,2)', FALSE, '价格'),
('demo', 'products', 'category', 'VARCHAR(50)', TRUE, '分类');