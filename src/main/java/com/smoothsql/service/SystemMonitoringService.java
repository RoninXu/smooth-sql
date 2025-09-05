package com.smoothsql.service;

import com.smoothsql.mapper.QueryHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统监控服务
 * 
 * 功能包括：
 * 1. 系统性能实时监控
 * 2. 查询性能统计分析
 * 3. 用户行为监控
 * 4. 异常检测和告警
 * 5. 资源使用率监控
 * 6. 健康检查和诊断
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@Service
public class SystemMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(SystemMonitoringService.class);

    @Autowired
    private QueryHistoryMapper queryHistoryMapper;

    // 性能指标存储
    private final Map<String, PerformanceMetric> performanceMetrics = new ConcurrentHashMap<>();
    
    // 系统警报存储
    private final List<SystemAlert> systemAlerts = new ArrayList<>();
    
    // 查询性能历史
    private final List<QueryPerformanceRecord> queryPerformanceHistory = new ArrayList<>();
    
    // 用户活动监控
    private final Map<String, UserActivityMetric> userActivityMetrics = new ConcurrentHashMap<>();

    /**
     * 获取系统健康状态
     * 
     * @return 系统健康报告
     */
    public SystemHealthReport getSystemHealth() {
        logger.debug("获取系统健康状态");

        try {
            SystemHealthReport report = new SystemHealthReport();
            report.setTimestamp(LocalDateTime.now());
            
            // JVM内存使用情况
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            double memoryUsage = maxMemory > 0 ? (double) usedMemory / maxMemory : 0.0;
            
            report.setMemoryUsage(memoryUsage);
            report.setMemoryUsed(usedMemory / 1024 / 1024); // MB
            report.setMemoryMax(maxMemory / 1024 / 1024); // MB
            
            // JVM运行时间
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            long uptime = runtimeBean.getUptime();
            report.setUptime(uptime);
            
            // 活跃连接数
            int activeConnections = performanceMetrics.size();
            report.setActiveConnections(activeConnections);
            
            // 查询性能统计
            QueryPerformanceStatistics queryStats = calculateQueryPerformanceStatistics();
            report.setQueryStats(queryStats);
            
            // 系统状态评估
            String healthStatus = evaluateSystemHealth(memoryUsage, activeConnections, queryStats);
            report.setOverallStatus(healthStatus);
            
            // 当前警报
            report.setActiveAlerts(getActiveAlerts());
            
            logger.debug("系统健康状态获取完成 - 状态: {}, 内存使用: {:.2f}%, 活跃连接: {}", 
                        healthStatus, memoryUsage * 100, activeConnections);
            
            return report;
            
        } catch (Exception e) {
            logger.error("获取系统健康状态异常: {}", e.getMessage(), e);
            return createErrorHealthReport();
        }
    }

    /**
     * 记录查询性能
     * 
     * @param sql SQL语句
     * @param executionTime 执行时间
     * @param resultCount 结果数量
     * @param userId 用户ID
     * @param databaseName 数据库名
     */
    public void recordQueryPerformance(String sql, long executionTime, int resultCount, 
                                     String userId, String databaseName) {
        try {
            QueryPerformanceRecord record = new QueryPerformanceRecord();
            record.setSql(sql);
            record.setExecutionTime(executionTime);
            record.setResultCount(resultCount);
            record.setUserId(userId);
            record.setDatabaseName(databaseName);
            record.setTimestamp(LocalDateTime.now());
            
            // 添加到历史记录
            queryPerformanceHistory.add(record);
            
            // 保持历史记录在合理范围内
            if (queryPerformanceHistory.size() > 10000) {
                queryPerformanceHistory.remove(0);
            }
            
            // 更新性能指标
            updatePerformanceMetrics(record);
            
            // 检查是否需要告警
            checkPerformanceThresholds(record);
            
            logger.debug("查询性能记录完成 - 用户: {}, 耗时: {}ms, 结果数: {}", 
                        userId, executionTime, resultCount);
            
        } catch (Exception e) {
            logger.error("记录查询性能异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 记录用户活动
     * 
     * @param userId 用户ID
     * @param activity 活动类型
     * @param details 活动详情
     */
    public void recordUserActivity(String userId, String activity, String details) {
        try {
            UserActivityMetric metric = userActivityMetrics.computeIfAbsent(userId, k -> {
                UserActivityMetric m = new UserActivityMetric();
                m.setUserId(userId);
                m.setFirstActivity(LocalDateTime.now());
                return m;
            });
            
            metric.setLastActivity(LocalDateTime.now());
            metric.incrementActivityCount(activity);
            
            // 记录具体活动
            UserActivity activityRecord = new UserActivity();
            activityRecord.setActivity(activity);
            activityRecord.setDetails(details);
            activityRecord.setTimestamp(LocalDateTime.now());
            metric.addActivity(activityRecord);
            
            logger.debug("用户活动记录完成 - 用户: {}, 活动: {}", userId, activity);
            
        } catch (Exception e) {
            logger.error("记录用户活动异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取性能分析报告
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 性能分析报告
     */
    public PerformanceAnalysisReport getPerformanceAnalysis(LocalDateTime startTime, LocalDateTime endTime) {
        logger.info("获取性能分析报告 - 时间范围: {} - {}", startTime, endTime);

        try {
            List<QueryPerformanceRecord> filteredRecords = queryPerformanceHistory.stream()
                .filter(record -> record.getTimestamp().isAfter(startTime) && 
                                record.getTimestamp().isBefore(endTime))
                .collect(Collectors.toList());
            
            PerformanceAnalysisReport report = new PerformanceAnalysisReport();
            report.setStartTime(startTime);
            report.setEndTime(endTime);
            
            // 查询统计
            report.setTotalQueries(filteredRecords.size());
            
            if (!filteredRecords.isEmpty()) {
                // 执行时间统计
                DoubleSummaryStatistics timeStats = filteredRecords.stream()
                    .mapToDouble(QueryPerformanceRecord::getExecutionTime)
                    .summaryStatistics();
                
                report.setAvgExecutionTime(timeStats.getAverage());
                report.setMaxExecutionTime((long) timeStats.getMax());
                report.setMinExecutionTime((long) timeStats.getMin());
                
                // 慢查询分析
                List<QueryPerformanceRecord> slowQueries = filteredRecords.stream()
                    .filter(record -> record.getExecutionTime() > 5000) // 超过5秒
                    .sorted((a, b) -> Long.compare(b.getExecutionTime(), a.getExecutionTime()))
                    .limit(10)
                    .collect(Collectors.toList());
                
                report.setSlowQueries(slowQueries);
                
                // 用户活动统计
                Map<String, Long> userQueryCounts = filteredRecords.stream()
                    .collect(Collectors.groupingBy(
                        QueryPerformanceRecord::getUserId,
                        Collectors.counting()
                    ));
                
                report.setUserQueryCounts(userQueryCounts);
                
                // 数据库使用统计
                Map<String, Long> databaseUsage = filteredRecords.stream()
                    .collect(Collectors.groupingBy(
                        QueryPerformanceRecord::getDatabaseName,
                        Collectors.counting()
                    ));
                
                report.setDatabaseUsage(databaseUsage);
                
                // 时间趋势分析
                Map<String, Long> hourlyTrend = filteredRecords.stream()
                    .collect(Collectors.groupingBy(
                        record -> record.getTimestamp().getHour() + ":00",
                        Collectors.counting()
                    ));
                
                report.setHourlyTrend(hourlyTrend);
            }
            
            logger.info("性能分析报告生成完成 - 查询总数: {}", report.getTotalQueries());
            
            return report;
            
        } catch (Exception e) {
            logger.error("获取性能分析报告异常: {}", e.getMessage(), e);
            return createErrorPerformanceReport(startTime, endTime);
        }
    }

    /**
     * 获取用户行为分析
     * 
     * @param userId 用户ID（可选，为null时获取所有用户）
     * @return 用户行为分析报告
     */
    public UserBehaviorAnalysisReport getUserBehaviorAnalysis(String userId) {
        logger.debug("获取用户行为分析 - 用户: {}", userId);

        try {
            UserBehaviorAnalysisReport report = new UserBehaviorAnalysisReport();
            report.setGeneratedAt(LocalDateTime.now());
            
            if (userId != null) {
                // 单用户分析
                UserActivityMetric metric = userActivityMetrics.get(userId);
                if (metric != null) {
                    report.setSingleUserAnalysis(analyzeSingleUser(metric));
                }
            } else {
                // 全体用户分析
                report.setOverallAnalysis(analyzeAllUsers());
            }
            
            return report;
            
        } catch (Exception e) {
            logger.error("获取用户行为分析异常: {}", e.getMessage(), e);
            return createErrorUserBehaviorReport();
        }
    }

    /**
     * 添加系统警报
     * 
     * @param alertType 警报类型
     * @param message 警报消息
     * @param severity 严重程度
     */
    public void addSystemAlert(String alertType, String message, AlertSeverity severity) {
        SystemAlert alert = new SystemAlert();
        alert.setId(UUID.randomUUID().toString());
        alert.setType(alertType);
        alert.setMessage(message);
        alert.setSeverity(severity);
        alert.setTimestamp(LocalDateTime.now());
        alert.setStatus("ACTIVE");
        
        synchronized (systemAlerts) {
            systemAlerts.add(alert);
            
            // 保持警报列表在合理大小
            if (systemAlerts.size() > 1000) {
                systemAlerts.removeIf(a -> "RESOLVED".equals(a.getStatus()) && 
                                         a.getTimestamp().isBefore(LocalDateTime.now().minusDays(7)));
            }
        }
        
        logger.warn("系统警报: [{}] {} - {}", severity, alertType, message);
    }

    /**
     * 定期清理过期数据
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void cleanupExpiredData() {
        logger.info("开始清理过期监控数据");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
            
            // 清理过期的查询性能记录
            int removedRecords = queryPerformanceHistory.size();
            queryPerformanceHistory.removeIf(record -> record.getTimestamp().isBefore(cutoffTime));
            removedRecords -= queryPerformanceHistory.size();
            
            // 清理过期的用户活动记录
            userActivityMetrics.values().forEach(metric -> 
                metric.getActivities().removeIf(activity -> 
                    activity.getTimestamp().isBefore(cutoffTime)));
            
            // 清理已解决的旧警报
            synchronized (systemAlerts) {
                int removedAlerts = systemAlerts.size();
                systemAlerts.removeIf(alert -> "RESOLVED".equals(alert.getStatus()) && 
                                             alert.getTimestamp().isBefore(cutoffTime));
                removedAlerts -= systemAlerts.size();
                
                if (removedAlerts > 0) {
                    logger.info("清理过期警报: {} 条", removedAlerts);
                }
            }
            
            logger.info("过期数据清理完成 - 清理查询记录: {} 条", removedRecords);
            
        } catch (Exception e) {
            logger.error("清理过期数据异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 定期系统健康检查
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void performHealthCheck() {
        try {
            SystemHealthReport health = getSystemHealth();
            
            // 检查内存使用率
            if (health.getMemoryUsage() > 0.9) {
                addSystemAlert("HIGH_MEMORY_USAGE", 
                              String.format("内存使用率过高: %.2f%%", health.getMemoryUsage() * 100),
                              AlertSeverity.HIGH);
            }
            
            // 检查慢查询
            long slowQueryCount = queryPerformanceHistory.stream()
                .filter(record -> record.getTimestamp().isAfter(LocalDateTime.now().minusMinutes(5)))
                .filter(record -> record.getExecutionTime() > 10000) // 超过10秒
                .count();
            
            if (slowQueryCount > 5) {
                addSystemAlert("FREQUENT_SLOW_QUERIES", 
                              String.format("最近5分钟内有 %d 个慢查询", slowQueryCount),
                              AlertSeverity.MEDIUM);
            }
            
        } catch (Exception e) {
            logger.error("系统健康检查异常: {}", e.getMessage(), e);
        }
    }

    // 私有辅助方法
    private QueryPerformanceStatistics calculateQueryPerformanceStatistics() {
        if (queryPerformanceHistory.isEmpty()) {
            return new QueryPerformanceStatistics(0L, 0.0, 0L, 0L);
        }
        
        DoubleSummaryStatistics stats = queryPerformanceHistory.stream()
            .filter(record -> record.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
            .mapToDouble(QueryPerformanceRecord::getExecutionTime)
            .summaryStatistics();
        
        return new QueryPerformanceStatistics(
            stats.getCount(),
            stats.getAverage(),
            (long) stats.getMax(),
            (long) stats.getMin()
        );
    }

    private String evaluateSystemHealth(double memoryUsage, int activeConnections, 
                                       QueryPerformanceStatistics queryStats) {
        if (memoryUsage > 0.95 || queryStats.getAvgExecutionTime() > 10000) {
            return "CRITICAL";
        } else if (memoryUsage > 0.85 || queryStats.getAvgExecutionTime() > 5000 || activeConnections > 100) {
            return "WARNING";
        } else {
            return "HEALTHY";
        }
    }

    private List<SystemAlert> getActiveAlerts() {
        synchronized (systemAlerts) {
            return systemAlerts.stream()
                .filter(alert -> "ACTIVE".equals(alert.getStatus()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());
        }
    }

    private void updatePerformanceMetrics(QueryPerformanceRecord record) {
        String key = record.getUserId() + "_" + record.getDatabaseName();
        
        PerformanceMetric metric = performanceMetrics.computeIfAbsent(key, k -> {
            PerformanceMetric m = new PerformanceMetric();
            m.setKey(key);
            m.setUserId(record.getUserId());
            m.setDatabaseName(record.getDatabaseName());
            return m;
        });
        
        metric.addQueryRecord(record.getExecutionTime(), record.getResultCount());
    }

    private void checkPerformanceThresholds(QueryPerformanceRecord record) {
        if (record.getExecutionTime() > 30000) { // 超过30秒
            addSystemAlert("VERY_SLOW_QUERY",
                          String.format("检测到极慢查询 - 用户: %s, 耗时: %dms", 
                                      record.getUserId(), record.getExecutionTime()),
                          AlertSeverity.HIGH);
        } else if (record.getExecutionTime() > 10000) { // 超过10秒
            addSystemAlert("SLOW_QUERY",
                          String.format("检测到慢查询 - 用户: %s, 耗时: %dms", 
                                      record.getUserId(), record.getExecutionTime()),
                          AlertSeverity.MEDIUM);
        }
    }

    private SingleUserAnalysis analyzeSingleUser(UserActivityMetric metric) {
        SingleUserAnalysis analysis = new SingleUserAnalysis();
        analysis.setUserId(metric.getUserId());
        analysis.setFirstActivity(metric.getFirstActivity());
        analysis.setLastActivity(metric.getLastActivity());
        analysis.setTotalActivities(metric.getTotalActivities());
        analysis.setActivityBreakdown(metric.getActivityCounts());
        
        // 计算活跃度
        long daysSinceFirst = java.time.Duration.between(metric.getFirstActivity(), LocalDateTime.now()).toDays();
        analysis.setActivityFrequency(daysSinceFirst > 0 ? (double) metric.getTotalActivities() / daysSinceFirst : 0.0);
        
        return analysis;
    }

    private OverallUserAnalysis analyzeAllUsers() {
        OverallUserAnalysis analysis = new OverallUserAnalysis();
        analysis.setTotalUsers(userActivityMetrics.size());
        
        // 活跃用户统计
        LocalDateTime last7Days = LocalDateTime.now().minusDays(7);
        long activeUsers = userActivityMetrics.values().stream()
            .mapToLong(metric -> metric.getLastActivity().isAfter(last7Days) ? 1 : 0)
            .sum();
        
        analysis.setActiveUsers((int) activeUsers);
        
        // 活动分布统计
        Map<String, Long> activityDistribution = userActivityMetrics.values().stream()
            .flatMap(metric -> metric.getActivityCounts().entrySet().stream())
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.summingLong(Map.Entry::getValue)
            ));
        
        analysis.setActivityDistribution(activityDistribution);
        
        return analysis;
    }

    private SystemHealthReport createErrorHealthReport() {
        SystemHealthReport report = new SystemHealthReport();
        report.setTimestamp(LocalDateTime.now());
        report.setOverallStatus("ERROR");
        return report;
    }

    private PerformanceAnalysisReport createErrorPerformanceReport(LocalDateTime startTime, LocalDateTime endTime) {
        PerformanceAnalysisReport report = new PerformanceAnalysisReport();
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        report.setTotalQueries(0);
        return report;
    }

    private UserBehaviorAnalysisReport createErrorUserBehaviorReport() {
        UserBehaviorAnalysisReport report = new UserBehaviorAnalysisReport();
        report.setGeneratedAt(LocalDateTime.now());
        return report;
    }

    // 内部类定义
    public static class SystemHealthReport {
        private LocalDateTime timestamp;
        private String overallStatus;
        private Double memoryUsage;
        private Long memoryUsed;
        private Long memoryMax;
        private Long uptime;
        private Integer activeConnections;
        private QueryPerformanceStatistics queryStats;
        private List<SystemAlert> activeAlerts;

        // Getters and Setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public String getOverallStatus() { return overallStatus; }
        public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
        
        public Double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(Double memoryUsage) { this.memoryUsage = memoryUsage; }
        
        public Long getMemoryUsed() { return memoryUsed; }
        public void setMemoryUsed(Long memoryUsed) { this.memoryUsed = memoryUsed; }
        
        public Long getMemoryMax() { return memoryMax; }
        public void setMemoryMax(Long memoryMax) { this.memoryMax = memoryMax; }
        
        public Long getUptime() { return uptime; }
        public void setUptime(Long uptime) { this.uptime = uptime; }
        
        public Integer getActiveConnections() { return activeConnections; }
        public void setActiveConnections(Integer activeConnections) { this.activeConnections = activeConnections; }
        
        public QueryPerformanceStatistics getQueryStats() { return queryStats; }
        public void setQueryStats(QueryPerformanceStatistics queryStats) { this.queryStats = queryStats; }
        
        public List<SystemAlert> getActiveAlerts() { return activeAlerts; }
        public void setActiveAlerts(List<SystemAlert> activeAlerts) { this.activeAlerts = activeAlerts; }
    }

    public static class QueryPerformanceStatistics {
        private Long totalQueries;
        private Double avgExecutionTime;
        private Long maxExecutionTime;
        private Long minExecutionTime;

        public QueryPerformanceStatistics(Long totalQueries, Double avgExecutionTime, 
                                        Long maxExecutionTime, Long minExecutionTime) {
            this.totalQueries = totalQueries;
            this.avgExecutionTime = avgExecutionTime;
            this.maxExecutionTime = maxExecutionTime;
            this.minExecutionTime = minExecutionTime;
        }

        // Getters
        public Long getTotalQueries() { return totalQueries; }
        public Double getAvgExecutionTime() { return avgExecutionTime; }
        public Long getMaxExecutionTime() { return maxExecutionTime; }
        public Long getMinExecutionTime() { return minExecutionTime; }
    }

    public static class QueryPerformanceRecord {
        private String sql;
        private Long executionTime;
        private Integer resultCount;
        private String userId;
        private String databaseName;
        private LocalDateTime timestamp;

        // Getters and Setters
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        
        public Long getExecutionTime() { return executionTime; }
        public void setExecutionTime(Long executionTime) { this.executionTime = executionTime; }
        
        public Integer getResultCount() { return resultCount; }
        public void setResultCount(Integer resultCount) { this.resultCount = resultCount; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class UserActivityMetric {
        private String userId;
        private LocalDateTime firstActivity;
        private LocalDateTime lastActivity;
        private Integer totalActivities = 0;
        private Map<String, Long> activityCounts = new HashMap<>();
        private List<UserActivity> activities = new ArrayList<>();

        public void incrementActivityCount(String activity) {
            totalActivities++;
            activityCounts.merge(activity, 1L, Long::sum);
        }

        public void addActivity(UserActivity activity) {
            activities.add(activity);
            if (activities.size() > 1000) {
                activities.remove(0);
            }
        }

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public LocalDateTime getFirstActivity() { return firstActivity; }
        public void setFirstActivity(LocalDateTime firstActivity) { this.firstActivity = firstActivity; }
        
        public LocalDateTime getLastActivity() { return lastActivity; }
        public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }
        
        public Integer getTotalActivities() { return totalActivities; }
        public void setTotalActivities(Integer totalActivities) { this.totalActivities = totalActivities; }
        
        public Map<String, Long> getActivityCounts() { return activityCounts; }
        public void setActivityCounts(Map<String, Long> activityCounts) { this.activityCounts = activityCounts; }
        
        public List<UserActivity> getActivities() { return activities; }
        public void setActivities(List<UserActivity> activities) { this.activities = activities; }
    }

    public static class UserActivity {
        private String activity;
        private String details;
        private LocalDateTime timestamp;

        // Getters and Setters
        public String getActivity() { return activity; }
        public void setActivity(String activity) { this.activity = activity; }
        
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class SystemAlert {
        private String id;
        private String type;
        private String message;
        private AlertSeverity severity;
        private LocalDateTime timestamp;
        private String status;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public AlertSeverity getSeverity() { return severity; }
        public void setSeverity(AlertSeverity severity) { this.severity = severity; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PerformanceMetric {
        private String key;
        private String userId;
        private String databaseName;
        private Long totalQueries = 0L;
        private Long totalExecutionTime = 0L;
        private Long totalResults = 0L;
        private Long maxExecutionTime = 0L;

        public void addQueryRecord(long executionTime, int resultCount) {
            totalQueries++;
            totalExecutionTime += executionTime;
            totalResults += resultCount;
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
        }

        // Getters and Setters
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        
        public Long getTotalQueries() { return totalQueries; }
        public void setTotalQueries(Long totalQueries) { this.totalQueries = totalQueries; }
        
        public Long getTotalExecutionTime() { return totalExecutionTime; }
        public void setTotalExecutionTime(Long totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }
        
        public Long getTotalResults() { return totalResults; }
        public void setTotalResults(Long totalResults) { this.totalResults = totalResults; }
        
        public Long getMaxExecutionTime() { return maxExecutionTime; }
        public void setMaxExecutionTime(Long maxExecutionTime) { this.maxExecutionTime = maxExecutionTime; }
    }

    public static class PerformanceAnalysisReport {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer totalQueries;
        private Double avgExecutionTime;
        private Long maxExecutionTime;
        private Long minExecutionTime;
        private List<QueryPerformanceRecord> slowQueries;
        private Map<String, Long> userQueryCounts;
        private Map<String, Long> databaseUsage;
        private Map<String, Long> hourlyTrend;

        // Getters and Setters
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public Integer getTotalQueries() { return totalQueries; }
        public void setTotalQueries(Integer totalQueries) { this.totalQueries = totalQueries; }
        
        public Double getAvgExecutionTime() { return avgExecutionTime; }
        public void setAvgExecutionTime(Double avgExecutionTime) { this.avgExecutionTime = avgExecutionTime; }
        
        public Long getMaxExecutionTime() { return maxExecutionTime; }
        public void setMaxExecutionTime(Long maxExecutionTime) { this.maxExecutionTime = maxExecutionTime; }
        
        public Long getMinExecutionTime() { return minExecutionTime; }
        public void setMinExecutionTime(Long minExecutionTime) { this.minExecutionTime = minExecutionTime; }
        
        public List<QueryPerformanceRecord> getSlowQueries() { return slowQueries; }
        public void setSlowQueries(List<QueryPerformanceRecord> slowQueries) { this.slowQueries = slowQueries; }
        
        public Map<String, Long> getUserQueryCounts() { return userQueryCounts; }
        public void setUserQueryCounts(Map<String, Long> userQueryCounts) { this.userQueryCounts = userQueryCounts; }
        
        public Map<String, Long> getDatabaseUsage() { return databaseUsage; }
        public void setDatabaseUsage(Map<String, Long> databaseUsage) { this.databaseUsage = databaseUsage; }
        
        public Map<String, Long> getHourlyTrend() { return hourlyTrend; }
        public void setHourlyTrend(Map<String, Long> hourlyTrend) { this.hourlyTrend = hourlyTrend; }
    }

    public static class UserBehaviorAnalysisReport {
        private LocalDateTime generatedAt;
        private SingleUserAnalysis singleUserAnalysis;
        private OverallUserAnalysis overallAnalysis;

        // Getters and Setters
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        
        public SingleUserAnalysis getSingleUserAnalysis() { return singleUserAnalysis; }
        public void setSingleUserAnalysis(SingleUserAnalysis singleUserAnalysis) { this.singleUserAnalysis = singleUserAnalysis; }
        
        public OverallUserAnalysis getOverallAnalysis() { return overallAnalysis; }
        public void setOverallAnalysis(OverallUserAnalysis overallAnalysis) { this.overallAnalysis = overallAnalysis; }
    }

    public static class SingleUserAnalysis {
        private String userId;
        private LocalDateTime firstActivity;
        private LocalDateTime lastActivity;
        private Integer totalActivities;
        private Map<String, Long> activityBreakdown;
        private Double activityFrequency;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public LocalDateTime getFirstActivity() { return firstActivity; }
        public void setFirstActivity(LocalDateTime firstActivity) { this.firstActivity = firstActivity; }
        
        public LocalDateTime getLastActivity() { return lastActivity; }
        public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }
        
        public Integer getTotalActivities() { return totalActivities; }
        public void setTotalActivities(Integer totalActivities) { this.totalActivities = totalActivities; }
        
        public Map<String, Long> getActivityBreakdown() { return activityBreakdown; }
        public void setActivityBreakdown(Map<String, Long> activityBreakdown) { this.activityBreakdown = activityBreakdown; }
        
        public Double getActivityFrequency() { return activityFrequency; }
        public void setActivityFrequency(Double activityFrequency) { this.activityFrequency = activityFrequency; }
    }

    public static class OverallUserAnalysis {
        private Integer totalUsers;
        private Integer activeUsers;
        private Map<String, Long> activityDistribution;

        // Getters and Setters
        public Integer getTotalUsers() { return totalUsers; }
        public void setTotalUsers(Integer totalUsers) { this.totalUsers = totalUsers; }
        
        public Integer getActiveUsers() { return activeUsers; }
        public void setActiveUsers(Integer activeUsers) { this.activeUsers = activeUsers; }
        
        public Map<String, Long> getActivityDistribution() { return activityDistribution; }
        public void setActivityDistribution(Map<String, Long> activityDistribution) { this.activityDistribution = activityDistribution; }
    }

    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}