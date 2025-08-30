# Smooth SQL - 自然语言转SQL应用

## 项目概述
Smooth SQL 是一个基于Spring Boot和Langchain4j的自然语言转SQL应用，允许用户使用中文自然语言查询数据库。

## 第一阶段MVP功能已完成

### ✅ 已实现的功能
1. **项目基础架构**
   - Spring Boot 3.1.2 + Java 17
   - MyBatis作为ORM框架
   - MySQL数据库支持
   - Langchain4j集成

2. **核心业务模块**
   - 自然语言处理服务 (NaturalLanguageService)
   - SQL生成服务 (SqlGenerationService)
   - 查询执行服务 (QueryExecutionService)

3. **REST API接口**
   - `/api/v1/sql/generate` - 自然语言转SQL
   - `/api/v1/sql/execute` - 执行SQL查询
   - `/api/v1/sql/generate-and-execute` - 一键生成并执行
   - `/api/v1/history` - 查询历史管理

4. **数据模型**
   - QueryHistory - 查询历史记录
   - DatabaseSchema - 数据库结构信息
   - 完整的DTO和响应模型

5. **安全特性**
   - SQL注入防护
   - 仅支持SELECT查询
   - 结果数量限制

6. **监控和日志**
   - 详细的执行日志记录
   - 性能监控（执行时间统计）
   - 全局异常处理
   - 分级日志配置

## 快速开始

### 环境要求
- Java 17+
- Maven 3.8+
- MySQL 8.0+

### 配置步骤
1. **创建数据库**
```sql
CREATE DATABASE smoothsql DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **配置DeepSeek API Key**
```bash
export OPENAI_API_KEY=your_deepseek_api_key_here
```
注：项目已配置使用DeepSeek模型，API Key配置在environment变量中。

3. **更新数据库连接配置**
编辑 `src/main/resources/application.yml` 中的数据库连接信息。

4. **初始化数据库**
执行 `src/main/resources/sql/schema.sql` 中的SQL脚本。

5. **启动应用**
```bash
mvn spring-boot:run
```

应用将在 http://localhost:8080 启动。

## API使用示例

### 测试DeepSeek AI连接
```bash
curl -X GET http://localhost:8080/api/v1/test/health
```

### 测试AI模型
```bash
curl -X POST http://localhost:8080/api/v1/test/ai \
  -H "Content-Type: application/json" \
  -d '{"query": "你好，请介绍一下自己"}'
```

### 生成SQL
```bash
curl -X POST http://localhost:8080/api/v1/sql/generate \
  -H "Content-Type: application/json" \
  -d '{
    "query": "查询用户表中的所有用户信息",
    "database": "demo"
  }'
```

### 一键查询
```bash
curl -X POST http://localhost:8080/api/v1/sql/generate-and-execute \
  -H "Content-Type: application/json" \
  -d '{
    "query": "查询订单表中昨天的所有订单",
    "database": "demo"
  }'
```

## 支持的自然语言示例
- "查询用户表中的所有用户"
- "统计订单表中今天的订单数量"
- "查找价格大于100的产品"
- "按分类统计产品数量"

## 项目结构
```
src/
├── main/
│   ├── java/com/smoothsql/
│   │   ├── controller/     # REST控制器（含详细日志）
│   │   ├── service/        # 业务服务层（含性能监控）
│   │   ├── entity/         # 数据实体
│   │   ├── mapper/         # MyBatis映射器
│   │   ├── dto/           # 数据传输对象
│   │   └── config/        # 配置类（含全局异常处理）
│   └── resources/
│       ├── mapper/        # MyBatis XML映射文件
│       ├── sql/           # 数据库脚本
│       └── application.yml # 应用配置（含日志配置）
└── logs/                  # 日志文件目录
    └── smooth-sql.log     # 应用日志文件
```

## 日志和监控

### 日志级别配置
- 应用程序日志：INFO级别
- 数据库操作：DEBUG级别  
- AI模型调用：INFO级别
- 性能统计：自动记录执行时间

### 监控指标
- SQL生成时间（NLP分析 + 规则引擎 + AI优化）
- SQL执行时间（数据库查询时间）
- 查询成功率和错误统计
- AI模型调用成功率

## 下一步计划
- [ ] 添加前端界面
- [ ] 增加更复杂的SQL支持（JOIN、聚合等）
- [ ] 优化自然语言理解准确性
- [ ] 添加查询结果可视化
- [ ] 用户权限和多数据源支持