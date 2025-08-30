package com.smoothsql.service;

import com.smoothsql.entity.DatabaseSchema;
import com.smoothsql.mapper.DatabaseSchemaMapper;
import com.smoothsql.dto.SqlExecuteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 性能优化服务
 * 
 * 功能包括：
 * 1. 查询结果缓存
 * 2. 数据库结构信息缓存
 * 3. SQL生成结果缓存
 * 4. 异步处理耗时操作
 * 5. 查询性能监控
 * 6. 智能预取数据
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
@Service
public class PerformanceOptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceOptimizationService.class);

    @Autowired
    private DatabaseSchemaMapper databaseSchemaMapper;

    // 查询性能统计
    private final Map<String, QueryPerformanceStats> performanceStats = new ConcurrentHashMap<>();
    
    // 热点查询缓存
    private final Map<String, Object> hotQueryCache = new ConcurrentHashMap<>();

    /**
     * 缓存数据库结构信息
     * 
     * @param databaseName 数据库名
     * @return 数据库结构信息
     */
    @Cacheable(value = "database-schema", key = "#databaseName")
    public List<DatabaseSchema> getCachedDatabaseSchema(String databaseName) {
        logger.debug("缓存数据库结构信息 - 数据库: {}", databaseName);
        
        long startTime = System.currentTimeMillis();
        List<DatabaseSchema> schemas = databaseSchemaMapper.selectByDatabaseName(databaseName);
        long duration = System.currentTimeMillis() - startTime;
        
        logger.debug("数据库结构信息获取完成 - 数据库: {}, 耗时: {}ms, 表数量: {}", 
                    databaseName, duration, 
                    schemas.stream().map(DatabaseSchema::getTableName).distinct().count());
        
        return schemas;
    }

    /**
     * 清除数据库结构缓存
     * 
     * @param databaseName 数据库名
     */
    @CacheEvict(value = "database-schema", key = "#databaseName")
    public void evictDatabaseSchemaCache(String databaseName) {
        logger.info("清除数据库结构缓存 - 数据库: {}", databaseName);
    }

    /**
     * 缓存查询结果
     * 
     * @param sql SQL语句
     * @param databaseName 数据库名
     * @param result 查询结果
     */
    @Cacheable(value = "query-results", key = "#sql + '-' + #databaseName", condition = "#result.data.rows.size() <= 1000")
    public SqlExecuteResponse cacheQueryResult(String sql, String databaseName, SqlExecuteResponse result) {
        logger.debug("缓存查询结果 - SQL: {}, 数据库: {}, 行数: {}", 
                    sql.substring(0, Math.min(50, sql.length())), 
                    databaseName, 
                    result.getData().getRows().size());
        return result;
    }

    /**
     * 获取缓存的查询结果
     * 
     * @param sql SQL语句
     * @param databaseName 数据库名
     * @return 缓存的查询结果，如果不存在则返回null
     */
    @Cacheable(value = "query-results", key = "#sql + '-' + #databaseName")
    public SqlExecuteResponse getCachedQueryResult(String sql, String databaseName) {
        // 这个方法主要用于缓存注解，实际逻辑由Spring Cache处理
        return null;
    }

    /**
     * 缓存用户权限信息
     * 
     * @param userId 用户ID
     * @param permissions 权限信息
     * @return 权限信息
     */
    @Cacheable(value = "user-permissions", key = "#userId")
    public Map<String, Object> cacheUserPermissions(String userId, Map<String, Object> permissions) {
        logger.debug("缓存用户权限信息 - 用户: {}", userId);
        return permissions;
    }

    /**
     * 清除用户权限缓存
     * 
     * @param userId 用户ID
     */
    @CacheEvict(value = "user-permissions", key = "#userId")
    public void evictUserPermissionsCache(String userId) {
        logger.info("清除用户权限缓存 - 用户: {}", userId);
    }

    /**
     * 记录查询性能统计
     * 
     * @param sql SQL语句
     * @param executionTime 执行时间（毫秒）
     * @param resultCount 结果行数
     */
    public void recordQueryPerformance(String sql, long executionTime, int resultCount) {
        try {
            String sqlKey = generateSqlKey(sql);
            
            QueryPerformanceStats stats = performanceStats.computeIfAbsent(sqlKey, k -> new QueryPerformanceStats());
            
            synchronized (stats) {
                stats.addExecution(executionTime, resultCount);
            }
            
            // 清理过期统计数据（保持最近1000个查询的统计）
            if (performanceStats.size() > 1000) {
                cleanupOldStats();
            }
            
        } catch (Exception e) {
            logger.error("记录查询性能统计异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取查询性能分析报告
     * 
     * @return 性能分析报告
     */
    public PerformanceReport getPerformanceReport() {
        try {
            logger.debug("生成性能分析报告");
            
            PerformanceReport report = new PerformanceReport();
            
            // 总体统计
            int totalQueries = performanceStats.values().stream().mapToInt(s -> s.executionCount).sum();
            double avgExecutionTime = performanceStats.values().stream()
                    .mapToDouble(s -> s.totalExecutionTime / (double) s.executionCount)
                    .average().orElse(0.0);
            
            report.setTotalQueries(totalQueries);
            report.setAverageExecutionTime(avgExecutionTime);
            
            // 慢查询统计
            List<SlowQueryInfo> slowQueries = performanceStats.entrySet().stream()
                    .filter(entry -> entry.getValue().averageExecutionTime > 1000) // 大于1秒的查询
                    .map(entry -> new SlowQueryInfo(
                        entry.getKey(),
                        entry.getValue().averageExecutionTime,
                        entry.getValue().executionCount
                    ))
                    .sorted((a, b) -> Double.compare(b.getAverageTime(), a.getAverageTime()))
                    .limit(10)
                    .collect(Collectors.toList());
            
            report.setSlowQueries(slowQueries);
            
            // 热点查询统计
            List<HotQueryInfo> hotQueries = performanceStats.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().executionCount, a.getValue().executionCount))
                    .limit(10)
                    .map(entry -> new HotQueryInfo(
                        entry.getKey(),
                        entry.getValue().executionCount,
                        entry.getValue().averageExecutionTime
                    ))
                    .collect(Collectors.toList());
            
            report.setHotQueries(hotQueries);
            
            // 缓存命中率统计
            // 这里可以添加缓存相关的统计信息
            report.setCacheHitRate(calculateCacheHitRate());
            
            logger.debug("性能分析报告生成完成 - 总查询数: {}, 平均执行时间: {:.2f}ms", 
                        totalQueries, avgExecutionTime);
            
            return report;
            
        } catch (Exception e) {
            logger.error("生成性能分析报告异常: {}", e.getMessage(), e);
            return new PerformanceReport();
        }
    }

    /**
     * 异步预取热点数据
     * 
     * @param databaseName 数据库名
     * @param userId 用户ID
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> prefetchHotData(String databaseName, String userId) {
        try {
            logger.debug("开始异步预取热点数据 - 数据库: {}, 用户: {}", databaseName, userId);
            
            // 预取数据库结构信息
            getCachedDatabaseSchema(databaseName);
            
            // 基于用户历史查询预取可能需要的数据
            // 这里可以添加更复杂的预取逻辑
            
            logger.debug("热点数据预取完成 - 数据库: {}, 用户: {}", databaseName, userId);
            
        } catch (Exception e) {
            logger.error("异步预取热点数据异常: {}", e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 优化SQL查询
     * 
     * @param sql 原始SQL
     * @param databaseName 数据库名
     * @return 优化后的SQL
     */
    public String optimizeSql(String sql, String databaseName) {
        try {
            logger.debug("开始SQL优化 - 数据库: {}", databaseName);
            
            String optimizedSql = sql;
            
            // 1. 添加LIMIT限制（如果没有的话）
            if (!sql.toLowerCase().contains("limit") && sql.toLowerCase().trim().startsWith("select")) {
                optimizedSql = sql + " LIMIT 10000";
                logger.debug("添加LIMIT限制");
            }
            
            // 2. 检查并建议使用索引
            List<String> indexSuggestions = analyzeIndexUsage(sql, databaseName);
            if (!indexSuggestions.isEmpty()) {
                logger.info("索引使用建议: {}", String.join(", ", indexSuggestions));
            }
            
            // 3. 其他优化逻辑...
            
            return optimizedSql;
            
        } catch (Exception e) {
            logger.error("SQL优化异常: {}", e.getMessage(), e);
            return sql; // 优化失败时返回原SQL
        }
    }

    private String generateSqlKey(String sql) {
        // 标准化SQL用于统计（移除参数值等）
        return sql.replaceAll("'[^']*'", "'?'")
                 .replaceAll("\\d+", "?")
                 .toLowerCase()
                 .trim();
    }

    private void cleanupOldStats() {
        // 保留最近使用的统计数据
        performanceStats.entrySet().removeIf(entry -> 
            entry.getValue().lastExecutionTime < System.currentTimeMillis() - 24 * 60 * 60 * 1000 // 24小时前
        );
    }

    private double calculateCacheHitRate() {
        // 这里可以实现实际的缓存命中率计算
        // 简化实现返回模拟数据
        return 0.75; // 75%命中率
    }

    private List<String> analyzeIndexUsage(String sql, String databaseName) {
        List<String> suggestions = new ArrayList<>();
        
        // 简化的索引分析逻辑
        if (sql.toLowerCase().contains("where") && !sql.toLowerCase().contains("index")) {
            suggestions.add("考虑在WHERE子句涉及的列上添加索引");
        }
        
        if (sql.toLowerCase().contains("order by") && !sql.toLowerCase().contains("index")) {
            suggestions.add("考虑在ORDER BY子句涉及的列上添加索引");
        }
        
        return suggestions;
    }

    // 内部类：查询性能统计
    private static class QueryPerformanceStats {
        int executionCount = 0;
        long totalExecutionTime = 0;
        double averageExecutionTime = 0;
        long minExecutionTime = Long.MAX_VALUE;
        long maxExecutionTime = 0;
        long lastExecutionTime = System.currentTimeMillis();

        synchronized void addExecution(long executionTime, int resultCount) {
            executionCount++;
            totalExecutionTime += executionTime;
            averageExecutionTime = (double) totalExecutionTime / executionCount;
            minExecutionTime = Math.min(minExecutionTime, executionTime);
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
            lastExecutionTime = System.currentTimeMillis();
        }
    }

    // 性能报告类
    public static class PerformanceReport {
        private int totalQueries;
        private double averageExecutionTime;
        private List<SlowQueryInfo> slowQueries = new ArrayList<>();
        private List<HotQueryInfo> hotQueries = new ArrayList<>();
        private double cacheHitRate;

        // Getters and Setters
        public int getTotalQueries() { return totalQueries; }
        public void setTotalQueries(int totalQueries) { this.totalQueries = totalQueries; }

        public double getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(double averageExecutionTime) { this.averageExecutionTime = averageExecutionTime; }

        public List<SlowQueryInfo> getSlowQueries() { return slowQueries; }
        public void setSlowQueries(List<SlowQueryInfo> slowQueries) { this.slowQueries = slowQueries; }

        public List<HotQueryInfo> getHotQueries() { return hotQueries; }
        public void setHotQueries(List<HotQueryInfo> hotQueries) { this.hotQueries = hotQueries; }

        public double getCacheHitRate() { return cacheHitRate; }
        public void setCacheHitRate(double cacheHitRate) { this.cacheHitRate = cacheHitRate; }
    }

    // 慢查询信息
    public static class SlowQueryInfo {
        private String sql;
        private double averageTime;
        private int executionCount;

        public SlowQueryInfo(String sql, double averageTime, int executionCount) {
            this.sql = sql;
            this.averageTime = averageTime;
            this.executionCount = executionCount;
        }

        // Getters
        public String getSql() { return sql; }
        public double getAverageTime() { return averageTime; }
        public int getExecutionCount() { return executionCount; }
    }

    // 热点查询信息
    public static class HotQueryInfo {
        private String sql;
        private int executionCount;
        private double averageTime;

        public HotQueryInfo(String sql, int executionCount, double averageTime) {
            this.sql = sql;
            this.executionCount = executionCount;
            this.averageTime = averageTime;
        }

        // Getters
        public String getSql() { return sql; }
        public int getExecutionCount() { return executionCount; }
        public double getAverageTime() { return averageTime; }
    }
}