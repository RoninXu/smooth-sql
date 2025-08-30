package com.smoothsql.mapper;

import com.smoothsql.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {
    
    int insert(User user);
    
    User selectById(@Param("id") Long id);
    
    User selectByUsername(@Param("username") String username);
    
    User selectByUserId(@Param("userId") String userId);
    
    User selectByEmail(@Param("email") String email);
    
    boolean existsByUsername(@Param("username") String username);
    
    boolean existsByEmail(@Param("email") String email);
    
    int updateById(User user);
    
    int updateLastLoginTime(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);
    
    int updatePermissions(@Param("userId") String userId, @Param("permissions") String permissions);
    
    int updateStatus(@Param("userId") String userId, @Param("status") String status);
    
    int deleteById(@Param("id") Long id);
    
    List<User> selectAll(@Param("offset") int offset, @Param("limit") int limit);
    
    long countAll();
    
    List<User> selectByRole(@Param("role") String role);
    
    List<User> selectByStatus(@Param("status") String status);
}