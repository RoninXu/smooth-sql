package com.smoothsql.service;

import com.smoothsql.entity.DatabaseSchema;
import com.smoothsql.mapper.DatabaseSchemaMapper;
import com.smoothsql.mapper.QueryHistoryMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能SQL自动补全服务
 * 
 * 功能包括：
 * 1. 基于上下文的SQL关键词补全
 * 2. 表名和字段名智能提示
 * 3. 基于历史查询的模式推荐
 * 4. 语法错误实时检测
 * 5. AI驱动的查询优化建议
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@Service
public class IntelligentAutoCompleteService {

    private static final Logger logger = LoggerFactory.getLogger(IntelligentAutoCompleteService.class);

    @Autowired
    private DatabaseSchemaMapper schemaMapper;
    
    @Autowired
    private QueryHistoryMapper queryHistoryMapper;
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;

    // SQL关键词库
    private static final List<String> SQL_KEYWORDS = Arrays.asList(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN",
        "GROUP BY", "ORDER BY", "HAVING", "DISTINCT", "UNION", "INSERT", "UPDATE", 
        "DELETE", "CREATE", "ALTER", "DROP", "INDEX", "VIEW", "PROCEDURE",
        "AND", "OR", "NOT", "IN", "EXISTS", "LIKE", "BETWEEN", "IS NULL", "IS NOT NULL",
        "COUNT", "SUM", "AVG", "MAX", "MIN", "COALESCE", "CASE", "WHEN", "THEN", "ELSE", "END"
    );

    /**
     * 获取智能补全建议
     * 
     * @param partialQuery 部分SQL查询
     * @param cursorPosition 光标位置
     * @param databaseName 数据库名
     * @param userId 用户ID
     * @return 补全建议列表
     */
    public AutoCompleteResult getAutoCompleteSuggestions(String partialQuery, int cursorPosition, 
                                                       String databaseName, String userId) {
        logger.debug("获取智能补全建议 - 用户: {}, 数据库: {}, 光标位置: {}", 
                    userId, databaseName, cursorPosition);

        try {
            long startTime = System.currentTimeMillis();
            
            // 分析当前上下文
            SqlContext context = analyzeContext(partialQuery, cursorPosition);
            
            // 生成不同类型的建议
            List<AutoCompleteSuggestion> suggestions = new ArrayList<>();
            
            // 1. SQL关键词建议
            suggestions.addAll(getKeywordSuggestions(context));
            
            // 2. 表名建议
            suggestions.addAll(getTableSuggestions(context, databaseName));
            
            // 3. 字段名建议
            suggestions.addAll(getColumnSuggestions(context, databaseName));
            
            // 4. 函数建议
            suggestions.addAll(getFunctionSuggestions(context));
            
            // 5. 基于历史的模式建议
            suggestions.addAll(getHistoricalPatternSuggestions(context, userId, databaseName));
            
            // 6. AI驱动的智能建议
            suggestions.addAll(getAiEnhancedSuggestions(context, databaseName));
            
            // 排序和过滤建议
            suggestions = rankAndFilterSuggestions(suggestions, context);
            
            long processingTime = System.currentTimeMillis() - startTime;
            logger.debug("智能补全完成 - 建议数量: {}, 耗时: {}ms", suggestions.size(), processingTime);
            
            return new AutoCompleteResult(suggestions, context, processingTime);
            
        } catch (Exception e) {
            logger.error("智能补全异常: {}", e.getMessage(), e);
            return new AutoCompleteResult(new ArrayList<>(), null, 0);
        }
    }

    /**
     * 检测SQL语法错误
     * 
     * @param sql SQL语句
     * @param databaseName 数据库名
     * @return 错误检测结果
     */
    public SqlErrorDetectionResult detectSqlErrors(String sql, String databaseName) {
        logger.debug("检测SQL语法错误 - 数据库: {}", databaseName);

        try {
            List<SqlError> errors = new ArrayList<>();
            List<SqlSuggestion> suggestions = new ArrayList<>();
            
            // 基础语法检查
            errors.addAll(performBasicSyntaxCheck(sql));
            
            // 表和字段存在性检查
            errors.addAll(performSchemaValidation(sql, databaseName));
            
            // AI增强的错误检测
            errors.addAll(performAiErrorDetection(sql));
            
            // 生成修复建议
            for (SqlError error : errors) {
                suggestions.addAll(generateFixSuggestions(error, sql, databaseName));
            }
            
            return new SqlErrorDetectionResult(errors, suggestions, errors.isEmpty());
            
        } catch (Exception e) {
            logger.error("SQL错误检测异常: {}", e.getMessage(), e);
            return new SqlErrorDetectionResult(new ArrayList<>(), new ArrayList<>(), true);
        }
    }

    /**
     * 获取查询优化建议
     * 
     * @param sql SQL语句
     * @param databaseName 数据库名
     * @return 优化建议
     */
    public QueryOptimizationResult getOptimizationSuggestions(String sql, String databaseName) {
        logger.debug("获取查询优化建议 - 数据库: {}", databaseName);

        try {
            List<OptimizationSuggestion> suggestions = new ArrayList<>();
            
            // 索引建议
            suggestions.addAll(getIndexSuggestions(sql, databaseName));
            
            // 查询重写建议
            suggestions.addAll(getQueryRewriteSuggestions(sql));
            
            // 性能提升建议
            suggestions.addAll(getPerformanceImprovementSuggestions(sql, databaseName));
            
            // AI驱动的高级优化
            suggestions.addAll(getAiOptimizationSuggestions(sql, databaseName));
            
            return new QueryOptimizationResult(suggestions, estimatePerformanceImpact(suggestions));
            
        } catch (Exception e) {
            logger.error("查询优化建议异常: {}", e.getMessage(), e);
            return new QueryOptimizationResult(new ArrayList<>(), 0.0);
        }
    }

    private SqlContext analyzeContext(String partialQuery, int cursorPosition) {
        SqlContext context = new SqlContext();
        context.setPartialQuery(partialQuery);
        context.setCursorPosition(cursorPosition);
        
        String queryUpper = partialQuery.toUpperCase();
        
        // 确定当前SQL子句
        if (queryUpper.contains("SELECT") && !queryUpper.contains("FROM")) {
            context.setCurrentClause("SELECT");
        } else if (queryUpper.contains("FROM") && !queryUpper.contains("WHERE")) {
            context.setCurrentClause("FROM");
        } else if (queryUpper.contains("WHERE")) {
            context.setCurrentClause("WHERE");
        } else if (queryUpper.contains("GROUP BY")) {
            context.setCurrentClause("GROUP BY");
        } else if (queryUpper.contains("ORDER BY")) {
            context.setCurrentClause("ORDER BY");
        } else {
            context.setCurrentClause("UNKNOWN");
        }
        
        // 提取当前输入的词
        String[] words = partialQuery.substring(0, cursorPosition).split("\\s+");
        if (words.length > 0) {
            context.setCurrentWord(words[words.length - 1]);
        }
        
        // 提取已提及的表名
        context.setReferencedTables(extractReferencedTables(partialQuery));
        
        return context;
    }

    private List<AutoCompleteSuggestion> getKeywordSuggestions(SqlContext context) {
        return SQL_KEYWORDS.stream()
            .filter(keyword -> keyword.toLowerCase().startsWith(context.getCurrentWord().toLowerCase()))
            .map(keyword -> new AutoCompleteSuggestion(
                keyword, 
                "KEYWORD", 
                "SQL关键词", 
                calculateKeywordScore(keyword, context),
                keyword
            ))
            .collect(Collectors.toList());
    }

    @Cacheable("tableSuggestions")
    private List<AutoCompleteSuggestion> getTableSuggestions(SqlContext context, String databaseName) {
        try {
            List<DatabaseSchema> schemas = schemaMapper.selectByDatabaseName(databaseName);
            return schemas.stream()
                .filter(schema -> schema.getTableName().toLowerCase()
                    .startsWith(context.getCurrentWord().toLowerCase()))
                .map(schema -> new AutoCompleteSuggestion(
                    schema.getTableName(),
                    "TABLE",
                    "数据表: " + (schema.getTableComment() != null ? schema.getTableComment() : ""),
                    calculateTableScore(schema.getTableName(), context),
                    schema.getTableName()
                ))
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("获取表建议异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Cacheable("columnSuggestions")
    private List<AutoCompleteSuggestion> getColumnSuggestions(SqlContext context, String databaseName) {
        try {
            List<AutoCompleteSuggestion> suggestions = new ArrayList<>();
            
            for (String tableName : context.getReferencedTables()) {
                List<DatabaseSchema> columns = schemaMapper.selectColumnsByTableName(databaseName, tableName);
                suggestions.addAll(columns.stream()
                    .filter(column -> column.getColumnName().toLowerCase()
                        .startsWith(context.getCurrentWord().toLowerCase()))
                    .map(column -> new AutoCompleteSuggestion(
                        column.getColumnName(),
                        "COLUMN",
                        "字段 (" + (column.getDataType() != null ? column.getDataType() : column.getColumnType()) + "): " + 
                        (column.getColumnComment() != null ? column.getColumnComment() : ""),
                        calculateColumnScore(column.getColumnName(), context),
                        tableName + "." + column.getColumnName()
                    ))
                    .collect(Collectors.toList()));
            }
            
            return suggestions;
        } catch (Exception e) {
            logger.warn("获取字段建议异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<AutoCompleteSuggestion> getFunctionSuggestions(SqlContext context) {
        List<String> functions = Arrays.asList(
            "COUNT", "SUM", "AVG", "MAX", "MIN", "COALESCE", "CONCAT", 
            "SUBSTRING", "LENGTH", "UPPER", "LOWER", "TRIM", "NOW", "DATE_FORMAT"
        );
        
        return functions.stream()
            .filter(func -> func.toLowerCase().startsWith(context.getCurrentWord().toLowerCase()))
            .map(func -> new AutoCompleteSuggestion(
                func + "()",
                "FUNCTION",
                "SQL函数",
                calculateFunctionScore(func, context),
                func + "()"
            ))
            .collect(Collectors.toList());
    }

    private List<AutoCompleteSuggestion> getHistoricalPatternSuggestions(SqlContext context, String userId, String databaseName) {
        try {
            // 获取用户历史查询模式
            List<String> historicalQueries = queryHistoryMapper.selectRecentQueriesByUser(userId, databaseName, 50);
            
            return historicalQueries.stream()
                .filter(query -> isPatternMatch(query, context))
                .limit(5)
                .map(query -> new AutoCompleteSuggestion(
                    extractPatternSuggestion(query, context),
                    "PATTERN",
                    "历史查询模式",
                    0.7,
                    extractPatternSuggestion(query, context)
                ))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("获取历史模式建议异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<AutoCompleteSuggestion> getAiEnhancedSuggestions(SqlContext context, String databaseName) {
        try {
            String prompt = String.format(
                "基于以下SQL上下文，提供3个最佳的补全建议：\n" +
                "当前查询: %s\n" +
                "当前子句: %s\n" +
                "数据库: %s\n\n" +
                "请只返回建议的SQL片段，每行一个，不要解释。",
                context.getPartialQuery(),
                context.getCurrentClause(),
                databaseName
            );
            
            String aiResponse = chatLanguageModel.generate(prompt);
            
            return Arrays.stream(aiResponse.split("\n"))
                .filter(suggestion -> !suggestion.trim().isEmpty())
                .limit(3)
                .map(suggestion -> new AutoCompleteSuggestion(
                    suggestion.trim(),
                    "AI",
                    "AI智能建议",
                    0.8,
                    suggestion.trim()
                ))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("AI增强建议异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SqlError> performBasicSyntaxCheck(String sql) {
        List<SqlError> errors = new ArrayList<>();
        
        // 检查括号匹配
        if (!isParenthesesBalanced(sql)) {
            errors.add(new SqlError("SYNTAX_ERROR", "括号不匹配", 0, "检查SQL中的括号是否正确配对"));
        }
        
        // 检查引号匹配
        if (!isQuotesBalanced(sql)) {
            errors.add(new SqlError("SYNTAX_ERROR", "引号不匹配", 0, "检查SQL中的引号是否正确配对"));
        }
        
        return errors;
    }

    private List<SqlError> performSchemaValidation(String sql, String databaseName) {
        List<SqlError> errors = new ArrayList<>();
        
        try {
            // 提取SQL中引用的表名和字段名，验证是否存在
            List<String> referencedTables = extractReferencedTables(sql);
            List<DatabaseSchema> availableSchemas = schemaMapper.selectByDatabaseName(databaseName);
            
            Set<String> validTables = availableSchemas.stream()
                .map(DatabaseSchema::getTableName)
                .collect(Collectors.toSet());
            
            for (String table : referencedTables) {
                if (!validTables.contains(table)) {
                    errors.add(new SqlError("SCHEMA_ERROR", "表不存在: " + table, 0, "检查表名是否正确"));
                }
            }
            
        } catch (Exception e) {
            logger.warn("模式验证异常: {}", e.getMessage());
        }
        
        return errors;
    }

    private List<SqlError> performAiErrorDetection(String sql) {
        try {
            String prompt = String.format(
                "分析以下SQL语句中的潜在错误：\n%s\n\n" +
                "如果发现错误，请按以下格式返回（每行一个错误）：\n" +
                "ERROR_TYPE|错误描述|建议修复方法\n" +
                "如果没有错误，返回：NO_ERRORS",
                sql
            );
            
            String aiResponse = chatLanguageModel.generate(prompt);
            
            if ("NO_ERRORS".equals(aiResponse.trim())) {
                return new ArrayList<>();
            }
            
            return Arrays.stream(aiResponse.split("\n"))
                .filter(line -> line.contains("|"))
                .map(line -> {
                    String[] parts = line.split("\\|");
                    return new SqlError(parts[0], parts[1], 0, parts.length > 2 ? parts[2] : "");
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("AI错误检测异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // 辅助方法
    private double calculateKeywordScore(String keyword, SqlContext context) {
        return context.getCurrentClause().equals("UNKNOWN") ? 0.9 : 0.7;
    }

    private double calculateTableScore(String tableName, SqlContext context) {
        return "FROM".equals(context.getCurrentClause()) ? 0.9 : 0.6;
    }

    private double calculateColumnScore(String columnName, SqlContext context) {
        return "SELECT".equals(context.getCurrentClause()) ? 0.9 : 0.7;
    }

    private double calculateFunctionScore(String functionName, SqlContext context) {
        return "SELECT".equals(context.getCurrentClause()) ? 0.8 : 0.5;
    }

    private List<String> extractReferencedTables(String sql) {
        List<String> tables = new ArrayList<>();
        String[] words = sql.split("\\s+");
        
        for (int i = 0; i < words.length - 1; i++) {
            if ("FROM".equalsIgnoreCase(words[i]) || "JOIN".equalsIgnoreCase(words[i])) {
                if (i + 1 < words.length) {
                    String tableName = words[i + 1].replaceAll("[^a-zA-Z0-9_]", "");
                    if (!tableName.isEmpty()) {
                        tables.add(tableName);
                    }
                }
            }
        }
        
        return tables;
    }

    private boolean isParenthesesBalanced(String sql) {
        int count = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') count++;
            else if (c == ')') count--;
            if (count < 0) return false;
        }
        return count == 0;
    }

    private boolean isQuotesBalanced(String sql) {
        int singleQuotes = 0;
        int doubleQuotes = 0;
        
        for (char c : sql.toCharArray()) {
            if (c == '\'') singleQuotes++;
            else if (c == '"') doubleQuotes++;
        }
        
        return singleQuotes % 2 == 0 && doubleQuotes % 2 == 0;
    }

    private boolean isPatternMatch(String historicalQuery, SqlContext context) {
        return historicalQuery.toUpperCase().contains(context.getCurrentClause().toUpperCase());
    }

    private String extractPatternSuggestion(String query, SqlContext context) {
        // 简化实现：返回查询的相关部分
        return query.substring(0, Math.min(50, query.length()));
    }

    private List<AutoCompleteSuggestion> rankAndFilterSuggestions(List<AutoCompleteSuggestion> suggestions, 
                                                                 SqlContext context) {
        return suggestions.stream()
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(20)
            .collect(Collectors.toList());
    }

    private List<SqlSuggestion> generateFixSuggestions(SqlError error, String sql, String databaseName) {
        List<SqlSuggestion> suggestions = new ArrayList<>();
        
        switch (error.getType()) {
            case "SYNTAX_ERROR":
                suggestions.add(new SqlSuggestion("修复语法错误", error.getDescription(), sql));
                break;
            case "SCHEMA_ERROR":
                suggestions.add(new SqlSuggestion("修复表名错误", "检查并更正表名", sql));
                break;
        }
        
        return suggestions;
    }

    private List<OptimizationSuggestion> getIndexSuggestions(String sql, String databaseName) {
        // 分析SQL中的WHERE和JOIN条件，建议创建索引
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        // 简化实现
        return suggestions;
    }

    private List<OptimizationSuggestion> getQueryRewriteSuggestions(String sql) {
        // 分析查询结构，提供重写建议
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        // 简化实现
        return suggestions;
    }

    private List<OptimizationSuggestion> getPerformanceImprovementSuggestions(String sql, String databaseName) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        // 简化实现
        return suggestions;
    }

    private List<OptimizationSuggestion> getAiOptimizationSuggestions(String sql, String databaseName) {
        try {
            String prompt = String.format(
                "分析以下SQL查询并提供优化建议：\n%s\n\n" +
                "请提供具体的优化建议，格式：建议类型|描述|预期改进效果",
                sql
            );
            
            String aiResponse = chatLanguageModel.generate(prompt);
            
            return Arrays.stream(aiResponse.split("\n"))
                .filter(line -> line.contains("|"))
                .map(line -> {
                    String[] parts = line.split("\\|");
                    return new OptimizationSuggestion(
                        parts[0], 
                        parts[1], 
                        parts.length > 2 ? Double.parseDouble(parts[2].replaceAll("[^0-9.]", "")) : 10.0
                    );
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("AI优化建议异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private double estimatePerformanceImpact(List<OptimizationSuggestion> suggestions) {
        return suggestions.stream()
            .mapToDouble(OptimizationSuggestion::getExpectedImprovement)
            .sum();
    }

    // 内部类定义
    public static class AutoCompleteResult {
        private List<AutoCompleteSuggestion> suggestions;
        private SqlContext context;
        private long processingTime;

        public AutoCompleteResult(List<AutoCompleteSuggestion> suggestions, SqlContext context, long processingTime) {
            this.suggestions = suggestions;
            this.context = context;
            this.processingTime = processingTime;
        }

        // Getters
        public List<AutoCompleteSuggestion> getSuggestions() { return suggestions; }
        public SqlContext getContext() { return context; }
        public long getProcessingTime() { return processingTime; }
    }

    public static class AutoCompleteSuggestion {
        private String text;
        private String type;
        private String description;
        private double score;
        private String insertText;

        public AutoCompleteSuggestion(String text, String type, String description, double score, String insertText) {
            this.text = text;
            this.type = type;
            this.description = description;
            this.score = score;
            this.insertText = insertText;
        }

        // Getters
        public String getText() { return text; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public double getScore() { return score; }
        public String getInsertText() { return insertText; }
    }

    public static class SqlContext {
        private String partialQuery;
        private int cursorPosition;
        private String currentClause;
        private String currentWord = "";
        private List<String> referencedTables = new ArrayList<>();

        // Getters and Setters
        public String getPartialQuery() { return partialQuery; }
        public void setPartialQuery(String partialQuery) { this.partialQuery = partialQuery; }
        
        public int getCursorPosition() { return cursorPosition; }
        public void setCursorPosition(int cursorPosition) { this.cursorPosition = cursorPosition; }
        
        public String getCurrentClause() { return currentClause; }
        public void setCurrentClause(String currentClause) { this.currentClause = currentClause; }
        
        public String getCurrentWord() { return currentWord; }
        public void setCurrentWord(String currentWord) { this.currentWord = currentWord; }
        
        public List<String> getReferencedTables() { return referencedTables; }
        public void setReferencedTables(List<String> referencedTables) { this.referencedTables = referencedTables; }
    }

    public static class SqlErrorDetectionResult {
        private List<SqlError> errors;
        private List<SqlSuggestion> suggestions;
        private boolean isValid;

        public SqlErrorDetectionResult(List<SqlError> errors, List<SqlSuggestion> suggestions, boolean isValid) {
            this.errors = errors;
            this.suggestions = suggestions;
            this.isValid = isValid;
        }

        // Getters
        public List<SqlError> getErrors() { return errors; }
        public List<SqlSuggestion> getSuggestions() { return suggestions; }
        public boolean isValid() { return isValid; }
    }

    public static class SqlError {
        private String type;
        private String message;
        private int position;
        private String suggestion;

        public SqlError(String type, String message, int position, String suggestion) {
            this.type = type;
            this.message = message;
            this.position = position;
            this.suggestion = suggestion;
        }

        // Getters
        public String getType() { return type; }
        public String getMessage() { return message; }
        public int getPosition() { return position; }
        public String getSuggestion() { return suggestion; }
    }

    public static class SqlSuggestion {
        private String title;
        private String description;
        private String fixedSql;

        public SqlSuggestion(String title, String description, String fixedSql) {
            this.title = title;
            this.description = description;
            this.fixedSql = fixedSql;
        }

        // Getters
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getFixedSql() { return fixedSql; }
    }

    public static class QueryOptimizationResult {
        private List<OptimizationSuggestion> suggestions;
        private double totalExpectedImprovement;

        public QueryOptimizationResult(List<OptimizationSuggestion> suggestions, double totalExpectedImprovement) {
            this.suggestions = suggestions;
            this.totalExpectedImprovement = totalExpectedImprovement;
        }

        // Getters
        public List<OptimizationSuggestion> getSuggestions() { return suggestions; }
        public double getTotalExpectedImprovement() { return totalExpectedImprovement; }
    }

    public static class OptimizationSuggestion {
        private String type;
        private String description;
        private double expectedImprovement;

        public OptimizationSuggestion(String type, String description, double expectedImprovement) {
            this.type = type;
            this.description = description;
            this.expectedImprovement = expectedImprovement;
        }

        // Getters
        public String getType() { return type; }
        public String getDescription() { return description; }
        public double getExpectedImprovement() { return expectedImprovement; }
    }
}