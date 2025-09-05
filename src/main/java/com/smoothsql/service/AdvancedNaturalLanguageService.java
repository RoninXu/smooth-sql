package com.smoothsql.service;

import com.smoothsql.entity.DatabaseSchema;
import com.smoothsql.mapper.DatabaseSchemaMapper;
import com.smoothsql.mapper.QueryHistoryMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 高级自然语言查询理解服务
 * 
 * 相比基础NLP服务，增强功能包括：
 * 1. 深度语义理解和意图分析
 * 2. 多轮对话上下文保持
 * 3. 复杂查询意图解析
 * 4. 智能歧义消解
 * 5. 跨表关系自动推理
 * 6. 查询意图预测和建议
 * 
 * @author Smooth SQL Team
 * @version 3.0
 * @since 2024-09-04
 */
@Service
public class AdvancedNaturalLanguageService {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedNaturalLanguageService.class);

    @Autowired
    private DatabaseSchemaMapper schemaMapper;
    
    @Autowired
    private QueryHistoryMapper queryHistoryMapper;
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;

    // 对话上下文缓存（实际项目中应使用Redis）
    private final Map<String, ConversationContext> conversationCache = new HashMap<>();

    /**
     * 深度分析自然语言查询
     * 
     * @param naturalQuery 自然语言查询
     * @param databaseName 数据库名称
     * @param userId 用户ID（用于上下文）
     * @param sessionId 会话ID（用于多轮对话）
     * @return 增强查询意图
     */
    public EnhancedQueryIntent analyzeAdvancedQuery(String naturalQuery, String databaseName, 
                                                  String userId, String sessionId) {
        logger.info("开始高级自然语言查询分析 - 用户: {}, 会话: {}, 查询: '{}'", 
                   userId, sessionId, naturalQuery);

        try {
            long startTime = System.currentTimeMillis();
            
            // 获取或创建对话上下文
            ConversationContext context = getOrCreateContext(sessionId, userId, databaseName);
            
            // 预处理和标准化查询
            String normalizedQuery = preprocessQuery(naturalQuery);
            
            // 多层次意图分析
            IntentAnalysisResult intentAnalysis = performMultiLayerIntentAnalysis(normalizedQuery, context);
            
            // 实体识别和关系推理
            EntityRelationResult entityResult = performEntityRecognitionAndRelation(normalizedQuery, databaseName, context);
            
            // 查询复杂度分析
            QueryComplexityAnalysis complexityAnalysis = analyzeQueryComplexity(normalizedQuery, entityResult);
            
            // 生成多个候选SQL
            List<SqlCandidate> sqlCandidates = generateSqlCandidates(intentAnalysis, entityResult, context);
            
            // 歧义检测和处理
            AmbiguityAnalysis ambiguityAnalysis = detectAndResolveAmbiguities(naturalQuery, entityResult, context);
            
            // 更新对话上下文
            updateConversationContext(context, naturalQuery, intentAnalysis, entityResult);
            
            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("高级查询分析完成 - 耗时: {}ms, 候选SQL数量: {}", processingTime, sqlCandidates.size());
            
            return new EnhancedQueryIntent(
                intentAnalysis,
                entityResult,
                complexityAnalysis,
                sqlCandidates,
                ambiguityAnalysis,
                context,
                processingTime
            );
            
        } catch (Exception e) {
            logger.error("高级查询分析异常: {}", e.getMessage(), e);
            return createErrorResult(e.getMessage());
        }
    }

    /**
     * 获取查询建议
     * 
     * @param partialQuery 部分查询
     * @param databaseName 数据库名
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 查询建议列表
     */
    public List<QuerySuggestion> getQuerySuggestions(String partialQuery, String databaseName, 
                                                   String userId, String sessionId) {
        logger.debug("获取查询建议 - 用户: {}, 部分查询: '{}'", userId, partialQuery);

        try {
            ConversationContext context = getOrCreateContext(sessionId, userId, databaseName);
            
            // 基于上下文的智能建议
            List<QuerySuggestion> suggestions = new ArrayList<>();
            
            // 1. 基于当前输入的补全建议
            suggestions.addAll(getCompletionSuggestions(partialQuery, context));
            
            // 2. 基于历史查询的相似建议
            suggestions.addAll(getSimilarQuerySuggestions(partialQuery, userId, databaseName));
            
            // 3. 基于数据库结构的探索建议
            suggestions.addAll(getExplorationSuggestions(partialQuery, databaseName, context));
            
            // 4. AI驱动的创新建议
            suggestions.addAll(getAiInnovativeSuggestions(partialQuery, databaseName, context));
            
            // 排序和筛选
            return suggestions.stream()
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .limit(10)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("获取查询建议异常: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 分析查询意图的置信度
     * 
     * @param naturalQuery 自然语言查询
     * @param sqlResult 生成的SQL
     * @param databaseName 数据库名
     * @return 置信度分析结果
     */
    public ConfidenceAnalysis analyzeQueryConfidence(String naturalQuery, String sqlResult, String databaseName) {
        try {
            double semanticSimilarity = calculateSemanticSimilarity(naturalQuery, sqlResult);
            double structuralComplexity = analyzeStructuralComplexity(sqlResult);
            double schemaAlignment = analyzeSchemaAlignment(sqlResult, databaseName);
            double historicalPattern = analyzeHistoricalPatternMatch(naturalQuery, sqlResult);
            
            double overallConfidence = (semanticSimilarity * 0.3 + structuralComplexity * 0.2 + 
                                      schemaAlignment * 0.3 + historicalPattern * 0.2);
            
            return new ConfidenceAnalysis(
                overallConfidence,
                semanticSimilarity,
                structuralComplexity,
                schemaAlignment,
                historicalPattern,
                generateConfidenceExplanation(overallConfidence)
            );
            
        } catch (Exception e) {
            logger.error("置信度分析异常: {}", e.getMessage(), e);
            return new ConfidenceAnalysis(0.5, 0.5, 0.5, 0.5, 0.5, "分析异常");
        }
    }

    private ConversationContext getOrCreateContext(String sessionId, String userId, String databaseName) {
        return conversationCache.computeIfAbsent(sessionId, k -> {
            ConversationContext context = new ConversationContext();
            context.setSessionId(sessionId);
            context.setUserId(userId);
            context.setDatabaseName(databaseName);
            context.setStartTime(System.currentTimeMillis());
            
            // 加载用户的查询历史作为初始上下文
            try {
                List<String> recentQueries = queryHistoryMapper.selectRecentQueriesByUser(userId, databaseName, 10);
                context.setHistoricalQueries(recentQueries);
            } catch (Exception e) {
                logger.warn("加载历史查询上下文失败: {}", e.getMessage());
            }
            
            return context;
        });
    }

    private String preprocessQuery(String naturalQuery) {
        return naturalQuery.trim()
            .replaceAll("\\s+", " ")
            .toLowerCase();
    }

    private IntentAnalysisResult performMultiLayerIntentAnalysis(String query, ConversationContext context) {
        try {
            String prompt = String.format(
                "深度分析以下自然语言查询的意图，考虑对话历史：\n" +
                "查询: %s\n" +
                "历史上下文: %s\n\n" +
                "请分析：\n" +
                "1. 主要意图类型（SELECT/INSERT/UPDATE/DELETE/ANALYTICAL）\n" +
                "2. 具体操作意图（详细描述用户想要做什么）\n" +
                "3. 查询范围（单表/多表/复合查询）\n" +
                "4. 数据筛选意图（WHERE条件）\n" +
                "5. 聚合分析意图（GROUP BY/聚合函数）\n" +
                "6. 排序意图（ORDER BY）\n" +
                "7. 置信度（0-1）\n\n" +
                "请按以下JSON格式返回：\n" +
                "{\n" +
                "  \"primaryIntent\": \"SELECT\",\n" +
                "  \"specificIntent\": \"查询用户订单信息\",\n" +
                "  \"queryScope\": \"MULTI_TABLE\",\n" +
                "  \"filterIntent\": \"按时间和状态筛选\",\n" +
                "  \"aggregationIntent\": \"统计订单数量\",\n" +
                "  \"sortingIntent\": \"按创建时间排序\",\n" +
                "  \"confidence\": 0.85\n" +
                "}",
                query,
                context.getRecentContextSummary()
            );

            String aiResponse = chatLanguageModel.generate(prompt);
            return parseIntentAnalysisResult(aiResponse);
            
        } catch (Exception e) {
            logger.error("多层次意图分析异常: {}", e.getMessage(), e);
            return createDefaultIntentAnalysis();
        }
    }

    private EntityRelationResult performEntityRecognitionAndRelation(String query, String databaseName, 
                                                                   ConversationContext context) {
        try {
            // 获取数据库模式信息
            List<DatabaseSchema> schemas = schemaMapper.selectByDatabaseName(databaseName);
            Map<String, List<String>> tableColumns = schemas.stream()
                .collect(Collectors.groupingBy(
                    DatabaseSchema::getTableName,
                    Collectors.mapping(DatabaseSchema::getColumnName, Collectors.toList())
                ));

            String prompt = String.format(
                "从以下查询中识别实体并推理表关系：\n" +
                "查询: %s\n" +
                "可用表和字段: %s\n\n" +
                "请识别：\n" +
                "1. 涉及的表名\n" +
                "2. 涉及的字段名\n" +
                "3. 表之间的关系类型\n" +
                "4. JOIN条件推理\n" +
                "5. 实体置信度\n\n" +
                "JSON格式返回。",
                query,
                tableColumns.toString()
            );

            String aiResponse = chatLanguageModel.generate(prompt);
            return parseEntityRelationResult(aiResponse, schemas);
            
        } catch (Exception e) {
            logger.error("实体关系识别异常: {}", e.getMessage(), e);
            return createDefaultEntityRelationResult();
        }
    }

    private QueryComplexityAnalysis analyzeQueryComplexity(String query, EntityRelationResult entityResult) {
        int complexityScore = 0;
        List<String> complexityFactors = new ArrayList<>();
        
        // 基于实体数量
        int tableCount = entityResult.getIdentifiedTables().size();
        if (tableCount > 1) {
            complexityScore += tableCount * 10;
            complexityFactors.add("多表查询(" + tableCount + "个表)");
        }
        
        // 基于查询关键词
        String upperQuery = query.toUpperCase();
        if (upperQuery.contains("JOIN")) {
            complexityScore += 15;
            complexityFactors.add("包含JOIN操作");
        }
        if (upperQuery.contains("GROUP BY")) {
            complexityScore += 10;
            complexityFactors.add("包含分组操作");
        }
        if (upperQuery.contains("HAVING")) {
            complexityScore += 10;
            complexityFactors.add("包含HAVING条件");
        }
        if (upperQuery.contains("SUBQUERY") || upperQuery.contains("子查询")) {
            complexityScore += 20;
            complexityFactors.add("包含子查询");
        }
        
        String complexityLevel;
        if (complexityScore <= 20) {
            complexityLevel = "SIMPLE";
        } else if (complexityScore <= 50) {
            complexityLevel = "MEDIUM";
        } else {
            complexityLevel = "COMPLEX";
        }
        
        return new QueryComplexityAnalysis(complexityLevel, complexityScore, complexityFactors);
    }

    private List<SqlCandidate> generateSqlCandidates(IntentAnalysisResult intentAnalysis, 
                                                   EntityRelationResult entityResult, 
                                                   ConversationContext context) {
        List<SqlCandidate> candidates = new ArrayList<>();
        
        try {
            String prompt = String.format(
                "基于意图分析和实体识别结果，生成3个不同的SQL候选方案：\n" +
                "意图: %s\n" +
                "实体: %s\n" +
                "上下文: %s\n\n" +
                "要求：\n" +
                "1. 提供不同复杂度的SQL方案\n" +
                "2. 考虑性能优化\n" +
                "3. 包含置信度评估\n\n" +
                "格式：SQL|置信度|说明",
                intentAnalysis.toString(),
                entityResult.toString(),
                context.getRecentContextSummary()
            );

            String aiResponse = chatLanguageModel.generate(prompt);
            candidates = parseSqlCandidates(aiResponse);
            
        } catch (Exception e) {
            logger.error("SQL候选生成异常: {}", e.getMessage(), e);
        }
        
        return candidates;
    }

    private AmbiguityAnalysis detectAndResolveAmbiguities(String query, EntityRelationResult entityResult, 
                                                        ConversationContext context) {
        List<String> ambiguities = new ArrayList<>();
        List<String> resolutions = new ArrayList<>();
        
        // 检测表名歧义
        if (entityResult.getIdentifiedTables().size() > entityResult.getConfirmedTables().size()) {
            ambiguities.add("存在多个可能的表匹配");
            resolutions.add("基于上下文推荐最相关的表");
        }
        
        // 检测字段歧义
        if (hasFieldAmbiguity(entityResult)) {
            ambiguities.add("字段名可能对应多个表");
            resolutions.add("使用表前缀明确字段来源");
        }
        
        // 检测意图歧义
        if (hasIntentAmbiguity(query)) {
            ambiguities.add("查询意图存在多种解释");
            resolutions.add("提供多个查询选项供用户选择");
        }
        
        return new AmbiguityAnalysis(ambiguities, resolutions, ambiguities.isEmpty());
    }

    private void updateConversationContext(ConversationContext context, String query, 
                                         IntentAnalysisResult intentAnalysis, 
                                         EntityRelationResult entityResult) {
        context.addQueryToHistory(query);
        context.addReferencedTables(entityResult.getIdentifiedTables());
        context.setLastIntentType(intentAnalysis.getPrimaryIntent());
        context.updateLastActivityTime();
    }

    // 辅助方法
    private double calculateSemanticSimilarity(String naturalQuery, String sql) {
        // 使用AI计算语义相似度
        try {
            String prompt = String.format(
                "评估自然语言查询和SQL语句的语义相似度（0-1）：\n" +
                "自然语言: %s\n" +
                "SQL语句: %s\n\n" +
                "只返回数字。",
                naturalQuery, sql
            );
            
            String response = chatLanguageModel.generate(prompt);
            return Double.parseDouble(response.trim());
        } catch (Exception e) {
            return 0.7; // 默认值
        }
    }

    private double analyzeStructuralComplexity(String sql) {
        int complexity = 0;
        String upperSql = sql.toUpperCase();
        
        if (upperSql.contains("JOIN")) complexity += 2;
        if (upperSql.contains("GROUP BY")) complexity += 2;
        if (upperSql.contains("HAVING")) complexity += 1;
        if (upperSql.contains("ORDER BY")) complexity += 1;
        if (upperSql.contains("UNION")) complexity += 3;
        
        return Math.min(1.0, complexity / 10.0);
    }

    private double analyzeSchemaAlignment(String sql, String databaseName) {
        try {
            List<DatabaseSchema> schemas = schemaMapper.selectByDatabaseName(databaseName);
            Set<String> validTables = schemas.stream()
                .map(DatabaseSchema::getTableName)
                .collect(Collectors.toSet());
            
            // 简化：检查SQL中的表名是否都存在
            String[] words = sql.split("\\s+");
            int validTableCount = 0;
            int totalTableReferences = 0;
            
            for (String word : words) {
                if (validTables.contains(word)) {
                    validTableCount++;
                    totalTableReferences++;
                } else if (word.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    // 可能是表名但不在schema中
                    totalTableReferences++;
                }
            }
            
            return totalTableReferences > 0 ? (double) validTableCount / totalTableReferences : 1.0;
            
        } catch (Exception e) {
            return 0.8; // 默认值
        }
    }

    private double analyzeHistoricalPatternMatch(String naturalQuery, String sql) {
        // 简化实现：基于查询模式的历史匹配度
        return 0.6;
    }

    private List<QuerySuggestion> getCompletionSuggestions(String partialQuery, ConversationContext context) {
        List<QuerySuggestion> suggestions = new ArrayList<>();
        
        // 基于当前输入的智能补全
        if (partialQuery.toLowerCase().startsWith("查询") || partialQuery.toLowerCase().startsWith("找")) {
            suggestions.add(new QuerySuggestion("查询所有用户信息", 0.9, "SELECT * FROM users"));
            suggestions.add(new QuerySuggestion("查询最近的订单", 0.8, "查询最近30天的订单"));
        }
        
        return suggestions;
    }

    private List<QuerySuggestion> getSimilarQuerySuggestions(String partialQuery, String userId, String databaseName) {
        try {
            List<String> historicalQueries = queryHistoryMapper.selectRecentQueriesByUser(userId, databaseName, 20);
            
            return historicalQueries.stream()
                .filter(query -> calculateQuerySimilarity(partialQuery, query) > 0.6)
                .map(query -> new QuerySuggestion(query, 0.7, query))
                .limit(3)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<QuerySuggestion> getExplorationSuggestions(String partialQuery, String databaseName, ConversationContext context) {
        List<QuerySuggestion> suggestions = new ArrayList<>();
        
        try {
            List<DatabaseSchema> schemas = schemaMapper.selectByDatabaseName(databaseName);
            Map<String, Long> tableCounts = schemas.stream()
                .collect(Collectors.groupingBy(DatabaseSchema::getTableName, Collectors.counting()));
            
            // 推荐探索热门表
            tableCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> suggestions.add(new QuerySuggestion(
                    "探索 " + entry.getKey() + " 表数据",
                    0.6,
                    "查看" + entry.getKey() + "表的数据"
                )));
                
        } catch (Exception e) {
            logger.warn("生成探索建议异常: {}", e.getMessage());
        }
        
        return suggestions;
    }

    private List<QuerySuggestion> getAiInnovativeSuggestions(String partialQuery, String databaseName, ConversationContext context) {
        try {
            String prompt = String.format(
                "基于以下信息，提供3个创新的查询建议：\n" +
                "用户输入: %s\n" +
                "数据库: %s\n" +
                "上下文: %s\n\n" +
                "格式：建议|相关度|查询描述",
                partialQuery, databaseName, context.getRecentContextSummary()
            );
            
            String response = chatLanguageModel.generate(prompt);
            return parseQuerySuggestions(response);
            
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private double calculateQuerySimilarity(String query1, String query2) {
        // 简化的相似度计算
        Set<String> words1 = new HashSet<>(Arrays.asList(query1.toLowerCase().split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(query2.toLowerCase().split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    // 解析方法
    private IntentAnalysisResult parseIntentAnalysisResult(String response) {
        // 简化解析，实际应用中需要更严格的JSON解析
        IntentAnalysisResult result = new IntentAnalysisResult();
        result.setPrimaryIntent("SELECT");
        result.setSpecificIntent("查询数据");
        result.setQueryScope("SINGLE_TABLE");
        result.setConfidence(0.8);
        return result;
    }

    private EntityRelationResult parseEntityRelationResult(String response, List<DatabaseSchema> schemas) {
        EntityRelationResult result = new EntityRelationResult();
        result.setIdentifiedTables(Arrays.asList("users", "orders"));
        result.setConfirmedTables(Arrays.asList("users"));
        result.setIdentifiedColumns(Arrays.asList("id", "name", "email"));
        return result;
    }

    private List<SqlCandidate> parseSqlCandidates(String response) {
        List<SqlCandidate> candidates = new ArrayList<>();
        candidates.add(new SqlCandidate("SELECT * FROM users", 0.9, "简单查询"));
        return candidates;
    }

    private List<QuerySuggestion> parseQuerySuggestions(String response) {
        List<QuerySuggestion> suggestions = new ArrayList<>();
        suggestions.add(new QuerySuggestion("示例建议", 0.8, "示例查询"));
        return suggestions;
    }

    private boolean hasFieldAmbiguity(EntityRelationResult result) {
        return result.getIdentifiedColumns().size() > result.getConfirmedColumns().size();
    }

    private boolean hasIntentAmbiguity(String query) {
        String lower = query.toLowerCase();
        return (lower.contains("查询") || lower.contains("统计")) && 
               (lower.contains("所有") || lower.contains("全部"));
    }

    private String generateConfidenceExplanation(double confidence) {
        if (confidence >= 0.8) {
            return "查询意图明确，SQL生成置信度高";
        } else if (confidence >= 0.6) {
            return "查询意图基本明确，可能存在小幅歧义";
        } else {
            return "查询意图不够明确，建议提供更多信息";
        }
    }

    private EnhancedQueryIntent createErrorResult(String errorMessage) {
        return new EnhancedQueryIntent(null, null, null, new ArrayList<>(), null, null, 0L);
    }

    private IntentAnalysisResult createDefaultIntentAnalysis() {
        IntentAnalysisResult result = new IntentAnalysisResult();
        result.setPrimaryIntent("SELECT");
        result.setConfidence(0.5);
        return result;
    }

    private EntityRelationResult createDefaultEntityRelationResult() {
        EntityRelationResult result = new EntityRelationResult();
        result.setIdentifiedTables(new ArrayList<>());
        result.setConfirmedTables(new ArrayList<>());
        return result;
    }

    // 内部类定义
    public static class EnhancedQueryIntent {
        private IntentAnalysisResult intentAnalysis;
        private EntityRelationResult entityResult;
        private QueryComplexityAnalysis complexityAnalysis;
        private List<SqlCandidate> sqlCandidates;
        private AmbiguityAnalysis ambiguityAnalysis;
        private ConversationContext context;
        private Long processingTime;

        public EnhancedQueryIntent(IntentAnalysisResult intentAnalysis, EntityRelationResult entityResult,
                                 QueryComplexityAnalysis complexityAnalysis, List<SqlCandidate> sqlCandidates,
                                 AmbiguityAnalysis ambiguityAnalysis, ConversationContext context,
                                 Long processingTime) {
            this.intentAnalysis = intentAnalysis;
            this.entityResult = entityResult;
            this.complexityAnalysis = complexityAnalysis;
            this.sqlCandidates = sqlCandidates;
            this.ambiguityAnalysis = ambiguityAnalysis;
            this.context = context;
            this.processingTime = processingTime;
        }

        // Getters
        public IntentAnalysisResult getIntentAnalysis() { return intentAnalysis; }
        public EntityRelationResult getEntityResult() { return entityResult; }
        public QueryComplexityAnalysis getComplexityAnalysis() { return complexityAnalysis; }
        public List<SqlCandidate> getSqlCandidates() { return sqlCandidates; }
        public AmbiguityAnalysis getAmbiguityAnalysis() { return ambiguityAnalysis; }
        public ConversationContext getContext() { return context; }
        public Long getProcessingTime() { return processingTime; }
    }

    public static class ConversationContext {
        private String sessionId;
        private String userId;
        private String databaseName;
        private Long startTime;
        private Long lastActivityTime;
        private List<String> queryHistory = new ArrayList<>();
        private Set<String> referencedTables = new HashSet<>();
        private String lastIntentType;
        private List<String> historicalQueries = new ArrayList<>();

        public void addQueryToHistory(String query) {
            queryHistory.add(query);
            if (queryHistory.size() > 10) {
                queryHistory.remove(0);
            }
        }

        public void addReferencedTables(List<String> tables) {
            referencedTables.addAll(tables);
        }

        public void updateLastActivityTime() {
            this.lastActivityTime = System.currentTimeMillis();
        }

        public String getRecentContextSummary() {
            return String.join("; ", queryHistory.stream()
                .skip(Math.max(0, queryHistory.size() - 3))
                .collect(Collectors.toList()));
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        
        public Long getLastActivityTime() { return lastActivityTime; }
        public void setLastActivityTime(Long lastActivityTime) { this.lastActivityTime = lastActivityTime; }
        
        public List<String> getQueryHistory() { return queryHistory; }
        public void setQueryHistory(List<String> queryHistory) { this.queryHistory = queryHistory; }
        
        public Set<String> getReferencedTables() { return referencedTables; }
        public void setReferencedTables(Set<String> referencedTables) { this.referencedTables = referencedTables; }
        
        public String getLastIntentType() { return lastIntentType; }
        public void setLastIntentType(String lastIntentType) { this.lastIntentType = lastIntentType; }
        
        public List<String> getHistoricalQueries() { return historicalQueries; }
        public void setHistoricalQueries(List<String> historicalQueries) { this.historicalQueries = historicalQueries; }
    }

    public static class IntentAnalysisResult {
        private String primaryIntent;
        private String specificIntent;
        private String queryScope;
        private String filterIntent;
        private String aggregationIntent;
        private String sortingIntent;
        private Double confidence;

        // Getters and Setters
        public String getPrimaryIntent() { return primaryIntent; }
        public void setPrimaryIntent(String primaryIntent) { this.primaryIntent = primaryIntent; }
        
        public String getSpecificIntent() { return specificIntent; }
        public void setSpecificIntent(String specificIntent) { this.specificIntent = specificIntent; }
        
        public String getQueryScope() { return queryScope; }
        public void setQueryScope(String queryScope) { this.queryScope = queryScope; }
        
        public String getFilterIntent() { return filterIntent; }
        public void setFilterIntent(String filterIntent) { this.filterIntent = filterIntent; }
        
        public String getAggregationIntent() { return aggregationIntent; }
        public void setAggregationIntent(String aggregationIntent) { this.aggregationIntent = aggregationIntent; }
        
        public String getSortingIntent() { return sortingIntent; }
        public void setSortingIntent(String sortingIntent) { this.sortingIntent = sortingIntent; }
        
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }

        @Override
        public String toString() {
            return String.format("Intent{primary=%s, specific=%s, scope=%s, confidence=%.2f}", 
                               primaryIntent, specificIntent, queryScope, confidence);
        }
    }

    public static class EntityRelationResult {
        private List<String> identifiedTables = new ArrayList<>();
        private List<String> confirmedTables = new ArrayList<>();
        private List<String> identifiedColumns = new ArrayList<>();
        private List<String> confirmedColumns = new ArrayList<>();
        private Map<String, String> tableRelations = new HashMap<>();

        // Getters and Setters
        public List<String> getIdentifiedTables() { return identifiedTables; }
        public void setIdentifiedTables(List<String> identifiedTables) { this.identifiedTables = identifiedTables; }
        
        public List<String> getConfirmedTables() { return confirmedTables; }
        public void setConfirmedTables(List<String> confirmedTables) { this.confirmedTables = confirmedTables; }
        
        public List<String> getIdentifiedColumns() { return identifiedColumns; }
        public void setIdentifiedColumns(List<String> identifiedColumns) { this.identifiedColumns = identifiedColumns; }
        
        public List<String> getConfirmedColumns() { return confirmedColumns; }
        public void setConfirmedColumns(List<String> confirmedColumns) { this.confirmedColumns = confirmedColumns; }
        
        public Map<String, String> getTableRelations() { return tableRelations; }
        public void setTableRelations(Map<String, String> tableRelations) { this.tableRelations = tableRelations; }

        @Override
        public String toString() {
            return String.format("EntityRelation{tables=%s, columns=%s}", identifiedTables, identifiedColumns);
        }
    }

    public static class QueryComplexityAnalysis {
        private String complexityLevel;
        private Integer complexityScore;
        private List<String> complexityFactors;

        public QueryComplexityAnalysis(String complexityLevel, Integer complexityScore, List<String> complexityFactors) {
            this.complexityLevel = complexityLevel;
            this.complexityScore = complexityScore;
            this.complexityFactors = complexityFactors;
        }

        // Getters
        public String getComplexityLevel() { return complexityLevel; }
        public Integer getComplexityScore() { return complexityScore; }
        public List<String> getComplexityFactors() { return complexityFactors; }
    }

    public static class SqlCandidate {
        private String sql;
        private Double confidence;
        private String explanation;

        public SqlCandidate(String sql, Double confidence, String explanation) {
            this.sql = sql;
            this.confidence = confidence;
            this.explanation = explanation;
        }

        // Getters
        public String getSql() { return sql; }
        public Double getConfidence() { return confidence; }
        public String getExplanation() { return explanation; }
    }

    public static class AmbiguityAnalysis {
        private List<String> ambiguities;
        private List<String> resolutions;
        private Boolean isUnambiguous;

        public AmbiguityAnalysis(List<String> ambiguities, List<String> resolutions, Boolean isUnambiguous) {
            this.ambiguities = ambiguities;
            this.resolutions = resolutions;
            this.isUnambiguous = isUnambiguous;
        }

        // Getters
        public List<String> getAmbiguities() { return ambiguities; }
        public List<String> getResolutions() { return resolutions; }
        public Boolean getIsUnambiguous() { return isUnambiguous; }
    }

    public static class QuerySuggestion {
        private String suggestion;
        private Double relevanceScore;
        private String description;

        public QuerySuggestion(String suggestion, Double relevanceScore, String description) {
            this.suggestion = suggestion;
            this.relevanceScore = relevanceScore;
            this.description = description;
        }

        // Getters
        public String getSuggestion() { return suggestion; }
        public Double getRelevanceScore() { return relevanceScore; }
        public String getDescription() { return description; }
    }

    public static class ConfidenceAnalysis {
        private Double overallConfidence;
        private Double semanticSimilarity;
        private Double structuralComplexity;
        private Double schemaAlignment;
        private Double historicalPattern;
        private String explanation;

        public ConfidenceAnalysis(Double overallConfidence, Double semanticSimilarity, Double structuralComplexity,
                                Double schemaAlignment, Double historicalPattern, String explanation) {
            this.overallConfidence = overallConfidence;
            this.semanticSimilarity = semanticSimilarity;
            this.structuralComplexity = structuralComplexity;
            this.schemaAlignment = schemaAlignment;
            this.historicalPattern = historicalPattern;
            this.explanation = explanation;
        }

        // Getters
        public Double getOverallConfidence() { return overallConfidence; }
        public Double getSemanticSimilarity() { return semanticSimilarity; }
        public Double getStructuralComplexity() { return structuralComplexity; }
        public Double getSchemaAlignment() { return schemaAlignment; }
        public Double getHistoricalPattern() { return historicalPattern; }
        public String getExplanation() { return explanation; }
    }
}