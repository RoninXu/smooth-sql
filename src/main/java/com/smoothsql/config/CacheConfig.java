package com.smoothsql.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 缓存和异步执行配置
 * 
 * @author Smooth SQL Team
 * @version 2.0
 * @since 2024-08-30
 */
@Configuration
@EnableCaching
@EnableAsync
public class CacheConfig {

    /**
     * 配置缓存管理器
     * 使用内存缓存，适合开发和小规模部署
     * 生产环境建议使用Redis或其他分布式缓存
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // 预定义缓存空间
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "database-schema",      // 数据库结构缓存
            "query-results",        // 查询结果缓存
            "user-permissions",     // 用户权限缓存
            "sql-generation",       // SQL生成缓存
            "query-statistics"      // 查询统计缓存
        ));
        
        return cacheManager;
    }

    /**
     * 配置异步执行器
     * 用于处理耗时的后台任务
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(4);
        
        // 最大线程数
        executor.setMaxPoolSize(8);
        
        // 队列容量
        executor.setQueueCapacity(100);
        
        // 线程名前缀
        executor.setThreadNamePrefix("SmoothSQL-Async-");
        
        // 当池达到最大大小并且队列已满时的处理策略
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}