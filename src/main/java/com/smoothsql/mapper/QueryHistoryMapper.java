package com.smoothsql.mapper;

import com.smoothsql.entity.QueryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

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
}