package com.smoothsql.controller;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 * 
 * 提供系统健康检查和AI模型测试功能
 * 用于验证DeepSeek模型集成是否正常工作
 * 
 * @author Smooth SQL Team
 * @version 1.0
 * @since 2024-08-30
 */
@RestController
@RequestMapping("/api/v1/test")
@CrossOrigin(origins = "*")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    @Autowired(required = false)
    private ChatLanguageModel chatLanguageModel;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", "Smooth SQL");
        response.put("ai_model", chatLanguageModel != null ? "available" : "unavailable");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ai")
    public ResponseEntity<Map<String, Object>> testAI(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (chatLanguageModel == null) {
                response.put("success", false);
                response.put("message", "AI模型未配置");
                return ResponseEntity.badRequest().body(response);
            }
            
            String query = request.getOrDefault("query", "你好，请用中文回复");
            String aiResponse = chatLanguageModel.generate(query);
            
            response.put("success", true);
            response.put("query", query);
            response.put("response", aiResponse);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "AI调用失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/sql-test")
    public ResponseEntity<Map<String, Object>> testSqlGeneration(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (chatLanguageModel == null) {
                response.put("success", false);
                response.put("message", "AI模型未配置");
                return ResponseEntity.badRequest().body(response);
            }
            
            String naturalQuery = request.getOrDefault("query", "查询用户表中的所有用户信息");
            
            String prompt = String.format(
                "你是SQL专家。请将以下中文查询转换为MySQL SQL语句：\n\n" +
                "查询需求：%s\n" +
                "假设存在以下表：users(id, username, email, created_at), orders(id, user_id, total_amount, status, created_at)\n\n" +
                "只返回SQL语句，不要解释：",
                naturalQuery
            );
            
            String sqlResponse = chatLanguageModel.generate(prompt);
            
            response.put("success", true);
            response.put("natural_query", naturalQuery);
            response.put("generated_sql", sqlResponse.trim());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "SQL生成失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}