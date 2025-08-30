package com.smoothsql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Smooth SQL 应用配置属性
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
@Component
@ConfigurationProperties(prefix = "smooth-sql")
public class SmoothSqlProperties {

    private Performance performance = new Performance();
    private Security security = new Security();
    private Export export = new Export();
    private Visualization visualization = new Visualization();

    // Getters and Setters
    public Performance getPerformance() { return performance; }
    public void setPerformance(Performance performance) { this.performance = performance; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Export getExport() { return export; }
    public void setExport(Export export) { this.export = export; }

    public Visualization getVisualization() { return visualization; }
    public void setVisualization(Visualization visualization) { this.visualization = visualization; }

    // 性能配置
    public static class Performance {
        private long queryTimeout = 30000;
        private int maxResultSize = 10000;
        private long cacheTtl = 3600;

        public long getQueryTimeout() { return queryTimeout; }
        public void setQueryTimeout(long queryTimeout) { this.queryTimeout = queryTimeout; }

        public int getMaxResultSize() { return maxResultSize; }
        public void setMaxResultSize(int maxResultSize) { this.maxResultSize = maxResultSize; }

        public long getCacheTtl() { return cacheTtl; }
        public void setCacheTtl(long cacheTtl) { this.cacheTtl = cacheTtl; }
    }

    // 安全配置
    public static class Security {
        private int maxQueriesPerDay = 1000;
        private int passwordMinLength = 8;
        private long sessionTimeout = 7200;

        public int getMaxQueriesPerDay() { return maxQueriesPerDay; }
        public void setMaxQueriesPerDay(int maxQueriesPerDay) { this.maxQueriesPerDay = maxQueriesPerDay; }

        public int getPasswordMinLength() { return passwordMinLength; }
        public void setPasswordMinLength(int passwordMinLength) { this.passwordMinLength = passwordMinLength; }

        public long getSessionTimeout() { return sessionTimeout; }
        public void setSessionTimeout(long sessionTimeout) { this.sessionTimeout = sessionTimeout; }
    }

    // 导出配置
    public static class Export {
        private int maxExportRows = 50000;
        private String allowedFormats = "xlsx,csv,json";

        public int getMaxExportRows() { return maxExportRows; }
        public void setMaxExportRows(int maxExportRows) { this.maxExportRows = maxExportRows; }

        public String getAllowedFormats() { return allowedFormats; }
        public void setAllowedFormats(String allowedFormats) { this.allowedFormats = allowedFormats; }
    }

    // 可视化配置
    public static class Visualization {
        private int maxChartDataPoints = 1000;
        private String defaultChartTypes = "bar,pie,line,table";

        public int getMaxChartDataPoints() { return maxChartDataPoints; }
        public void setMaxChartDataPoints(int maxChartDataPoints) { this.maxChartDataPoints = maxChartDataPoints; }

        public String getDefaultChartTypes() { return defaultChartTypes; }
        public void setDefaultChartTypes(String defaultChartTypes) { this.defaultChartTypes = defaultChartTypes; }
    }
}