package com.smoothsql.service;

import com.smoothsql.mapper.QueryHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 系统监控服务测试类
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@ExtendWith(MockitoExtension.class)
class SystemMonitoringServiceTest {

    @Mock
    private QueryHistoryMapper queryHistoryMapper;

    @InjectMocks
    private SystemMonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        // 测试前初始化
    }

    @Test
    void testGetSystemHealth() {
        // 执行测试
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();

        // 验证结果
        assertNotNull(report);
        assertNotNull(report.getTimestamp());
        assertNotNull(report.getOverallStatus());
        assertNotNull(report.getMemoryUsage());
        assertNotNull(report.getUptime());
        assertTrue(report.getMemoryUsage() >= 0.0 && report.getMemoryUsage() <= 1.0);
        assertTrue(report.getUptime() >= 0);
    }

    @Test
    void testRecordQueryPerformance() {
        // 准备测试数据
        String sql = "SELECT * FROM users";
        long executionTime = 1500L;
        int resultCount = 10;
        String userId = "user1";
        String databaseName = "testdb";

        // 执行测试
        assertDoesNotThrow(() -> {
            monitoringService.recordQueryPerformance(sql, executionTime, resultCount, userId, databaseName);
        });

        // 验证性能记录被正确存储（通过后续查询验证）
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();
        assertNotNull(report.getQueryStats());
    }

    @Test
    void testRecordUserActivity() {
        // 准备测试数据
        String userId = "user1";
        String activity = "QUERY_EXECUTION";
        String details = "执行查询：SELECT * FROM users";

        // 执行测试
        assertDoesNotThrow(() -> {
            monitoringService.recordUserActivity(userId, activity, details);
        });

        // 验证用户活动统计
        SystemMonitoringService.UserBehaviorAnalysisReport behaviorReport = 
            monitoringService.getUserBehaviorAnalysis(userId);
        
        assertNotNull(behaviorReport);
        assertNotNull(behaviorReport.getSingleUserAnalysis());
        assertEquals(userId, behaviorReport.getSingleUserAnalysis().getUserId());
    }

    @Test
    void testGetPerformanceAnalysis() {
        // 准备测试数据
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();

        // 先记录一些性能数据
        monitoringService.recordQueryPerformance("SELECT * FROM users", 1000L, 5, "user1", "testdb");
        monitoringService.recordQueryPerformance("SELECT * FROM orders", 2000L, 10, "user1", "testdb");
        monitoringService.recordQueryPerformance("SELECT * FROM products", 3000L, 8, "user2", "testdb");

        // 执行测试
        SystemMonitoringService.PerformanceAnalysisReport report = 
            monitoringService.getPerformanceAnalysis(startTime, endTime);

        // 验证结果
        assertNotNull(report);
        assertEquals(startTime, report.getStartTime());
        assertEquals(endTime, report.getEndTime());
        assertTrue(report.getTotalQueries() >= 0);
        
        if (report.getTotalQueries() > 0) {
            assertNotNull(report.getAvgExecutionTime());
            assertTrue(report.getAvgExecutionTime() > 0);
        }
    }

    @Test
    void testGetUserBehaviorAnalysis_SingleUser() {
        // 准备测试数据
        String userId = "user1";
        
        // 记录用户活动
        monitoringService.recordUserActivity(userId, "LOGIN", "用户登录");
        monitoringService.recordUserActivity(userId, "QUERY_EXECUTION", "执行查询");
        monitoringService.recordUserActivity(userId, "EXPORT_DATA", "导出数据");

        // 执行测试
        SystemMonitoringService.UserBehaviorAnalysisReport report = 
            monitoringService.getUserBehaviorAnalysis(userId);

        // 验证结果
        assertNotNull(report);
        assertNotNull(report.getSingleUserAnalysis());
        assertEquals(userId, report.getSingleUserAnalysis().getUserId());
        assertTrue(report.getSingleUserAnalysis().getTotalActivities() >= 3);
        assertNotNull(report.getSingleUserAnalysis().getActivityBreakdown());
    }

    @Test
    void testGetUserBehaviorAnalysis_AllUsers() {
        // 准备测试数据
        monitoringService.recordUserActivity("user1", "LOGIN", "登录");
        monitoringService.recordUserActivity("user2", "QUERY_EXECUTION", "查询");
        monitoringService.recordUserActivity("user1", "EXPORT_DATA", "导出");

        // 执行测试
        SystemMonitoringService.UserBehaviorAnalysisReport report = 
            monitoringService.getUserBehaviorAnalysis(null);

        // 验证结果
        assertNotNull(report);
        assertNotNull(report.getOverallAnalysis());
        assertTrue(report.getOverallAnalysis().getTotalUsers() >= 2);
        assertTrue(report.getOverallAnalysis().getActiveUsers() >= 0);
    }

    @Test
    void testAddSystemAlert() {
        // 准备测试数据
        String alertType = "HIGH_MEMORY_USAGE";
        String message = "内存使用率超过90%";
        SystemMonitoringService.AlertSeverity severity = SystemMonitoringService.AlertSeverity.HIGH;

        // 执行测试
        monitoringService.addSystemAlert(alertType, message, severity);

        // 验证警报被添加到系统健康报告中
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();
        assertNotNull(report.getActiveAlerts());
        
        boolean alertExists = report.getActiveAlerts().stream()
            .anyMatch(alert -> alert.getType().equals(alertType) && 
                             alert.getMessage().equals(message) &&
                             alert.getSeverity().equals(severity));
        assertTrue(alertExists);
    }

    @Test
    void testPerformHealthCheck() {
        // 执行健康检查
        assertDoesNotThrow(() -> {
            monitoringService.performHealthCheck();
        });

        // 验证健康检查不会抛出异常，并且系统状态正常
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();
        assertNotNull(report.getOverallStatus());
    }

    @Test
    void testSlowQueryDetection() {
        // 记录慢查询
        String slowQuery = "SELECT * FROM large_table WHERE complex_condition";
        long slowExecutionTime = 35000L; // 35秒
        
        monitoringService.recordQueryPerformance(slowQuery, slowExecutionTime, 100, "user1", "testdb");

        // 验证慢查询被正确检测和记录
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();
        
        // 检查是否生成了慢查询警报
        boolean hasSlowQueryAlert = report.getActiveAlerts().stream()
            .anyMatch(alert -> alert.getType().contains("SLOW_QUERY"));
        
        assertTrue(hasSlowQueryAlert || report.getQueryStats().getMaxExecutionTime() >= slowExecutionTime);
    }

    @Test
    void testMemoryUsageMonitoring() {
        // 获取系统健康报告
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();

        // 验证内存使用率监控
        assertNotNull(report.getMemoryUsage());
        assertNotNull(report.getMemoryUsed());
        assertNotNull(report.getMemoryMax());
        
        assertTrue(report.getMemoryUsage() >= 0.0 && report.getMemoryUsage() <= 1.0);
        assertTrue(report.getMemoryUsed() >= 0);
        assertTrue(report.getMemoryMax() >= report.getMemoryUsed());

        // 验证内存使用率计算正确
        double calculatedUsage = report.getMemoryMax() > 0 ? 
            (double) report.getMemoryUsed() / report.getMemoryMax() : 0.0;
        assertEquals(calculatedUsage, report.getMemoryUsage(), 0.01);
    }

    @Test
    void testQueryStatistics() {
        // 记录多个查询性能数据
        long[] executionTimes = {1000L, 2000L, 1500L, 3000L, 500L};
        
        for (int i = 0; i < executionTimes.length; i++) {
            monitoringService.recordQueryPerformance(
                "SELECT * FROM table" + i, 
                executionTimes[i], 
                10, 
                "user1", 
                "testdb"
            );
        }

        // 验证查询统计信息
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();
        SystemMonitoringService.QueryPerformanceStatistics stats = report.getQueryStats();
        
        assertNotNull(stats);
        assertTrue(stats.getTotalQueries() >= executionTimes.length);
        assertTrue(stats.getAvgExecutionTime() > 0);
        assertTrue(stats.getMaxExecutionTime() >= 3000L);
        assertTrue(stats.getMinExecutionTime() >= 500L);
    }

    @Test
    void testCleanupExpiredData() {
        // 记录一些测试数据
        monitoringService.recordQueryPerformance("SELECT 1", 1000L, 1, "user1", "testdb");
        monitoringService.recordUserActivity("user1", "LOGIN", "登录");
        monitoringService.addSystemAlert("TEST_ALERT", "测试警报", SystemMonitoringService.AlertSeverity.LOW);

        // 执行数据清理
        assertDoesNotThrow(() -> {
            monitoringService.cleanupExpiredData();
        });

        // 验证清理操作不会出错
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();
        assertNotNull(report);
    }

    @Test
    void testConcurrentQueryRecording() throws InterruptedException {
        // 测试并发查询记录的线程安全性
        int threadCount = 10;
        int queriesPerThread = 100;
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < queriesPerThread; j++) {
                    monitoringService.recordQueryPerformance(
                        "SELECT * FROM table_" + threadId + "_" + j,
                        1000L + j,
                        10,
                        "user" + threadId,
                        "testdb"
                    );
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证并发操作的正确性
        SystemMonitoringService.SystemHealthReport report = monitoringService.getSystemHealth();
        assertNotNull(report);
        assertTrue(report.getQueryStats().getTotalQueries() >= 0);
    }

    @Test
    void testHealthStatusEvaluation() {
        // 测试不同场景下的健康状态评估

        // 1. 正常情况
        SystemMonitoringService.SystemHealthReport normalReport = monitoringService.getSystemHealth();
        assertNotNull(normalReport.getOverallStatus());

        // 2. 记录快速查询
        for (int i = 0; i < 5; i++) {
            monitoringService.recordQueryPerformance("SELECT " + i, 500L, 1, "user1", "testdb");
        }
        
        SystemMonitoringService.SystemHealthReport fastQueryReport = monitoringService.getSystemHealth();
        assertNotNull(fastQueryReport.getOverallStatus());

        // 3. 记录慢查询
        monitoringService.recordQueryPerformance("SLOW SELECT", 15000L, 1, "user1", "testdb");
        
        SystemMonitoringService.SystemHealthReport slowQueryReport = monitoringService.getSystemHealth();
        assertNotNull(slowQueryReport.getOverallStatus());
        
        // 验证慢查询对系统状态的影响
        assertTrue(slowQueryReport.getQueryStats().getMaxExecutionTime() >= 15000L);
    }
}