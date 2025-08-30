package com.smoothsql.dto;

import com.smoothsql.service.QueryExecutionService.QueryResultData;
import java.time.LocalDateTime;

public class GenerateAndExecuteResponse {
    private boolean success;
    private String message;
    private SqlGenerateResponse.SqlData sqlData;
    private QueryResultData executeData;
    private LocalDateTime timestamp;

    public GenerateAndExecuteResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public GenerateAndExecuteResponse(boolean success, String message, SqlGenerateResponse.SqlData sqlData, QueryResultData executeData) {
        this();
        this.success = success;
        this.message = message;
        this.sqlData = sqlData;
        this.executeData = executeData;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SqlGenerateResponse.SqlData getSqlData() {
        return sqlData;
    }

    public void setSqlData(SqlGenerateResponse.SqlData sqlData) {
        this.sqlData = sqlData;
    }

    public QueryResultData getExecuteData() {
        return executeData;
    }

    public void setExecuteData(QueryResultData executeData) {
        this.executeData = executeData;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}