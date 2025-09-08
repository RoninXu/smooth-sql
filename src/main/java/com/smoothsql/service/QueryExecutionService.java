package com.smoothsql.service;

import com.smoothsql.dto.SqlExecuteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * SQL查询执行服务
 * 
 * 提供安全的SQL执行功能，包括：
 * 1. SQL安全验证（仅允许SELECT语句）
 * 2. SQL注入防护
 * 3. 结果数量限制
 * 4. 动态结果映射
 * 5. 执行时间监控
 * 6. 详细的执行日志
 * 
 * 安全措施：
 * - 只允许SELECT查询，禁止DML/DDL操作
 * - 自动添加LIMIT限制，防止大量数据查询
 * - 正则表达式验证SQL格式
 * - 禁用危险SQL关键词
 * 
 * @author Smooth SQL Team
 * @version 1.0
 * @since 2024-08-30
 */
@Service
public class QueryExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(QueryExecutionService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // SQL安全验证正则表达式：只允许SELECT语句
    private static final Pattern SAFE_SQL_PATTERN = Pattern.compile(
        "^\\s*SELECT\\s+.*\\s+FROM\\s+.*$", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // 禁止的SQL关键词，防止恶意操作
    private static final String[] FORBIDDEN_KEYWORDS = {
        "DELETE", "DROP", "INSERT", "UPDATE", "CREATE", "ALTER", 
        "TRUNCATE", "EXEC", "EXECUTE", "CALL", "UNION", "INTO"
    };

    /**
     * 执行SQL查询的主入口方法
     * 
     * 执行流程：
     * 1. SQL安全性验证
     * 2. 添加查询限制
     * 3. 执行SQL查询
     * 4. 处理查询结果
     * 5. 记录执行日志
     * 
     * @param sql 待执行的SQL语句
     * @param limit 结果数量限制，如果为null则默认100条
     * @return 查询执行结果，包含数据和执行统计信息
     */
    public QueryExecutionResult executeSql(String sql, Integer limit) {
        logger.info("开始执行SQL查询 - SQL: '{}', 限制条数: {}", sql, limit);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 第一步：SQL安全性验证
            logger.debug("步骤1: 开始SQL安全性验证");
            if (!isSafeSql(sql)) {
                logger.warn("SQL安全验证失败 - 不支持的SQL操作: '{}'", sql);
                return new QueryExecutionResult(
                    false, 
                    "不支持的SQL操作，仅支持SELECT查询", 
                    null, 
                    0L
                );
            }
            logger.debug("SQL安全验证通过");
            
            // 第二步：添加LIMIT限制
            logger.debug("步骤2: 添加查询数量限制");
            String limitedSql = addLimitToSql(sql, limit);
            logger.debug("添加限制后的SQL: '{}'", limitedSql);
            
            // 第三步：执行查询
            logger.debug("步骤3: 开始执行数据库查询");
            long dbStartTime = System.currentTimeMillis();
            
            List<Map<String, Object>> rows = jdbcTemplate.query(limitedSql, new DynamicRowMapper());
            
            long dbTime = System.currentTimeMillis() - dbStartTime;
            logger.debug("数据库查询完成 - 耗时: {}ms, 返回行数: {}", dbTime, rows.size());
            
            // 第四步：处理查询结果
            logger.debug("步骤4: 处理查询结果");
            List<String> columns = getColumnsFromResult(rows);
            logger.debug("提取到列信息: {}", columns);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            logger.info("SQL查询执行成功 - 总耗时: {}ms (数据库: {}ms), 返回 {} 行 {} 列", 
                       executionTime, dbTime, rows.size(), columns.size());
            
            return new QueryExecutionResult(
                true,
                "查询执行成功",
                new QueryResultData(columns, rows, rows.size(), executionTime),
                executionTime
            );
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("SQL查询执行失败 - 耗时: {}ms, SQL: '{}', 异常: {}", 
                        executionTime, sql, e.getMessage(), e);
            
            return new QueryExecutionResult(
                false,
                "SQL执行失败: " + e.getMessage(),
                null,
                executionTime
            );
        }
    }

    /**
     * SQL安全性验证方法
     * 
     * 多层安全检查：
     * 1. 空值检查
     * 2. SELECT语句检查
     * 3. 禁用关键词检查
     * 4. 正则表达式格式验证
     * 
     * @param sql 待验证的SQL语句
     * @return true表示安全，false表示存在安全风险
     */
    private boolean isSafeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            logger.debug("SQL安全检查失败: SQL语句为空");
            return false;
        }
        
        String upperSql = sql.trim().toUpperCase();
        
        // 检查1: 只允许SELECT语句
        if (!upperSql.startsWith("SELECT")) {
            logger.debug("SQL安全检查失败: 不是SELECT语句，SQL开头: '{}'", 
                        upperSql.length() > 10 ? upperSql.substring(0, 10) : upperSql);
            return false;
        }
        
        // 检查2: 禁止危险关键词
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                logger.debug("SQL安全检查失败: 包含禁止关键词 '{}'", keyword);
                return false;
            }
        }
        
        // 检查3: 正则表达式格式验证
        boolean patternMatch = SAFE_SQL_PATTERN.matcher(sql).matches();
        if (!patternMatch) {
            logger.debug("SQL安全检查失败: 不符合安全SQL格式");
        }
        
        logger.debug("SQL安全验证结果: {}", patternMatch ? "通过" : "失败");
        return patternMatch;
    }

    private String addLimitToSql(String sql, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 100; // 默认限制100条
        }
        
        String upperSql = sql.trim().toUpperCase();
        
        // 如果SQL已经包含LIMIT，则不重复添加
        if (upperSql.contains("LIMIT")) {
            return sql;
        }
        
        return sql + " LIMIT " + limit;
    }

    private List<String> getColumnsFromResult(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        
        return new ArrayList<>(rows.get(0).keySet());
    }

    // 动态行映射器
    private static class DynamicRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            Map<String, Object> row = new LinkedHashMap<>();
            
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            
            return row;
        }
    }

    // 查询执行结果类
    public static class QueryExecutionResult {
        private boolean success;
        private String message;
        private QueryResultData data;
        private Long executionTime;

        public QueryExecutionResult(boolean success, String message, QueryResultData data, Long executionTime) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.executionTime = executionTime;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public QueryResultData getData() { return data; }
        public Long getExecutionTime() { return executionTime; }
    }

    // 查询结果数据类
    public static class QueryResultData {
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private Integer totalCount;
        private Long executionTime;

        public QueryResultData(List<String> columns, List<Map<String, Object>> rows, Integer totalCount, Long executionTime) {
            this.columns = columns;
            this.rows = rows;
            this.totalCount = totalCount;
            this.executionTime = executionTime;
        }

        public List<String> getColumns() { return columns; }
        public List<Map<String, Object>> getRows() { return rows; }
        public Integer getTotalCount() { return totalCount; }
        public Long getExecutionTime() { return executionTime; }
    }

    /**
     * 兼容接口：执行查询（兼容EnhancedSqlController调用）
     */
    public SqlExecuteResponse executeQuery(String sql, String database, Integer limit) {
        QueryExecutionResult result = executeSql(sql, limit);
        
        SqlExecuteResponse response = new SqlExecuteResponse();
        response.setSuccess(result.isSuccess());
        response.setMessage(result.getMessage());
        
        if (result.isSuccess() && result.getData() != null) {
            response.setData(result.getData());
        }
        
        return response;
    }

    /**
     * 兼容接口：执行查询（两参数版本）
     */
    public SqlExecuteResponse executeQuery(String sql, String database) {
        return executeQuery(sql, database, null);
    }
}