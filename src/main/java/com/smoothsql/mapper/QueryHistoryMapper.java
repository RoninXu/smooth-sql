package com.smoothsql.mapper;

import com.smoothsql.entity.QueryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface QueryHistoryMapper {
    
    int insert(QueryHistory queryHistory);
    
    QueryHistory selectById(@Param("id") Long id);
    
    List<QueryHistory> selectByUserId(@Param("userId") String userId, 
                                     @Param("offset") int offset, 
                                     @Param("limit") int limit);
    
    int countByUserId(@Param("userId") String userId);
    
    List<QueryHistory> selectAll(@Param("offset") int offset, @Param("limit") int limit);
    
    int updateById(QueryHistory queryHistory);
    
    int deleteById(@Param("id") Long id);
    
    // 新增方法 - 第二阶段功能
    List<QueryHistory> selectByUserIdWithPagination(@Param("params") Map<String, Object> params);
    
    long countByUserId(@Param("userId") String userId, 
                      @Param("databaseName") String databaseName, 
                      @Param("status") String status);
    
    long countByUserIdAndDateRange(@Param("userId") String userId, 
                                  @Param("startDate") LocalDateTime startDate, 
                                  @Param("endDate") LocalDateTime endDate);
    
    long countByUserIdAndStatusAndDateRange(@Param("userId") String userId, 
                                           @Param("status") String status,
                                           @Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    Map<String, Long> countByDatabaseAndDateRange(@Param("userId") String userId, 
                                                 @Param("startDate") LocalDateTime startDate, 
                                                 @Param("endDate") LocalDateTime endDate);
    
    Map<String, Long> countByDayAndDateRange(@Param("userId") String userId, 
                                            @Param("startDate") LocalDateTime startDate, 
                                            @Param("endDate") LocalDateTime endDate);
    
    Double averageExecutionTime(@Param("userId") String userId, 
                               @Param("startDate") LocalDateTime startDate, 
                               @Param("endDate") LocalDateTime endDate);
    
    List<QueryHistory> selectByUserIdAndDateRange(@Param("userId") String userId, 
                                                 @Param("startDate") LocalDateTime startDate, 
                                                 @Param("endDate") LocalDateTime endDate);
    
    List<QueryHistory> selectRecentSuccessful(@Param("userId") String userId, 
                                             @Param("databaseName") String databaseName, 
                                             @Param("limit") int limit);
    
    List<QueryHistory> selectPopularQueries(@Param("databaseName") String databaseName, 
                                           @Param("limit") int limit);
    
    int updateFavoriteStatus(@Param("id") Long id, 
                            @Param("userId") String userId, 
                            @Param("isFavorite") boolean isFavorite);
    
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);
}