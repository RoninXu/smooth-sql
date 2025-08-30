package com.smoothsql.service;

import com.smoothsql.entity.QueryHistory;
import com.smoothsql.mapper.QueryHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 查询历史管理服务
 * 
 * 功能包括：
 * 1. 查询历史记录管理（增删改查）
 * 2. 历史查询统计分析
 * 3. 智能查询推荐
 * 4. 用户查询行为分析
 * 5. 历史记录分类和标签
 * 6. 收藏夹功能
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
@Service
public class QueryHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(QueryHistoryService.class);

    @Autowired
    private QueryHistoryMapper queryHistoryMapper;

    /**
     * 保存查询历史记录
     * 
     * @param naturalQuery 自然语言查询
     * @param generatedSql 生成的SQL
     * @param databaseName 数据库名
     * @param userId 用户ID
     * @param executionTime 执行时间（毫秒）
     * @param resultCount 结果行数
     * @param status 执行状态
     * @return 保存的历史记录ID
     */
    public Long saveQueryHistory(String naturalQuery, String generatedSql, String databaseName, 
                               String userId, Long executionTime, Integer resultCount, String status) {
        try {
            logger.debug("保存查询历史 - 用户: {}, 数据库: {}, 状态: {}", userId, databaseName, status);
            
            QueryHistory history = new QueryHistory();
            history.setUserId(userId != null ? userId : "anonymous");
            history.setNaturalQuery(naturalQuery);
            history.setGeneratedSql(generatedSql);
            history.setDatabaseName(databaseName);
            history.setExecutionTime(executionTime);
            history.setResultCount(resultCount);
            history.setStatus(status);
            history.setCreatedAt(LocalDateTime.now());
            
            // 自动生成标签
            history.setTags(generateAutoTags(naturalQuery, generatedSql));
            
            int inserted = queryHistoryMapper.insert(history);
            
            if (inserted > 0) {
                logger.debug("查询历史保存成功 - ID: {}", history.getId());
                return history.getId();
            } else {
                logger.warn("查询历史保存失败");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("保存查询历史异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取用户查询历史列表
     * 
     * @param userId 用户ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param databaseName 数据库名（可选筛选条件）
     * @param status 状态（可选筛选条件）
     * @return 历史记录列表
     */
    public QueryHistoryResult getUserQueryHistory(String userId, int page, int size, 
                                                String databaseName, String status) {
        try {
            logger.debug("查询用户历史记录 - 用户: {}, 页码: {}, 大小: {}", userId, page, size);
            
            int offset = page * size;
            
            // 构建查询条件
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("databaseName", databaseName);
            params.put("status", status);
            params.put("offset", offset);
            params.put("limit", size);
            
            List<QueryHistory> histories = queryHistoryMapper.selectByUserIdWithPagination(params);
            long totalCount = queryHistoryMapper.countByUserId(userId, databaseName, status);
            
            logger.debug("历史记录查询完成 - 返回: {} 条, 总计: {} 条", histories.size(), totalCount);
            
            return new QueryHistoryResult(histories, totalCount, page, size);
            
        } catch (Exception e) {
            logger.error("查询历史记录异常: {}", e.getMessage(), e);
            return new QueryHistoryResult(Collections.emptyList(), 0, page, size);
        }
    }

    /**
     * 获取查询统计信息
     * 
     * @param userId 用户ID
     * @param startDate 开始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 统计信息
     */
    public QueryStatistics getQueryStatistics(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            logger.debug("获取查询统计 - 用户: {}, 时间范围: {} 到 {}", userId, startDate, endDate);
            
            QueryStatistics statistics = new QueryStatistics();
            
            // 基础统计
            long totalQueries = queryHistoryMapper.countByUserIdAndDateRange(userId, startDate, endDate);
            long successQueries = queryHistoryMapper.countByUserIdAndStatusAndDateRange(userId, "SUCCESS", startDate, endDate);
            long failedQueries = totalQueries - successQueries;
            
            statistics.setTotalQueries(totalQueries);
            statistics.setSuccessQueries(successQueries);
            statistics.setFailedQueries(failedQueries);
            statistics.setSuccessRate(totalQueries > 0 ? (double) successQueries / totalQueries : 0.0);
            
            // 按数据库统计
            Map<String, Long> databaseStats = queryHistoryMapper.countByDatabaseAndDateRange(userId, startDate, endDate);
            statistics.setDatabaseUsage(databaseStats);
            
            // 按日期统计
            Map<String, Long> dailyStats = queryHistoryMapper.countByDayAndDateRange(userId, startDate, endDate);
            statistics.setDailyUsage(dailyStats);
            
            // 最常用查询类型
            Map<String, Long> queryTypes = analyzeQueryTypes(userId, startDate, endDate);
            statistics.setQueryTypeUsage(queryTypes);
            
            // 平均执行时间
            Double avgExecutionTime = queryHistoryMapper.averageExecutionTime(userId, startDate, endDate);
            statistics.setAverageExecutionTime(avgExecutionTime != null ? avgExecutionTime : 0.0);
            
            logger.debug("统计信息生成完成 - 总查询数: {}, 成功率: {:.2f}%", 
                        totalQueries, statistics.getSuccessRate() * 100);
            
            return statistics;
            
        } catch (Exception e) {
            logger.error("获取查询统计异常: {}", e.getMessage(), e);
            return new QueryStatistics();
        }
    }

    /**
     * 获取智能查询推荐
     * 
     * @param userId 用户ID
     * @param databaseName 数据库名
     * @param limit 推荐数量限制
     * @return 推荐的查询列表
     */
    public List<QueryRecommendation> getQueryRecommendations(String userId, String databaseName, int limit) {
        try {
            logger.debug("获取查询推荐 - 用户: {}, 数据库: {}, 限制: {}", userId, databaseName, limit);
            
            List<QueryRecommendation> recommendations = new ArrayList<>();
            
            // 1. 最近成功的查询
            List<QueryHistory> recentSuccessful = queryHistoryMapper.selectRecentSuccessful(userId, databaseName, limit / 2);
            for (QueryHistory history : recentSuccessful) {
                recommendations.add(new QueryRecommendation(
                    "recent_success",
                    history.getNaturalQuery(),
                    history.getGeneratedSql(),
                    "最近成功执行的查询",
                    0.8
                ));
            }
            
            // 2. 相似用户的热门查询
            List<QueryHistory> popularQueries = queryHistoryMapper.selectPopularQueries(databaseName, limit / 2);
            for (QueryHistory history : popularQueries) {
                if (!containsQuery(recommendations, history.getNaturalQuery())) {
                    recommendations.add(new QueryRecommendation(
                        "popular",
                        history.getNaturalQuery(),
                        history.getGeneratedSql(),
                        "热门查询推荐",
                        0.6
                    ));
                }
            }
            
            // 按推荐度排序并限制数量
            recommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            List<QueryRecommendation> result = recommendations.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
            
            logger.debug("查询推荐生成完成 - 推荐数量: {}", result.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("获取查询推荐异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 标记查询为收藏
     * 
     * @param historyId 历史记录ID
     * @param userId 用户ID
     * @return 是否成功
     */
    public boolean favoriteQuery(Long historyId, String userId) {
        try {
            logger.debug("收藏查询 - 历史ID: {}, 用户: {}", historyId, userId);
            
            int updated = queryHistoryMapper.updateFavoriteStatus(historyId, userId, true);
            boolean success = updated > 0;
            
            if (success) {
                logger.debug("查询收藏成功");
            } else {
                logger.warn("查询收藏失败 - 可能记录不存在或权限不足");
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("收藏查询异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 取消收藏查询
     * 
     * @param historyId 历史记录ID
     * @param userId 用户ID
     * @return 是否成功
     */
    public boolean unfavoriteQuery(Long historyId, String userId) {
        try {
            logger.debug("取消收藏查询 - 历史ID: {}, 用户: {}", historyId, userId);
            
            int updated = queryHistoryMapper.updateFavoriteStatus(historyId, userId, false);
            boolean success = updated > 0;
            
            if (success) {
                logger.debug("取消收藏成功");
            } else {
                logger.warn("取消收藏失败 - 可能记录不存在或权限不足");
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("取消收藏异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除查询历史
     * 
     * @param historyId 历史记录ID
     * @param userId 用户ID
     * @return 是否成功
     */
    public boolean deleteQueryHistory(Long historyId, String userId) {
        try {
            logger.debug("删除查询历史 - 历史ID: {}, 用户: {}", historyId, userId);
            
            int deleted = queryHistoryMapper.deleteByIdAndUserId(historyId, userId);
            boolean success = deleted > 0;
            
            if (success) {
                logger.debug("查询历史删除成功");
            } else {
                logger.warn("查询历史删除失败 - 可能记录不存在或权限不足");
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("删除查询历史异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private String generateAutoTags(String naturalQuery, String generatedSql) {
        Set<String> tags = new HashSet<>();
        
        String queryLower = naturalQuery.toLowerCase();
        String sqlLower = generatedSql.toLowerCase();
        
        // 根据查询内容生成标签
        if (queryLower.contains("统计") || queryLower.contains("计数") || sqlLower.contains("count")) {
            tags.add("统计");
        }
        if (queryLower.contains("查询") || queryLower.contains("查找") || sqlLower.contains("select")) {
            tags.add("查询");
        }
        if (queryLower.contains("分组") || sqlLower.contains("group by")) {
            tags.add("分组");
        }
        if (queryLower.contains("排序") || sqlLower.contains("order by")) {
            tags.add("排序");
        }
        if (queryLower.contains("关联") || queryLower.contains("连接") || sqlLower.contains("join")) {
            tags.add("关联");
        }
        
        return String.join(",", tags);
    }

    private Map<String, Long> analyzeQueryTypes(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Long> queryTypes = new HashMap<>();
        
        try {
            List<QueryHistory> histories = queryHistoryMapper.selectByUserIdAndDateRange(userId, startDate, endDate);
            
            for (QueryHistory history : histories) {
                String sql = history.getGeneratedSql().toLowerCase();
                String type = "其他";
                
                if (sql.contains("count(")) {
                    type = "统计查询";
                } else if (sql.contains("join")) {
                    type = "关联查询";
                } else if (sql.contains("group by")) {
                    type = "分组查询";
                } else if (sql.contains("order by")) {
                    type = "排序查询";
                } else if (sql.startsWith("select")) {
                    type = "普通查询";
                }
                
                queryTypes.put(type, queryTypes.getOrDefault(type, 0L) + 1);
            }
            
        } catch (Exception e) {
            logger.error("分析查询类型异常: {}", e.getMessage(), e);
        }
        
        return queryTypes;
    }

    private boolean containsQuery(List<QueryRecommendation> recommendations, String naturalQuery) {
        return recommendations.stream()
                .anyMatch(rec -> rec.getNaturalQuery().equals(naturalQuery));
    }

    // 内部类：查询历史结果
    public static class QueryHistoryResult {
        private List<QueryHistory> histories;
        private long totalCount;
        private int currentPage;
        private int pageSize;
        private int totalPages;

        public QueryHistoryResult(List<QueryHistory> histories, long totalCount, int currentPage, int pageSize) {
            this.histories = histories;
            this.totalCount = totalCount;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
            this.totalPages = (int) Math.ceil((double) totalCount / pageSize);
        }

        // Getters
        public List<QueryHistory> getHistories() { return histories; }
        public long getTotalCount() { return totalCount; }
        public int getCurrentPage() { return currentPage; }
        public int getPageSize() { return pageSize; }
        public int getTotalPages() { return totalPages; }
    }

    // 内部类：查询统计
    public static class QueryStatistics {
        private long totalQueries;
        private long successQueries;
        private long failedQueries;
        private double successRate;
        private double averageExecutionTime;
        private Map<String, Long> databaseUsage;
        private Map<String, Long> dailyUsage;
        private Map<String, Long> queryTypeUsage;

        // Getters and Setters
        public long getTotalQueries() { return totalQueries; }
        public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }

        public long getSuccessQueries() { return successQueries; }
        public void setSuccessQueries(long successQueries) { this.successQueries = successQueries; }

        public long getFailedQueries() { return failedQueries; }
        public void setFailedQueries(long failedQueries) { this.failedQueries = failedQueries; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }

        public double getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(double averageExecutionTime) { this.averageExecutionTime = averageExecutionTime; }

        public Map<String, Long> getDatabaseUsage() { return databaseUsage; }
        public void setDatabaseUsage(Map<String, Long> databaseUsage) { this.databaseUsage = databaseUsage; }

        public Map<String, Long> getDailyUsage() { return dailyUsage; }
        public void setDailyUsage(Map<String, Long> dailyUsage) { this.dailyUsage = dailyUsage; }

        public Map<String, Long> getQueryTypeUsage() { return queryTypeUsage; }
        public void setQueryTypeUsage(Map<String, Long> queryTypeUsage) { this.queryTypeUsage = queryTypeUsage; }
    }

    // 内部类：查询推荐
    public static class QueryRecommendation {
        private String type;
        private String naturalQuery;
        private String generatedSql;
        private String description;
        private double score;

        public QueryRecommendation(String type, String naturalQuery, String generatedSql, 
                                 String description, double score) {
            this.type = type;
            this.naturalQuery = naturalQuery;
            this.generatedSql = generatedSql;
            this.description = description;
            this.score = score;
        }

        // Getters
        public String getType() { return type; }
        public String getNaturalQuery() { return naturalQuery; }
        public String getGeneratedSql() { return generatedSql; }
        public String getDescription() { return description; }
        public double getScore() { return score; }
    }
}