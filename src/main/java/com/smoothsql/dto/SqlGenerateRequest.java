package com.smoothsql.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public class SqlGenerateRequest {
    @NotBlank(message = "查询内容不能为空")
    private String query;
    
    private String database;
    private Map<String, Object> context;

    public SqlGenerateRequest() {}

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}