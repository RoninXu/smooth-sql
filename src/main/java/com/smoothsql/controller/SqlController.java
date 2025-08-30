package com.smoothsql.controller;

import com.smoothsql.dto.*;
import com.smoothsql.service.SqlGenerationService;
import com.smoothsql.service.QueryExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * SQL相关API控制器
 * 
 * 提供自然语言转SQL的核心功能，包括：
 * 1. 自然语言转SQL生成
 * 2. SQL语句执行
 * 3. 一键生成并执行查询
 * 
 * 所有接口都支持跨域访问，并包含完整的参数验证和异常处理
 * 
 * @author Smooth SQL Team
 * @version 1.0
 * @since 2024-08-30
 */
@RestController
@RequestMapping("/api/v1/sql")
@Validated
@CrossOrigin(origins = "*")
public class SqlController {

    private static final Logger logger = LoggerFactory.getLogger(SqlController.class);

    @Autowired
    private SqlGenerationService sqlGenerationService;
    
    @Autowired
    private QueryExecutionService queryExecutionService;

    /**
     * 自然语言转SQL生成接口
     * 
     * 将用户输入的自然语言查询转换为标准SQL语句
     * 支持中文查询，使用DeepSeek AI模型进行智能解析和优化
     * 
     * @param request 包含自然语言查询的请求对象
     * @return 包含生成的SQL语句、解释说明、置信度等信息的响应
     * 
     * 请求示例：
     * {
     *   "query": "查询用户表中昨天注册的所有用户",
     *   "database": "demo",
     *   "context": {"userId": "user123", "sessionId": "session456"}
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<SqlGenerateResponse> generateSql(@Valid @RequestBody SqlGenerateRequest request) {
        // 记录请求开始
        logger.info("开始处理SQL生成请求 - 查询内容: '{}', 数据库: '{}'", 
                   request.getQuery(), request.getDatabase());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 提取用户信息和数据库配置
            String userId = extractUserIdFromContext(request.getContext());
            String databaseName = request.getDatabase() != null ? request.getDatabase() : "demo";
            
            logger.debug("请求参数解析完成 - 用户ID: '{}', 目标数据库: '{}'", userId, databaseName);
            
            // 调用SQL生成服务
            SqlGenerationService.SqlGenerationResult result = sqlGenerationService.generateSql(
                request.getQuery(), 
                databaseName, 
                userId
            );
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (result.isSuccess()) {
                logger.info("SQL生成成功 - 耗时: {}ms, 生成SQL: '{}', 置信度: {}", 
                          processingTime, result.getSql(), result.getConfidence());
                
                SqlGenerateResponse.SqlData data = new SqlGenerateResponse.SqlData(
                    result.getSql(),
                    result.getExplanation(),
                    result.getTables(),
                    result.getConfidence()
                );
                
                return ResponseEntity.ok(new SqlGenerateResponse(true, data));
            } else {
                logger.warn("SQL生成失败 - 耗时: {}ms, 错误信息: '{}'", processingTime, result.getErrorMessage());
                
                return ResponseEntity.badRequest()
                    .body(new SqlGenerateResponse(false, result.getErrorMessage()));
            }
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("SQL生成过程发生异常 - 耗时: {}ms, 查询: '{}', 异常信息: {}", 
                        processingTime, request.getQuery(), e.getMessage(), e);
            
            return ResponseEntity.internalServerError()
                .body(new SqlGenerateResponse(false, "服务内部错误: " + e.getMessage()));
        }
    }

    /**
     * SQL语句执行接口
     * 
     * 执行用户提供的SQL语句，并返回查询结果
     * 包含多层安全验证，仅支持SELECT查询，防止SQL注入
     * 
     * @param request 包含SQL语句和执行参数的请求对象
     * @return 包含查询结果的响应，包括列信息、数据行、执行时间等
     * 
     * 请求示例：
     * {
     *   "sql": "SELECT * FROM users WHERE created_at > '2024-08-29'",
     *   "database": "demo",
     *   "limit": 50
     * }
     */
    @PostMapping("/execute")
    public ResponseEntity<SqlExecuteResponse> executeSql(@Valid @RequestBody SqlExecuteRequest request) {
        // 记录SQL执行请求
        logger.info("开始执行SQL查询 - SQL: '{}', 限制条数: {}", 
                   request.getSql(), request.getLimit());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 调用查询执行服务
            QueryExecutionService.QueryExecutionResult result = queryExecutionService.executeSql(
                request.getSql(), 
                request.getLimit()
            );
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (result.isSuccess()) {
                logger.info("SQL执行成功 - 耗时: {}ms, 返回数据条数: {}, 执行时间: {}ms", 
                          processingTime, 
                          result.getData() != null ? result.getData().getTotalCount() : 0,
                          result.getData() != null ? result.getData().getExecutionTime() : 0);
                
                SqlExecuteResponse response = new SqlExecuteResponse(
                    true,
                    "SQL执行成功",
                    result.getData()
                );
                return ResponseEntity.ok(response);
            } else {
                logger.warn("SQL执行失败 - 耗时: {}ms, 失败原因: '{}'", processingTime, result.getMessage());
                
                return ResponseEntity.badRequest()
                    .body(new SqlExecuteResponse(false, result.getMessage(), null));
            }
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("SQL执行过程发生异常 - 耗时: {}ms, SQL: '{}', 异常信息: {}", 
                        processingTime, request.getSql(), e.getMessage(), e);
            
            return ResponseEntity.internalServerError()
                .body(new SqlExecuteResponse(false, "SQL执行失败: " + e.getMessage(), null));
        }
    }

    /**
     * 一键生成并执行SQL接口
     * 
     * 这是最常用的接口，将自然语言查询转换为SQL并直接执行，返回查询结果
     * 相当于 generateSql + executeSql 的组合操作，提供更便捷的使用体验
     * 
     * @param request 包含自然语言查询的请求对象
     * @return 包含SQL生成信息和执行结果的完整响应
     * 
     * 请求示例：
     * {
     *   "query": "统计每个分类的产品数量，按数量降序排列",
     *   "database": "demo",
     *   "context": {"userId": "user123"}
     * }
     * 
     * 响应包含：
     * - sqlData: SQL生成的详细信息（SQL语句、解释、置信度等）
     * - executeData: SQL执行的结果数据（列信息、数据行、执行时间等）
     */
    @PostMapping("/generate-and-execute")
    public ResponseEntity<GenerateAndExecuteResponse> generateAndExecute(@Valid @RequestBody SqlGenerateRequest request) {
        // 记录一键查询请求开始
        logger.info("开始处理一键查询请求 - 自然语言查询: '{}', 数据库: '{}'", 
                   request.getQuery(), request.getDatabase());
        
        long totalStartTime = System.currentTimeMillis();
        
        try {
            // 提取用户信息和数据库配置
            String userId = extractUserIdFromContext(request.getContext());
            String databaseName = request.getDatabase() != null ? request.getDatabase() : "demo";
            
            logger.debug("一键查询参数解析完成 - 用户ID: '{}', 目标数据库: '{}'", userId, databaseName);
            
            // 第一步：生成SQL
            logger.debug("步骤1: 开始生成SQL语句");
            long generateStartTime = System.currentTimeMillis();
            
            SqlGenerationService.SqlGenerationResult generateResult = sqlGenerationService.generateSql(
                request.getQuery(), 
                databaseName, 
                userId
            );
            
            long generateTime = System.currentTimeMillis() - generateStartTime;
            
            if (!generateResult.isSuccess()) {
                logger.warn("SQL生成失败，终止执行 - 生成耗时: {}ms, 错误: '{}'", 
                          generateTime, generateResult.getErrorMessage());
                
                return ResponseEntity.badRequest()
                    .body(new GenerateAndExecuteResponse(false, generateResult.getErrorMessage(), null, null));
            }
            
            logger.info("SQL生成成功 - 生成耗时: {}ms, SQL: '{}', 置信度: {}", 
                       generateTime, generateResult.getSql(), generateResult.getConfidence());
            
            // 第二步：执行SQL
            logger.debug("步骤2: 开始执行生成的SQL语句");
            long executeStartTime = System.currentTimeMillis();
            
            QueryExecutionService.QueryExecutionResult executeResult = queryExecutionService.executeSql(
                generateResult.getSql(), 
                100  // 默认限制100条记录
            );
            
            long executeTime = System.currentTimeMillis() - executeStartTime;
            long totalTime = System.currentTimeMillis() - totalStartTime;
            
            // 构建完整响应
            GenerateAndExecuteResponse response = new GenerateAndExecuteResponse(
                executeResult.isSuccess(),
                executeResult.isSuccess() ? "查询成功完成" : executeResult.getMessage(),
                new SqlGenerateResponse.SqlData(
                    generateResult.getSql(),
                    generateResult.getExplanation(),
                    generateResult.getTables(),
                    generateResult.getConfidence()
                ),
                executeResult.getData()
            );
            
            if (executeResult.isSuccess()) {
                logger.info("一键查询全部完成 - 总耗时: {}ms (生成: {}ms, 执行: {}ms), 返回记录数: {}", 
                          totalTime, generateTime, executeTime,
                          executeResult.getData() != null ? executeResult.getData().getTotalCount() : 0);
            } else {
                logger.warn("SQL执行失败 - 总耗时: {}ms (生成: {}ms, 执行: {}ms), 失败原因: '{}'", 
                          totalTime, generateTime, executeTime, executeResult.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - totalStartTime;
            logger.error("一键查询过程发生异常 - 总耗时: {}ms, 查询: '{}', 异常信息: {}", 
                        totalTime, request.getQuery(), e.getMessage(), e);
            
            return ResponseEntity.internalServerError()
                .body(new GenerateAndExecuteResponse(false, "服务内部错误: " + e.getMessage(), null, null));
        }
    }

    /**
     * 从请求上下文中提取用户ID
     * 
     * 用于用户行为跟踪和查询历史记录
     * 如果上下文中没有userId，则返回"anonymous"作为默认值
     * 
     * @param context 请求上下文Map，可能包含userId、sessionId等信息
     * @return 用户ID字符串，如果未提供则返回"anonymous"
     */
    private String extractUserIdFromContext(java.util.Map<String, Object> context) {
        if (context != null && context.containsKey("userId")) {
            String userId = context.get("userId").toString();
            logger.debug("从请求上下文中提取到用户ID: '{}'", userId);
            return userId;
        }
        logger.debug("请求上下文中未包含用户ID，使用默认值: 'anonymous'");
        return "anonymous";
    }
}