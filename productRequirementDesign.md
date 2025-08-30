# Smooth SQL - 自然语言转SQL应用产品文档

## 1. 产品概述

### 1.1 产品简介
Smooth SQL 是一个基于人工智能的自然语言到SQL查询转换应用，允许用户使用自然语言描述数据查询需求，自动生成对应的SQL语句。该应用旨在降低数据查询门槛，提高非技术用户的数据访问效率。

### 1.2 核心价值
- **降低技术门槛**：无需掌握SQL语法，通过自然语言即可查询数据
- **提高工作效率**：快速生成复杂SQL查询，减少编写时间
- **智能理解**：基于Langchain4j的大语言模型，准确理解用户意图
- **安全可靠**：支持多种数据库，提供查询结果验证和安全控制

### 1.3 目标用户
- 数据分析师
- 业务分析人员
- 产品经理
- 运营人员
- 其他需要进行数据查询但不熟悉SQL的用户

## 2. 核心功能

### 2.1 自然语言解析
- 支持中文和英文自然语言输入
- 智能识别查询意图（查询、统计、聚合等）
- 自动提取关键信息（表名、字段名、条件等）
- 处理复杂的多表关联查询需求

### 2.2 SQL生成与优化
- 根据自然语言生成标准SQL语句
- 支持SELECT、JOIN、WHERE、GROUP BY、ORDER BY等复杂语法
- 自动优化查询性能
- 提供SQL语句解释和说明

### 2.3 数据库支持
- 主要支持MySQL数据库
- 可扩展支持PostgreSQL、SQLite等其他数据库
- 自动获取数据库结构信息
- 支持多数据源配置

### 2.4 查询结果展示
- 直观的表格形式展示查询结果
- 支持结果导出（CSV、Excel等格式）
- 提供数据可视化图表
- 查询历史记录管理

### 2.5 安全与权限控制
- 用户权限管理
- SQL注入防护
- 敏感数据脱敏
- 查询日志审计

## 3. 技术架构

### 3.1 技术栈
- **后端框架**: Spring Boot 3.1.2
- **编程语言**: Java 17
- **AI框架**: Langchain4j
- **数据库**: MySQL 8.0+
- **ORM框架**: MyBatis
- **构建工具**: Maven 3.8+
- **开发工具**: Spring Boot DevTools

### 3.2 系统架构图
```
┌─────────────────────────────────────────────────────────────┐
│                    前端界面层                                 │
│                 (Web UI / API)                            │
├─────────────────────────────────────────────────────────────┤
│                    控制器层                                  │
│              (REST Controllers)                           │
├─────────────────────────────────────────────────────────────┤
│                    业务逻辑层                                │
│    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│    │自然语言处理服务│  │  SQL生成服务 │  │  查询执行服务 │     │
│    │(NLP Service)│  │(SQL Service)│  │(Query Service)│     │
│    └─────────────┘  └─────────────┘  └─────────────┘     │
├─────────────────────────────────────────────────────────────┤
│                    AI集成层                                 │
│              (Langchain4j Integration)                    │
├─────────────────────────────────────────────────────────────┤
│                    数据访问层                               │
│                   (MyBatis)                             │
├─────────────────────────────────────────────────────────────┤
│                    数据库层                                 │
│                  (MySQL Database)                        │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 核心组件设计

#### 3.3.1 自然语言处理模块
- **NaturalLanguageProcessor**: 自然语言预处理
- **IntentRecognizer**: 意图识别服务
- **EntityExtractor**: 实体提取服务
- **ContextManager**: 上下文管理

#### 3.3.2 SQL生成模块
- **SqlGenerator**: SQL生成核心引擎
- **QueryOptimizer**: 查询优化器
- **SchemaAnalyzer**: 数据库结构分析器
- **TemplateManager**: SQL模板管理器

#### 3.3.3 查询执行模块
- **QueryExecutor**: 查询执行器
- **ResultProcessor**: 结果处理器
- **SecurityValidator**: 安全验证器
- **CacheManager**: 查询缓存管理器

## 4. API接口设计

### 4.1 核心接口

#### 4.1.1 自然语言转SQL接口
```http
POST /api/v1/sql/generate
Content-Type: application/json

{
  "query": "查询订单表中昨天的所有订单信息",
  "database": "ecommerce",
  "context": {
    "userId": "user123",
    "sessionId": "session456"
  }
}
```

响应：
```json
{
  "success": true,
  "data": {
    "sql": "SELECT * FROM orders WHERE DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)",
    "explanation": "查询orders表中创建时间为昨天的所有记录",
    "tables": ["orders"],
    "confidence": 0.95
  },
  "timestamp": "2024-01-01T10:00:00Z"
}
```

#### 4.1.2 SQL执行接口
```http
POST /api/v1/sql/execute
Content-Type: application/json

{
  "sql": "SELECT * FROM orders WHERE DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)",
  "database": "ecommerce",
  "limit": 100
}
```

响应：
```json
{
  "success": true,
  "data": {
    "columns": ["id", "customer_id", "total_amount", "created_at"],
    "rows": [
      [1, 123, 299.99, "2024-01-01T09:30:00Z"],
      [2, 124, 159.50, "2024-01-01T14:20:00Z"]
    ],
    "totalCount": 2,
    "executionTime": 150
  }
}
```

#### 4.1.3 数据库结构查询接口
```http
GET /api/v1/database/{databaseName}/schema
```

#### 4.1.4 查询历史接口
```http
GET /api/v1/history?page=0&size=10&userId=user123
```

### 4.2 数据模型

#### 4.2.1 查询记录模型
```java
@Entity
@Table(name = "query_history")
public class QueryHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "natural_query", columnDefinition = "TEXT")
    private String naturalQuery;
    
    @Column(name = "generated_sql", columnDefinition = "TEXT")
    private String generatedSql;
    
    @Column(name = "database_name")
    private String databaseName;
    
    @Column(name = "execution_time")
    private Long executionTime;
    
    @Column(name = "result_count")
    private Integer resultCount;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "status")
    private QueryStatus status;
}
```

#### 4.2.2 数据库结构模型
```java
@Entity
@Table(name = "database_schema")
public class DatabaseSchema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "database_name")
    private String databaseName;
    
    @Column(name = "table_name")
    private String tableName;
    
    @Column(name = "column_name")
    private String columnName;
    
    @Column(name = "column_type")
    private String columnType;
    
    @Column(name = "is_nullable")
    private Boolean isNullable;
    
    @Column(name = "column_comment")
    private String columnComment;
}
```

## 5. 部署与配置

### 5.1 环境要求
- **Java**: OpenJDK 17 或更高版本
- **Maven**: 3.8+ 
- **MySQL**: 8.0+
- **内存**: 最小2GB，推荐4GB+
- **磁盘**: 最小1GB可用空间

### 5.2 数据库配置
1. 创建MySQL数据库：
```sql
CREATE DATABASE smoothsql DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 配置application.yml：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/smoothsql?useSSL=false&serverTimezone=UTC
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
# MyBatis配置
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.smoothsql.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

# Langchain4j配置
langchain4j:
  api-key: your_api_key
  model: gpt-3.5-turbo
  max-tokens: 2000
```

### 5.3 编译与运行
```bash
# 编译项目
mvn clean compile

# 运行应用
mvn spring-boot:run

# 打包部署
mvn clean package
java -jar target/smooth-sql-0.0.1-SNAPSHOT.jar
```

### 5.4 Docker部署
```dockerfile
FROM openjdk:17-jre-slim

COPY target/smooth-sql-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## 6. 开发计划

### 6.1 第一阶段（MVP）
- [ ] 基础自然语言处理功能
- [ ] 简单SQL生成（SELECT查询）
- [ ] MySQL数据库集成
- [ ] REST API接口开发
- [ ] 基础Web界面

### 6.2 第二阶段（功能增强）
- [ ] 复杂查询支持（JOIN、聚合等）
- [ ] 查询结果可视化
- [ ] 查询历史管理
- [ ] 用户权限系统
- [ ] 性能优化

### 6.3 第三阶段（高级功能）
- [ ] 多数据库支持
- [ ] 智能查询推荐
- [ ] 查询性能分析
- [ ] 企业级安全功能
- [ ] 云原生部署支持

## 7. 预期收益

### 7.1 用户价值
- 减少SQL学习成本，提高数据访问效率
- 降低查询错误率，提升数据查询准确性
- 支持复杂业务查询，满足多样化需求

### 7.2 技术价值
- 探索AI在企业数据查询中的应用
- 积累自然语言处理经验
- 构建可扩展的AI应用架构

### 7.3 商业价值
- 为企业提供智能数据查询解决方案
- 降低数据分析门槛，提升业务决策效率
- 可作为SaaS产品提供给中小企业使用