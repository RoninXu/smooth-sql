package com.smoothsql.controller;

import com.smoothsql.entity.QueryHistory;
import com.smoothsql.mapper.QueryHistoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/history")
@CrossOrigin(origins = "*")
public class HistoryController {

    @Autowired
    private QueryHistoryMapper queryHistoryMapper;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getQueryHistory(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        try {
            int offset = page * size;
            
            List<QueryHistory> histories;
            int totalCount;
            
            if (userId != null && !userId.isEmpty()) {
                histories = queryHistoryMapper.selectByUserId(userId, offset, size);
                totalCount = queryHistoryMapper.countByUserId(userId);
            } else {
                histories = queryHistoryMapper.selectAll(offset, size);
                totalCount = histories.size(); // 简化处理，实际应该有总数查询
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", histories);
            response.put("pagination", Map.of(
                "page", page,
                "size", size,
                "total", totalCount,
                "totalPages", (int) Math.ceil((double) totalCount / size)
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取查询历史失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getQueryHistoryById(@PathVariable Long id) {
        try {
            QueryHistory history = queryHistoryMapper.selectById(id);
            
            Map<String, Object> response = new HashMap<>();
            if (history != null) {
                response.put("success", true);
                response.put("data", history);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "查询记录不存在");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取查询记录失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteQueryHistory(@PathVariable Long id) {
        try {
            int deleted = queryHistoryMapper.deleteById(id);
            
            Map<String, Object> response = new HashMap<>();
            if (deleted > 0) {
                response.put("success", true);
                response.put("message", "删除成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "查询记录不存在");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "删除查询记录失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}