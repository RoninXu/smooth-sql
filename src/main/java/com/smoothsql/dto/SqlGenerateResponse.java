package com.smoothsql.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SqlGenerateResponse {
    private boolean success;
    private SqlData data;
    private LocalDateTime timestamp;
    private String message;

    public SqlGenerateResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public SqlGenerateResponse(boolean success, SqlData data) {
        this();
        this.success = success;
        this.data = data;
    }

    public SqlGenerateResponse(boolean success, String message) {
        this();
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public SqlData getData() {
        return data;
    }

    public void setData(SqlData data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class SqlData {
        private String sql;
        private String explanation;
        private List<String> tables;
        private Double confidence;

        public SqlData() {}

        public SqlData(String sql, String explanation, List<String> tables, Double confidence) {
            this.sql = sql;
            this.explanation = explanation;
            this.tables = tables;
            this.confidence = confidence;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public List<String> getTables() {
            return tables;
        }

        public void setTables(List<String> tables) {
            this.tables = tables;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }
}