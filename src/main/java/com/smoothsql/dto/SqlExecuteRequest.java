package com.smoothsql.dto;

import jakarta.validation.constraints.NotBlank;

public class SqlExecuteRequest {
    @NotBlank(message = "SQL语句不能为空")
    private String sql;
    
    private String database;
    private Integer limit = 100;

    public SqlExecuteRequest() {}

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}