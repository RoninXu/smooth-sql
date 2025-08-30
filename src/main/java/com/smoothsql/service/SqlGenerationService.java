package com.smoothsql.service;

import com.smoothsql.entity.QueryHistory;
import com.smoothsql.mapper.QueryHistoryMapper;
import com.smoothsql.service.NaturalLanguageService.QueryIntent;
import com.smoothsql.service.NaturalLanguageService.QueryCondition;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL生成服务
 * 
 * 核心功能包括：
 * 1. 将自然语言查询转换为SQL语句
 * 2. 使用规则引擎生成基础SQL
 * 3. 通过DeepSeek AI模型优化SQL
 * 4. 生成SQL解释说明
 * 5. 保存查询历史记录
 * 6. 计算生成置信度
 * 
 * 采用混合架构：规则引擎 + AI优化，确保基础功能可用性的同时提升SQL质量
 * 
 * @author Smooth SQL Team
 * @version 1.0
 * @since 2024-08-30
 */
@Service
public class SqlGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SqlGenerationService.class);

    @Autowired
    private NaturalLanguageService naturalLanguageService;
    
    @Autowired
    private QueryHistoryMapper queryHistoryMapper;
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;

    /**
     * 生成SQL的主入口方法
     * 
     * 执行完整的SQL生成流程：
     * 1. 自然语言意图分析
     * 2. 规则引擎生成基础SQL
     * 3. AI模型优化SQL
     * 4. 生成解释说明
     * 5. 保存查询历史
     * 6. 计算置信度
     * 
     * @param naturalQuery 用户输入的自然语言查询
     * @param databaseName 目标数据库名称
     * @param userId 用户ID，用于历史记录
     * @return SQL生成结果，包含SQL语句、解释、置信度等信息
     */
    public SqlGenerationResult generateSql(String naturalQuery, String databaseName, String userId) {
        logger.info("开始SQL生成流程 - 自然语言: '{}', 数据库: '{}', 用户: '{}'", 
                   naturalQuery, databaseName, userId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 第一步：分析自然语言查询
            logger.debug("步骤1: 开始分析自然语言查询意图");
            long nlpStartTime = System.currentTimeMillis();
            
            QueryIntent intent = naturalLanguageService.analyzeQuery(naturalQuery, databaseName);
            
            long nlpTime = System.currentTimeMillis() - nlpStartTime;
            logger.debug("自然语言分析完成 - 耗时: {}ms, 意图类型: '{}', 识别表: {}, 识别字段: {}", 
                        nlpTime, intent.getIntentType(), intent.getTables(), intent.getColumns());
            
            // 第二步：使用规则引擎生成基础SQL
            logger.debug("步骤2: 开始使用规则引擎生成基础SQL");
            long ruleStartTime = System.currentTimeMillis();
            
            String baseSql = generateBaseSqlFromIntent(intent);
            
            long ruleTime = System.currentTimeMillis() - ruleStartTime;
            logger.debug("规则引擎生成完成 - 耗时: {}ms, 基础SQL: '{}'", ruleTime, baseSql);
            
            // 第三步：使用AI模型优化SQL
            logger.debug("步骤3: 开始使用DeepSeek AI优化SQL");
            long aiStartTime = System.currentTimeMillis();
            
            String optimizedSql = optimizeSqlWithAI(baseSql, naturalQuery, intent);
            
            long aiTime = System.currentTimeMillis() - aiStartTime;
            logger.debug("AI优化完成 - 耗时: {}ms, 最终SQL: '{}'", aiTime, optimizedSql);
            
            // 第四步：生成解释说明
            logger.debug("步骤4: 生成SQL解释说明");
            String explanation = generateExplanation(optimizedSql, intent);
            logger.debug("解释说明生成完成: '{}'", explanation);
            
            // 第五步：计算置信度
            Double confidence = calculateConfidence(intent);
            logger.debug("置信度计算完成: {}", confidence);
            
            // 第六步：保存查询历史
            logger.debug("步骤5: 保存查询历史记录");
            saveQueryHistory(naturalQuery, optimizedSql, databaseName, userId);
            
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("SQL生成流程全部完成 - 总耗时: {}ms (NLP: {}ms, 规则: {}ms, AI: {}ms), 最终SQL: '{}', 置信度: {}", 
                       totalTime, nlpTime, ruleTime, aiTime, optimizedSql, confidence);
            
            return new SqlGenerationResult(
                true, 
                optimizedSql, 
                explanation, 
                intent.getTables(),
                confidence
            );
            
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("SQL生成流程发生异常 - 耗时: {}ms, 查询: '{}', 异常: {}", 
                        totalTime, naturalQuery, e.getMessage(), e);
            
            return new SqlGenerationResult(
                false,
                null,
                "SQL生成失败: " + e.getMessage(),
                null,
                0.0
            );
        }
    }

    private String generateBaseSqlFromIntent(QueryIntent intent) {
        StringBuilder sql = new StringBuilder();
        
        // SELECT 子句
        sql.append("SELECT ");
        if (intent.getColumns().isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", intent.getColumns()));
        }
        
        // FROM 子句
        sql.append(" FROM ");
        if (intent.getTables().isEmpty()) {
            throw new RuntimeException("无法识别要查询的表");
        }
        sql.append(intent.getTables().get(0)); // 简单起见，只使用第一个表
        
        // WHERE 子句
        if (!intent.getConditions().isEmpty()) {
            sql.append(" WHERE ");
            List<String> conditions = intent.getConditions().stream()
                .map(this::buildConditionClause)
                .collect(Collectors.toList());
            sql.append(String.join(" AND ", conditions));
        }
        
        // GROUP BY 子句
        if (intent.getGroupBy() != null) {
            sql.append(" GROUP BY ").append(intent.getGroupBy());
        }
        
        // ORDER BY 子句
        if (intent.getOrderBy() != null) {
            sql.append(" ORDER BY id ").append(intent.getOrderBy());
        }
        
        return sql.toString();
    }

    private String buildConditionClause(QueryCondition condition) {
        if ("LIKE".equals(condition.getOperator())) {
            return condition.getField() + " LIKE '%" + condition.getValue() + "%'";
        } else if ("BETWEEN".equals(condition.getOperator())) {
            return condition.getField() + " BETWEEN " + condition.getValue();
        } else {
            return condition.getField() + " " + condition.getOperator() + " '" + condition.getValue() + "'";
        }
    }

    private String optimizeSqlWithAI(String baseSql, String naturalQuery, QueryIntent intent) {
        try {
            String prompt = String.format(
                "你是一个专业的SQL专家。请根据以下信息生成或优化SQL查询：\n\n" +
                "自然语言需求：%s\n" +
                "基础SQL：%s\n" +
                "涉及的数据表：%s\n\n" +
                "请遵循以下要求：\n" +
                "1. 只返回SQL语句，不要任何解释\n" +
                "2. 确保SQL语法正确\n" +
                "3. 优化查询性能\n" +
                "4. 使用标准MySQL语法\n" +
                "5. 如果原SQL已经正确，可以直接返回\n\n" +
                "SQL：",
                naturalQuery,
                baseSql,
                String.join(", ", intent.getTables())
            );
            
            String aiResponse = chatLanguageModel.generate(prompt);
            
            // 清理AI响应，提取纯SQL
            String cleanSql = cleanAiResponse(aiResponse);
            
            return cleanSql.isEmpty() ? baseSql : cleanSql;
            
        } catch (Exception e) {
            // AI服务不可用时，返回基础SQL
            System.err.println("AI服务调用失败: " + e.getMessage());
            return baseSql;
        }
    }
    
    private String cleanAiResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "";
        }
        
        // 移除代码块标记
        String cleaned = response.replaceAll("```sql|```", "").trim();
        
        // 移除多余的换行和空白
        cleaned = cleaned.replaceAll("\\n+", " ").replaceAll("\\s+", " ").trim();
        
        // 如果响应包含多个语句，只取第一个SELECT语句
        if (cleaned.toUpperCase().startsWith("SELECT")) {
            int semicolonIndex = cleaned.indexOf(';');
            if (semicolonIndex > 0) {
                cleaned = cleaned.substring(0, semicolonIndex);
            }
        }
        
        return cleaned;
    }

    private String generateExplanation(String sql, QueryIntent intent) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("该SQL语句用于");
        
        switch (intent.getIntentType()) {
            case "SELECT":
                explanation.append("查询");
                break;
            case "COUNT":
                explanation.append("统计数量");
                break;
            case "SUM":
                explanation.append("求和计算");
                break;
            case "AVG":
                explanation.append("计算平均值");
                break;
            default:
                explanation.append("查询");
        }
        
        explanation.append(String.join("、", intent.getTables())).append("表");
        
        if (!intent.getColumns().isEmpty()) {
            explanation.append("的").append(String.join("、", intent.getColumns())).append("字段");
        }
        
        if (!intent.getConditions().isEmpty()) {
            explanation.append("，筛选条件为：").append(intent.getConditions().get(0).getField())
                      .append(intent.getConditions().get(0).getOperator())
                      .append(intent.getConditions().get(0).getValue());
        }
        
        return explanation.toString();
    }

    /**
     * 保存查询历史记录
     * 
     * 将每次成功的查询记录保存到数据库中，用于：
     * 1. 用户查询历史回顾
     * 2. 系统使用情况统计
     * 3. SQL生成质量分析
     * 4. 用户行为分析
     * 
     * @param naturalQuery 原始自然语言查询
     * @param sql 生成的SQL语句
     * @param databaseName 数据库名称
     * @param userId 用户ID
     */
    private void saveQueryHistory(String naturalQuery, String sql, String databaseName, String userId) {
        try {
            logger.debug("开始保存查询历史 - 用户: '{}', 数据库: '{}'", userId, databaseName);
            
            QueryHistory history = new QueryHistory();
            history.setUserId(userId != null ? userId : "anonymous");
            history.setNaturalQuery(naturalQuery);
            history.setGeneratedSql(sql);
            history.setDatabaseName(databaseName);
            history.setStatus("SUCCESS");
            history.setCreatedAt(LocalDateTime.now());
            
            int inserted = queryHistoryMapper.insert(history);
            
            if (inserted > 0) {
                logger.debug("查询历史保存成功 - 记录ID: {}", history.getId());
            } else {
                logger.warn("查询历史保存失败 - 未插入任何记录");
            }
            
        } catch (Exception e) {
            logger.error("保存查询历史时发生异常 - 用户: '{}', 查询: '{}', 异常: {}", 
                        userId, naturalQuery, e.getMessage(), e);
            // 不影响主流程，仅记录错误
        }
    }

    private Double calculateConfidence(QueryIntent intent) {
        double confidence = 0.5; // 基础置信度
        
        // 根据识别的表数量调整置信度
        if (!intent.getTables().isEmpty()) {
            confidence += 0.2;
        }
        
        // 根据识别的字段数量调整置信度
        if (!intent.getColumns().isEmpty()) {
            confidence += 0.2;
        }
        
        // 根据识别的条件数量调整置信度
        if (!intent.getConditions().isEmpty()) {
            confidence += 0.1;
        }
        
        return Math.min(confidence, 1.0);
    }

    // SQL生成结果类
    public static class SqlGenerationResult {
        private boolean success;
        private String sql;
        private String explanation;
        private List<String> tables;
        private Double confidence;
        private String errorMessage;

        public SqlGenerationResult(boolean success, String sql, String explanation, List<String> tables, Double confidence) {
            this.success = success;
            this.sql = sql;
            this.explanation = explanation;
            this.tables = tables;
            this.confidence = confidence;
        }

        public SqlGenerationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getSql() { return sql; }
        public String getExplanation() { return explanation; }
        public List<String> getTables() { return tables; }
        public Double getConfidence() { return confidence; }
        public String getErrorMessage() { return errorMessage; }
    }
}