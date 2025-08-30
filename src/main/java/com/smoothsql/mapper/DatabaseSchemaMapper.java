package com.smoothsql.mapper;

import com.smoothsql.entity.DatabaseSchema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DatabaseSchemaMapper {
    
    int insert(DatabaseSchema databaseSchema);
    
    List<DatabaseSchema> selectByDatabaseName(@Param("databaseName") String databaseName);
    
    List<DatabaseSchema> selectByTableName(@Param("databaseName") String databaseName, 
                                          @Param("tableName") String tableName);
    
    List<String> selectTableNames(@Param("databaseName") String databaseName);
    
    List<String> selectColumnNames(@Param("databaseName") String databaseName, 
                                  @Param("tableName") String tableName);
    
    int deleteByDatabaseName(@Param("databaseName") String databaseName);
    
    int batchInsert(@Param("schemas") List<DatabaseSchema> schemas);
}