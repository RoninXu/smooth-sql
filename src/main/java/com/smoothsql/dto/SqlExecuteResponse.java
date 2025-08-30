package com.smoothsql.dto;

import com.smoothsql.service.QueryExecutionService.QueryResultData;
import java.time.LocalDateTime;

public class SqlExecuteResponse {
    private boolean success;
    private String message;
    private QueryResultData data;
    private LocalDateTime timestamp;

    public SqlExecuteResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public SqlExecuteResponse(boolean success, String message, QueryResultData data) {
        this();
        this.success = success;
        this.message = message;
        this.data = data;
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

    public QueryResultData getData() {
        return data;
    }

    public void setData(QueryResultData data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}