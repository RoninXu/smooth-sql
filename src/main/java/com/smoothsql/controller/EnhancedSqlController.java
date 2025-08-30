package com.smoothsql.controller;

import com.smoothsql.dto.*;
import com.smoothsql.service.*;
import com.smoothsql.service.UserPermissionService.QueryLimitResult;
import com.smoothsql.service.QueryHistoryService.QueryStatistics;
import com.smoothsql.service.QueryHistoryService.QueryRecommendation;
import com.smoothsql.service.ResultVisualizationService.VisualizationAnalysis;
import com.smoothsql.service.ResultVisualizationService.ChartData;
import com.smoothsql.service.PerformanceOptimizationService.PerformanceReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 增强版SQL控制器 - 第二阶段功能
 * 
 * 提供以下增强功能：
 * 1. 复杂查询支持（JOIN、聚合函数）
 * 2. 查询结果可视化和导出
 * 3. 查询历史管理和推荐
 * 4. 用户权限验证
 * 5. 性能监控和优化
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
@RestController
@RequestMapping("/api/v2/sql")
@CrossOrigin(origins = "*")
public class EnhancedSqlController {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedSqlController.class);

    @Autowired
    private SqlGenerationService sqlGenerationService;

    @Autowired
    private QueryExecutionService queryExecutionService;

    @Autowired
    private ResultVisualizationService visualizationService;

    @Autowired
    private QueryHistoryService historyService;

    @Autowired
    private UserPermissionService permissionService;

    @Autowired
    private PerformanceOptimizationService performanceService;

    /**
     * 增强版查询生成和执行接口
     * 整合权限验证、性能监控、结果可视化
     */
    @PostMapping("/enhanced-query")
    public ResponseEntity<EnhancedQueryResponse> enhancedQuery(@Valid @RequestBody EnhancedQueryRequest request) {
        logger.info("增强版查询请求 - 用户: {}, 查询: '{}'", request.getUserId(), request.getQuery());

        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 权限验证
            if (!permissionService.hasAccessToDatabase(request.getUserId(), request.getDatabase())) {
                return ResponseEntity.status(403).body(new EnhancedQueryResponse(
                    false, "无权限访问该数据库", null, null, null, null
                ));
            }

            // 2. 查询频率限制检查
            QueryLimitResult limitResult = permissionService.checkQueryLimit(request.getUserId());
            if (!limitResult.isAllowed()) {
                return ResponseEntity.status(429).body(new EnhancedQueryResponse(
                    false, limitResult.getMessage(), null, null, null, null
                ));
            }

            // 3. 生成SQL
            SqlGenerationService.SqlGenerationResult generationResult = 
                sqlGenerationService.generateSql(request.getQuery(), request.getDatabase(), request.getUserId());

            if (!generationResult.isSuccess()) {
                return ResponseEntity.ok(new EnhancedQueryResponse(
                    false, generationResult.getErrorMessage(), null, null, null, null
                ));
            }

            // 4. 优化SQL
            String optimizedSql = performanceService.optimizeSql(generationResult.getSql(), request.getDatabase());

            // 5. 执行查询
            SqlExecuteResponse executeResult = queryExecutionService.executeQuery(
                optimizedSql, request.getDatabase(), request.getLimit()
            );

            // 6. 记录性能统计
            long executionTime = System.currentTimeMillis() - startTime;
            performanceService.recordQueryPerformance(
                optimizedSql, 
                executionTime,
                executeResult.isSuccess() ? executeResult.getData().getRows().size() : 0
            );

            // 7. 生成可视化分析（如果查询成功）
            VisualizationAnalysis visualization = null;
            if (executeResult.isSuccess()) {
                visualization = visualizationService.analyzeForVisualization(executeResult);
            }

            // 8. 保存历史记录
            historyService.saveQueryHistory(
                request.getQuery(),
                optimizedSql,
                request.getDatabase(),
                request.getUserId(),
                executionTime,
                executeResult.isSuccess() ? executeResult.getData().getRows().size() : null,
                executeResult.isSuccess() ? "SUCCESS" : "FAILED"
            );

            // 9. 异步预取相关数据
            performanceService.prefetchHotData(request.getDatabase(), request.getUserId());

            EnhancedQueryResponse response = new EnhancedQueryResponse(
                executeResult.isSuccess(),
                executeResult.getMessage(),
                executeResult.getData(),
                generationResult.getExplanation(),
                visualization,
                Map.of(
                    "executionTime", executionTime,
                    "confidence", generationResult.getConfidence(),
                    "queriesRemaining", limitResult.getMaxAllowed() - limitResult.getCurrentCount()
                )
            );

            logger.info("增强版查询完成 - 用户: {}, 成功: {}, 耗时: {}ms", 
                       request.getUserId(), response.isSuccess(), executionTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("增强版查询异常 - 用户: {}, 异常: {}", request.getUserId(), e.getMessage(), e);
            
            return ResponseEntity.status(500).body(new EnhancedQueryResponse(
                false, "系统错误: " + e.getMessage(), null, null, null, null
            ));
        }
    }

    /**
     * 获取查询结果的图表数据
     */
    @PostMapping("/chart-data")
    public ResponseEntity<ChartData> getChartData(@RequestBody ChartDataRequest request) {
        logger.debug("获取图表数据 - 类型: {}, 用户: {}", request.getChartType(), request.getUserId());

        try {
            // 权限验证
            if (!permissionService.hasPermission(request.getUserId(), "QUERY_DATA")) {
                return ResponseEntity.status(403).build();
            }

            // 重新执行查询获取最新数据
            SqlExecuteResponse queryResult = queryExecutionService.executeQuery(
                request.getSql(), request.getDatabase(), null
            );

            if (!queryResult.isSuccess()) {
                return ResponseEntity.status(400).build();
            }

            ChartData chartData = visualizationService.prepareChartData(
                queryResult, request.getChartType(), request.getGroupByColumn(), request.getValueColumn()
            );

            return ResponseEntity.ok(chartData);

        } catch (Exception e) {
            logger.error("获取图表数据异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 导出查询结果为Excel
     */
    @PostMapping("/export/excel")
    public ResponseEntity<byte[]> exportToExcel(@RequestBody ExportRequest request) {
        logger.info("导出Excel - 用户: {}, SQL: {}", request.getUserId(), request.getSql().substring(0, Math.min(50, request.getSql().length())));

        try {
            // 权限验证
            if (!permissionService.hasPermission(request.getUserId(), "EXPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }

            // 重新执行查询
            SqlExecuteResponse queryResult = queryExecutionService.executeQuery(
                request.getSql(), request.getDatabase(), null
            );

            if (!queryResult.isSuccess()) {
                return ResponseEntity.status(400).build();
            }

            // 生成Excel文件
            byte[] excelData = visualizationService.exportToExcel(queryResult, request.getSheetName());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "query_result.xlsx");

            return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);

        } catch (Exception e) {
            logger.error("导出Excel异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 导出查询结果为CSV
     */
    @PostMapping("/export/csv")
    public ResponseEntity<String> exportToCSV(@RequestBody ExportRequest request) {
        logger.info("导出CSV - 用户: {}", request.getUserId());

        try {
            // 权限验证
            if (!permissionService.hasPermission(request.getUserId(), "EXPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }

            // 重新执行查询
            SqlExecuteResponse queryResult = queryExecutionService.executeQuery(
                request.getSql(), request.getDatabase(), null
            );

            if (!queryResult.isSuccess()) {
                return ResponseEntity.status(400).build();
            }

            // 生成CSV内容
            String csvData = visualizationService.exportToCSV(queryResult);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", "query_result.csv");

            return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);

        } catch (Exception e) {
            logger.error("导出CSV异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取用户查询历史统计
     */
    @GetMapping("/statistics/{userId}")
    public ResponseEntity<QueryStatistics> getQueryStatistics(
            @PathVariable String userId,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        
        try {
            // 权限验证（用户只能查看自己的统计，管理员可以查看所有）
            if (!userId.equals(userId) && !permissionService.hasPermission(userId, "MANAGE_USERS")) {
                return ResponseEntity.status(403).build();
            }

            QueryStatistics statistics = historyService.getQueryStatistics(userId, startDate, endDate);
            return ResponseEntity.ok(statistics);

        } catch (Exception e) {
            logger.error("获取查询统计异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取智能查询推荐
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<QueryRecommendation>> getQueryRecommendations(
            @RequestParam String userId,
            @RequestParam String database,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            if (!permissionService.hasPermission(userId, "QUERY_DATA")) {
                return ResponseEntity.status(403).build();
            }

            List<QueryRecommendation> recommendations = 
                historyService.getQueryRecommendations(userId, database, limit);
            
            return ResponseEntity.ok(recommendations);

        } catch (Exception e) {
            logger.error("获取查询推荐异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取系统性能报告
     */
    @GetMapping("/performance-report")
    public ResponseEntity<PerformanceReport> getPerformanceReport(@RequestParam String userId) {
        try {
            // 只有管理员可以查看性能报告
            if (!permissionService.hasPermission(userId, "MANAGE_USERS")) {
                return ResponseEntity.status(403).build();
            }

            PerformanceReport report = performanceService.getPerformanceReport();
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            logger.error("获取性能报告异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    // 请求和响应DTO类
    public static class EnhancedQueryRequest {
        private String query;
        private String database;
        private String userId;
        private Integer limit;

        // Getters and Setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    public static class EnhancedQueryResponse {
        private boolean success;
        private String message;
        private SqlExecuteResponse.QueryResultData data;
        private String explanation;
        private VisualizationAnalysis visualization;
        private Map<String, Object> metadata;

        public EnhancedQueryResponse(boolean success, String message, 
                                   SqlExecuteResponse.QueryResultData data, String explanation,
                                   VisualizationAnalysis visualization, Map<String, Object> metadata) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.explanation = explanation;
            this.visualization = visualization;
            this.metadata = metadata;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public SqlExecuteResponse.QueryResultData getData() { return data; }
        public String getExplanation() { return explanation; }
        public VisualizationAnalysis getVisualization() { return visualization; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    public static class ChartDataRequest {
        private String sql;
        private String database;
        private String userId;
        private String chartType;
        private String groupByColumn;
        private String valueColumn;

        // Getters and Setters
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getChartType() { return chartType; }
        public void setChartType(String chartType) { this.chartType = chartType; }
        public String getGroupByColumn() { return groupByColumn; }
        public void setGroupByColumn(String groupByColumn) { this.groupByColumn = groupByColumn; }
        public String getValueColumn() { return valueColumn; }
        public void setValueColumn(String valueColumn) { this.valueColumn = valueColumn; }
    }

    public static class ExportRequest {
        private String sql;
        private String database;
        private String userId;
        private String sheetName;

        // Getters and Setters
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getSheetName() { return sheetName; }
        public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    }
}