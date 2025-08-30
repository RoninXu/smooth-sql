package com.smoothsql.service;

import com.smoothsql.entity.DatabaseSchema;
import com.smoothsql.mapper.DatabaseSchemaMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class NaturalLanguageService {

    @Autowired
    private DatabaseSchemaMapper databaseSchemaMapper;

    private static final Map<String, String> INTENT_KEYWORDS = new HashMap<>();
    private static final Map<String, String> OPERATION_KEYWORDS = new HashMap<>();
    
    static {
        // 查询意图关键词
        INTENT_KEYWORDS.put("查询|查找|搜索|检索|显示|展示|获取|找出|列出", "SELECT");
        INTENT_KEYWORDS.put("统计|计数|数量|总数|汇总", "COUNT");
        INTENT_KEYWORDS.put("求和|总和|合计", "SUM");
        INTENT_KEYWORDS.put("平均|均值", "AVG");
        INTENT_KEYWORDS.put("最大|最高|最多", "MAX");
        INTENT_KEYWORDS.put("最小|最低|最少", "MIN");
        INTENT_KEYWORDS.put("分组|按.*分组", "GROUP_BY");
        INTENT_KEYWORDS.put("排序|按.*排序|从大到小|从小到大|升序|降序", "ORDER_BY");
        INTENT_KEYWORDS.put("关联|连接|联合|结合.*查询", "JOIN");
        INTENT_KEYWORDS.put("内连接|内联", "INNER_JOIN");
        INTENT_KEYWORDS.put("左连接|左联", "LEFT_JOIN");
        INTENT_KEYWORDS.put("右连接|右联", "RIGHT_JOIN");
        INTENT_KEYWORDS.put("去重|不重复|唯一", "DISTINCT");
        
        // 操作关键词
        OPERATION_KEYWORDS.put("大于|超过|高于|多于", ">");
        OPERATION_KEYWORDS.put("小于|低于|少于|不足", "<");
        OPERATION_KEYWORDS.put("等于|是|为", "=");
        OPERATION_KEYWORDS.put("不等于|不是|不为", "!=");
        OPERATION_KEYWORDS.put("包含|含有", "LIKE");
        OPERATION_KEYWORDS.put("在.*之间|介于", "BETWEEN");
        OPERATION_KEYWORDS.put("昨天", "DATE_SUB(CURDATE(), INTERVAL 1 DAY)");
        OPERATION_KEYWORDS.put("今天|当天", "CURDATE()");
        OPERATION_KEYWORDS.put("本月", "MONTH(CURDATE())");
        OPERATION_KEYWORDS.put("本年|今年", "YEAR(CURDATE())");
    }

    public QueryIntent analyzeQuery(String naturalQuery, String databaseName) {
        QueryIntent intent = new QueryIntent();
        intent.setOriginalQuery(naturalQuery);
        intent.setDatabaseName(databaseName);
        
        // 识别查询意图
        String queryIntent = identifyIntent(naturalQuery);
        intent.setIntentType(queryIntent);
        
        // 提取表名
        List<String> tables = extractTables(naturalQuery, databaseName);
        intent.setTables(tables);
        
        // 提取字段名
        List<String> columns = extractColumns(naturalQuery, databaseName, tables);
        intent.setColumns(columns);
        
        // 提取条件
        List<QueryCondition> conditions = extractConditions(naturalQuery, databaseName, tables);
        intent.setConditions(conditions);
        
        // 提取排序和分组信息
        intent.setOrderBy(extractOrderBy(naturalQuery));
        intent.setGroupBy(extractGroupBy(naturalQuery));
        
        // 提取JOIN条件
        List<JoinCondition> joinConditions = extractJoinConditions(naturalQuery, databaseName, tables);
        intent.setJoinConditions(joinConditions);
        
        // 识别聚合函数
        String aggregateFunction = extractAggregateFunction(naturalQuery);
        intent.setAggregateFunction(aggregateFunction);
        
        // 提取聚合字段
        String aggregateColumn = extractAggregateColumn(naturalQuery, aggregateFunction);
        intent.setAggregateColumn(aggregateColumn);
        
        // 识别是否需要去重
        boolean isDistinct = extractDistinctFlag(naturalQuery);
        intent.setDistinct(isDistinct);
        
        return intent;
    }

    private String identifyIntent(String query) {
        query = query.toLowerCase();
        
        for (Map.Entry<String, String> entry : INTENT_KEYWORDS.entrySet()) {
            if (Pattern.compile(entry.getKey()).matcher(query).find()) {
                return entry.getValue();
            }
        }
        
        return "SELECT"; // 默认为查询
    }

    private List<String> extractTables(String query, String databaseName) {
        List<String> availableTables = databaseSchemaMapper.selectTableNames(databaseName);
        List<String> foundTables = new ArrayList<>();
        
        String queryLower = query.toLowerCase();
        
        for (String table : availableTables) {
            if (queryLower.contains(table.toLowerCase()) || 
                queryLower.contains(convertTableNameToChinese(table))) {
                foundTables.add(table);
            }
        }
        
        return foundTables.isEmpty() ? availableTables : foundTables;
    }

    private List<String> extractColumns(String query, String databaseName, List<String> tables) {
        List<String> allColumns = new ArrayList<>();
        Set<String> foundColumns = new HashSet<>();
        
        // 获取所有可能的字段名
        for (String table : tables) {
            List<String> tableColumns = databaseSchemaMapper.selectColumnNames(databaseName, table);
            allColumns.addAll(tableColumns);
        }
        
        String queryLower = query.toLowerCase();
        
        // 检查查询中是否包含特定字段名
        for (String column : allColumns) {
            if (queryLower.contains(column.toLowerCase()) || 
                queryLower.contains(convertColumnNameToChinese(column))) {
                foundColumns.add(column);
            }
        }
        
        return new ArrayList<>(foundColumns);
    }

    private List<QueryCondition> extractConditions(String query, String databaseName, List<String> tables) {
        List<QueryCondition> conditions = new ArrayList<>();
        
        // 简单的条件提取逻辑
        for (Map.Entry<String, String> entry : OPERATION_KEYWORDS.entrySet()) {
            if (Pattern.compile(entry.getKey()).matcher(query).find()) {
                QueryCondition condition = new QueryCondition();
                condition.setOperator(entry.getValue());
                condition.setField(extractFieldFromCondition(query, entry.getKey()));
                condition.setValue(extractValueFromCondition(query, entry.getKey()));
                conditions.add(condition);
                break; // 简单起见，只提取第一个条件
            }
        }
        
        return conditions;
    }

    private String extractOrderBy(String query) {
        if (query.matches(".*排序.*|.*从大到小.*|.*从小到大.*|.*升序.*|.*降序.*")) {
            if (query.contains("从大到小") || query.contains("降序")) {
                return "DESC";
            } else {
                return "ASC";
            }
        }
        return null;
    }

    private String extractGroupBy(String query) {
        if (query.matches(".*分组.*|.*按.*分组.*")) {
            // 简单提取分组字段逻辑
            return "extracted_group_field";
        }
        return null;
    }

    private String extractFieldFromCondition(String query, String keyword) {
        // 简化的字段提取逻辑
        return "id"; // 示例返回
    }

    private String extractValueFromCondition(String query, String keyword) {
        // 简化的值提取逻辑
        return "1"; // 示例返回
    }

    private String convertTableNameToChinese(String tableName) {
        // 表名英中文映射
        Map<String, String> tableMapping = new HashMap<>();
        tableMapping.put("users", "用户");
        tableMapping.put("orders", "订单");
        tableMapping.put("products", "产品");
        
        return tableMapping.getOrDefault(tableName.toLowerCase(), tableName);
    }

    private String convertColumnNameToChinese(String columnName) {
        // 字段名英中文映射
        Map<String, String> columnMapping = new HashMap<>();
        columnMapping.put("id", "编号|ID");
        columnMapping.put("name", "名称|姓名");
        columnMapping.put("username", "用户名");
        columnMapping.put("email", "邮箱|电子邮件");
        columnMapping.put("created_at", "创建时间|注册时间");
        columnMapping.put("total_amount", "总金额|金额");
        columnMapping.put("status", "状态");
        columnMapping.put("price", "价格");
        columnMapping.put("category", "分类|类别");
        
        return columnMapping.getOrDefault(columnName.toLowerCase(), columnName);
    }
    
    private List<JoinCondition> extractJoinConditions(String query, String databaseName, List<String> tables) {
        List<JoinCondition> joinConditions = new ArrayList<>();
        
        // 如果有多个表且查询中包含JOIN关键词
        if (tables.size() > 1) {
            String queryLower = query.toLowerCase();
            String joinType = "INNER"; // 默认内连接
            
            // 确定JOIN类型
            if (queryLower.contains("左连接") || queryLower.contains("左联")) {
                joinType = "LEFT";
            } else if (queryLower.contains("右连接") || queryLower.contains("右联")) {
                joinType = "RIGHT";
            } else if (queryLower.contains("内连接") || queryLower.contains("内联")) {
                joinType = "INNER";
            }
            
            // 简化的JOIN条件生成，假设是基于ID连接
            for (int i = 1; i < tables.size(); i++) {
                JoinCondition joinCondition = new JoinCondition();
                joinCondition.setJoinType(joinType);
                joinCondition.setLeftTable(tables.get(0));
                joinCondition.setRightTable(tables.get(i));
                joinCondition.setLeftColumn("id");
                joinCondition.setRightColumn(tables.get(0).substring(0, tables.get(0).length() - 1) + "_id"); // 例如: users -> user_id
                joinConditions.add(joinCondition);
            }
        }
        
        return joinConditions;
    }
    
    private String extractAggregateFunction(String query) {
        String queryLower = query.toLowerCase();
        
        if (queryLower.matches(".*统计.*|.*计数.*|.*数量.*|.*总数.*")) {
            return "COUNT";
        } else if (queryLower.matches(".*求和.*|.*总和.*|.*合计.*")) {
            return "SUM";
        } else if (queryLower.matches(".*平均.*|.*均值.*")) {
            return "AVG";
        } else if (queryLower.matches(".*最大.*|.*最高.*|.*最多.*")) {
            return "MAX";
        } else if (queryLower.matches(".*最小.*|.*最低.*|.*最少.*")) {
            return "MIN";
        }
        
        return null;
    }
    
    private String extractAggregateColumn(String query, String aggregateFunction) {
        if (aggregateFunction == null) {
            return null;
        }
        
        String queryLower = query.toLowerCase();
        
        // 简化的聚合字段识别
        if ("COUNT".equals(aggregateFunction)) {
            return "*"; // COUNT通常用*
        } else {
            // 对于SUM、AVG、MAX、MIN，尝试识别数值字段
            if (queryLower.contains("金额") || queryLower.contains("价格") || queryLower.contains("total")) {
                return "total_amount";
            } else if (queryLower.contains("年龄") || queryLower.contains("age")) {
                return "age";
            } else if (queryLower.contains("分数") || queryLower.contains("score")) {
                return "score";
            }
            return "id"; // 默认字段
        }
    }
    
    private boolean extractDistinctFlag(String query) {
        String queryLower = query.toLowerCase();
        return queryLower.contains("去重") || queryLower.contains("不重复") || queryLower.contains("唯一");
    }

    // 内部类：查询意图
    public static class QueryIntent {
        private String originalQuery;
        private String databaseName;
        private String intentType;
        private List<String> tables;
        private List<String> columns;
        private List<QueryCondition> conditions;
        private String orderBy;
        private String groupBy;
        private List<JoinCondition> joinConditions;
        private String aggregateFunction;
        private String aggregateColumn;
        private boolean isDistinct;

        // Getters and Setters
        public String getOriginalQuery() { return originalQuery; }
        public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
        
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        
        public String getIntentType() { return intentType; }
        public void setIntentType(String intentType) { this.intentType = intentType; }
        
        public List<String> getTables() { return tables; }
        public void setTables(List<String> tables) { this.tables = tables; }
        
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        
        public List<QueryCondition> getConditions() { return conditions; }
        public void setConditions(List<QueryCondition> conditions) { this.conditions = conditions; }
        
        public String getOrderBy() { return orderBy; }
        public void setOrderBy(String orderBy) { this.orderBy = orderBy; }
        
        public String getGroupBy() { return groupBy; }
        public void setGroupBy(String groupBy) { this.groupBy = groupBy; }
        
        public List<JoinCondition> getJoinConditions() { return joinConditions; }
        public void setJoinConditions(List<JoinCondition> joinConditions) { this.joinConditions = joinConditions; }
        
        public String getAggregateFunction() { return aggregateFunction; }
        public void setAggregateFunction(String aggregateFunction) { this.aggregateFunction = aggregateFunction; }
        
        public String getAggregateColumn() { return aggregateColumn; }
        public void setAggregateColumn(String aggregateColumn) { this.aggregateColumn = aggregateColumn; }
        
        public boolean isDistinct() { return isDistinct; }
        public void setDistinct(boolean distinct) { isDistinct = distinct; }
    }

    // 内部类：查询条件
    public static class QueryCondition {
        private String field;
        private String operator;
        private String value;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
    
    // 内部类：JOIN条件
    public static class JoinCondition {
        private String joinType; // INNER, LEFT, RIGHT, FULL
        private String leftTable;
        private String rightTable;
        private String leftColumn;
        private String rightColumn;
        
        public JoinCondition() {}
        
        public JoinCondition(String joinType, String leftTable, String rightTable, 
                           String leftColumn, String rightColumn) {
            this.joinType = joinType;
            this.leftTable = leftTable;
            this.rightTable = rightTable;
            this.leftColumn = leftColumn;
            this.rightColumn = rightColumn;
        }
        
        public String getJoinType() { return joinType; }
        public void setJoinType(String joinType) { this.joinType = joinType; }
        
        public String getLeftTable() { return leftTable; }
        public void setLeftTable(String leftTable) { this.leftTable = leftTable; }
        
        public String getRightTable() { return rightTable; }
        public void setRightTable(String rightTable) { this.rightTable = rightTable; }
        
        public String getLeftColumn() { return leftColumn; }
        public void setLeftColumn(String leftColumn) { this.leftColumn = leftColumn; }
        
        public String getRightColumn() { return rightColumn; }
        public void setRightColumn(String rightColumn) { this.rightColumn = rightColumn; }
    }
}