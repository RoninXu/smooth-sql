package com.smoothsql.service;

import com.smoothsql.dto.SqlExecuteResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 查询结果可视化和导出服务
 * 
 * 功能包括：
 * 1. 查询结果数据统计分析
 * 2. 图表数据生成（柱状图、饼图、折线图）
 * 3. Excel文件导出
 * 4. CSV文件导出
 * 5. 数据透视表生成
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
@Service
public class ResultVisualizationService {

    private static final Logger logger = LoggerFactory.getLogger(ResultVisualizationService.class);

    /**
     * 分析查询结果，生成可视化建议
     * 
     * @param response 查询执行结果
     * @return 可视化分析结果
     */
    public VisualizationAnalysis analyzeForVisualization(SqlExecuteResponse response) {
        logger.info("开始分析查询结果以生成可视化建议 - 行数: {}, 列数: {}", 
                   response.getData().getRows().size(), response.getData().getColumns().size());
        
        VisualizationAnalysis analysis = new VisualizationAnalysis();
        
        List<String> columns = response.getData().getColumns();
        List<List<Object>> rows = response.getData().getRows();
        
        // 分析数据类型
        Map<String, ColumnType> columnTypes = analyzeColumnTypes(columns, rows);
        analysis.setColumnTypes(columnTypes);
        
        // 生成图表建议
        List<ChartRecommendation> chartRecommendations = generateChartRecommendations(columnTypes, rows.size());
        analysis.setChartRecommendations(chartRecommendations);
        
        // 生成统计信息
        Map<String, Object> statistics = generateStatistics(columns, rows, columnTypes);
        analysis.setStatistics(statistics);
        
        logger.info("可视化分析完成 - 建议图表类型数量: {}", chartRecommendations.size());
        
        return analysis;
    }

    /**
     * 为特定图表类型准备数据
     * 
     * @param response 查询结果
     * @param chartType 图表类型
     * @param groupByColumn 分组列（可选）
     * @param valueColumn 数值列（可选）
     * @return 图表数据
     */
    public ChartData prepareChartData(SqlExecuteResponse response, String chartType, 
                                    String groupByColumn, String valueColumn) {
        logger.debug("准备图表数据 - 类型: {}, 分组列: {}, 数值列: {}", chartType, groupByColumn, valueColumn);
        
        List<String> columns = response.getData().getColumns();
        List<List<Object>> rows = response.getData().getRows();
        
        ChartData chartData = new ChartData();
        chartData.setType(chartType);
        
        switch (chartType.toUpperCase()) {
            case "BAR":
            case "COLUMN":
                chartData = prepareBarChartData(columns, rows, groupByColumn, valueColumn);
                break;
            case "PIE":
                chartData = preparePieChartData(columns, rows, groupByColumn, valueColumn);
                break;
            case "LINE":
                chartData = prepareLineChartData(columns, rows, groupByColumn, valueColumn);
                break;
            default:
                chartData = prepareTableData(columns, rows);
        }
        
        return chartData;
    }

    /**
     * 导出为Excel格式
     * 
     * @param response 查询结果
     * @param sheetName 工作表名称
     * @return Excel文件字节数组
     */
    public byte[] exportToExcel(SqlExecuteResponse response, String sheetName) throws IOException {
        logger.info("开始导出Excel文件 - 工作表: {}", sheetName);
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName != null ? sheetName : "查询结果");
        
        List<String> columns = response.getData().getColumns();
        List<List<Object>> rows = response.getData().getRows();
        
        // 创建表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        // 写入表头
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i));
            cell.setCellStyle(headerStyle);
        }
        
        // 写入数据行
        for (int i = 0; i < rows.size(); i++) {
            Row dataRow = sheet.createRow(i + 1);
            List<Object> rowData = rows.get(i);
            
            for (int j = 0; j < rowData.size(); j++) {
                Cell cell = dataRow.createCell(j);
                Object value = rowData.get(j);
                
                if (value != null) {
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }
        }
        
        // 自动调整列宽
        for (int i = 0; i < columns.size(); i++) {
            sheet.autoSizeColumn(i);
        }
        
        // 转换为字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        byte[] result = outputStream.toByteArray();
        logger.info("Excel导出完成 - 文件大小: {} bytes", result.length);
        
        return result;
    }

    /**
     * 导出为CSV格式
     * 
     * @param response 查询结果
     * @return CSV文件内容
     */
    public String exportToCSV(SqlExecuteResponse response) {
        logger.info("开始导出CSV文件");
        
        StringBuilder csv = new StringBuilder();
        
        List<String> columns = response.getData().getColumns();
        List<List<Object>> rows = response.getData().getRows();
        
        // 写入表头
        csv.append(String.join(",", columns)).append("\n");
        
        // 写入数据行
        for (List<Object> row : rows) {
            List<String> stringValues = new ArrayList<>();
            for (Object value : row) {
                if (value == null) {
                    stringValues.add("");
                } else {
                    // CSV转义处理
                    String strValue = value.toString();
                    if (strValue.contains(",") || strValue.contains("\"") || strValue.contains("\n")) {
                        strValue = "\"" + strValue.replace("\"", "\"\"") + "\"";
                    }
                    stringValues.add(strValue);
                }
            }
            csv.append(String.join(",", stringValues)).append("\n");
        }
        
        String result = csv.toString();
        logger.info("CSV导出完成 - 内容长度: {} chars", result.length());
        
        return result;
    }

    private Map<String, ColumnType> analyzeColumnTypes(List<String> columns, List<List<Object>> rows) {
        Map<String, ColumnType> columnTypes = new HashMap<>();
        
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            ColumnType type = ColumnType.STRING;
            
            // 分析前几行数据来确定类型
            int sampleSize = Math.min(10, rows.size());
            int numericCount = 0;
            int dateCount = 0;
            
            for (int j = 0; j < sampleSize; j++) {
                if (i < rows.get(j).size()) {
                    Object value = rows.get(j).get(i);
                    if (value instanceof Number) {
                        numericCount++;
                    } else if (value != null && isDateLike(value.toString())) {
                        dateCount++;
                    }
                }
            }
            
            if (numericCount >= sampleSize * 0.8) {
                type = ColumnType.NUMERIC;
            } else if (dateCount >= sampleSize * 0.8) {
                type = ColumnType.DATE;
            }
            
            columnTypes.put(column, type);
        }
        
        return columnTypes;
    }

    private boolean isDateLike(String value) {
        // 简单的日期格式检测
        return value.matches("\\d{4}-\\d{2}-\\d{2}.*") || 
               value.matches("\\d{2}/\\d{2}/\\d{4}.*") ||
               value.matches("\\d{4}年\\d{1,2}月\\d{1,2}日.*");
    }

    private List<ChartRecommendation> generateChartRecommendations(Map<String, ColumnType> columnTypes, int rowCount) {
        List<ChartRecommendation> recommendations = new ArrayList<>();
        
        long numericColumns = columnTypes.values().stream().mapToLong(type -> type == ColumnType.NUMERIC ? 1 : 0).sum();
        long categoricalColumns = columnTypes.size() - numericColumns;
        
        // 基于数据特征推荐图表
        if (numericColumns >= 1 && categoricalColumns >= 1) {
            recommendations.add(new ChartRecommendation("BAR", "柱状图", "适合显示分类数据的数值比较"));
            if (rowCount <= 10) {
                recommendations.add(new ChartRecommendation("PIE", "饼图", "适合显示分类数据的占比关系"));
            }
        }
        
        if (numericColumns >= 1) {
            recommendations.add(new ChartRecommendation("LINE", "折线图", "适合显示数值变化趋势"));
        }
        
        recommendations.add(new ChartRecommendation("TABLE", "表格", "原始数据表格显示"));
        
        return recommendations;
    }

    private Map<String, Object> generateStatistics(List<String> columns, List<List<Object>> rows, 
                                                 Map<String, ColumnType> columnTypes) {
        Map<String, Object> statistics = new HashMap<>();
        
        statistics.put("totalRows", rows.size());
        statistics.put("totalColumns", columns.size());
        statistics.put("numericColumns", columnTypes.values().stream().mapToLong(t -> t == ColumnType.NUMERIC ? 1 : 0).sum());
        statistics.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        // 为数值列计算统计信息
        Map<String, Map<String, Object>> columnStats = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            if (columnTypes.get(column) == ColumnType.NUMERIC) {
                Map<String, Object> stats = calculateNumericStats(rows, i);
                columnStats.put(column, stats);
            }
        }
        statistics.put("columnStatistics", columnStats);
        
        return statistics;
    }

    private Map<String, Object> calculateNumericStats(List<List<Object>> rows, int columnIndex) {
        Map<String, Object> stats = new HashMap<>();
        List<Double> values = new ArrayList<>();
        
        for (List<Object> row : rows) {
            if (columnIndex < row.size() && row.get(columnIndex) instanceof Number) {
                values.add(((Number) row.get(columnIndex)).doubleValue());
            }
        }
        
        if (!values.isEmpty()) {
            values.sort(Double::compareTo);
            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            
            stats.put("count", values.size());
            stats.put("sum", sum);
            stats.put("average", sum / values.size());
            stats.put("min", values.get(0));
            stats.put("max", values.get(values.size() - 1));
            stats.put("median", values.get(values.size() / 2));
        }
        
        return stats;
    }

    private ChartData prepareBarChartData(List<String> columns, List<List<Object>> rows, 
                                        String groupByColumn, String valueColumn) {
        ChartData chartData = new ChartData();
        chartData.setType("BAR");
        
        // 简化实现：取前两列作为分组和数值
        if (columns.size() >= 2) {
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            
            int groupIndex = groupByColumn != null ? columns.indexOf(groupByColumn) : 0;
            int valueIndex = valueColumn != null ? columns.indexOf(valueColumn) : 1;
            
            for (List<Object> row : rows) {
                if (groupIndex < row.size() && valueIndex < row.size()) {
                    labels.add(row.get(groupIndex).toString());
                    Object value = row.get(valueIndex);
                    if (value instanceof Number) {
                        values.add(((Number) value).doubleValue());
                    } else {
                        values.add(0.0);
                    }
                }
            }
            
            chartData.setLabels(labels);
            chartData.setValues(values);
        }
        
        return chartData;
    }

    private ChartData preparePieChartData(List<String> columns, List<List<Object>> rows, 
                                        String groupByColumn, String valueColumn) {
        // 饼图数据准备（与柱状图类似）
        return prepareBarChartData(columns, rows, groupByColumn, valueColumn);
    }

    private ChartData prepareLineChartData(List<String> columns, List<List<Object>> rows, 
                                         String groupByColumn, String valueColumn) {
        // 折线图数据准备（与柱状图类似）
        return prepareBarChartData(columns, rows, groupByColumn, valueColumn);
    }

    private ChartData prepareTableData(List<String> columns, List<List<Object>> rows) {
        ChartData chartData = new ChartData();
        chartData.setType("TABLE");
        chartData.setColumns(columns);
        chartData.setRows(rows);
        return chartData;
    }

    // 内部类：可视化分析结果
    public static class VisualizationAnalysis {
        private Map<String, ColumnType> columnTypes;
        private List<ChartRecommendation> chartRecommendations;
        private Map<String, Object> statistics;

        // Getters and Setters
        public Map<String, ColumnType> getColumnTypes() { return columnTypes; }
        public void setColumnTypes(Map<String, ColumnType> columnTypes) { this.columnTypes = columnTypes; }
        
        public List<ChartRecommendation> getChartRecommendations() { return chartRecommendations; }
        public void setChartRecommendations(List<ChartRecommendation> chartRecommendations) { 
            this.chartRecommendations = chartRecommendations; 
        }
        
        public Map<String, Object> getStatistics() { return statistics; }
        public void setStatistics(Map<String, Object> statistics) { this.statistics = statistics; }
    }

    // 内部类：图表推荐
    public static class ChartRecommendation {
        private String type;
        private String name;
        private String description;

        public ChartRecommendation(String type, String name, String description) {
            this.type = type;
            this.name = name;
            this.description = description;
        }

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // 内部类：图表数据
    public static class ChartData {
        private String type;
        private List<String> labels;
        private List<Double> values;
        private List<String> columns;
        private List<List<Object>> rows;

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        
        public List<Double> getValues() { return values; }
        public void setValues(List<Double> values) { this.values = values; }
        
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        
        public List<List<Object>> getRows() { return rows; }
        public void setRows(List<List<Object>> rows) { this.rows = rows; }
    }

    // 枚举：列数据类型
    public enum ColumnType {
        NUMERIC, STRING, DATE
    }
}