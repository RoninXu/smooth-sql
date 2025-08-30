# Smooth SQL 安全配置指南

## 🔐 环境变量配置

为了确保API密钥和敏感信息的安全，本项目使用环境变量进行配置。

### 1. 创建环境变量文件

复制 `.env.example` 文件为 `.env`：

```bash
cp .env.example .env
```

### 2. 配置必要的环境变量

编辑 `.env` 文件，填入真实的配置值：

```bash
# 数据库配置
DATABASE_URL=jdbc:mysql://localhost:3307/smoothsql?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DATABASE_USERNAME=root
DATABASE_PASSWORD=your-database-password

# 管理员账户配置
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your-secure-admin-password

# AI 模型配置
OPENAI_API_KEY=your-openai-api-key-here
AI_MODEL_NAME=deepseek-chat
AI_MAX_TOKENS=2000
AI_TEMPERATURE=0.1
```

### 3. 系统环境变量设置

#### Windows 设置
```cmd
# 临时设置（当前会话有效）
set DATABASE_PASSWORD=your-password
set OPENAI_API_KEY=your-api-key

# 永久设置（需要重启）
setx DATABASE_PASSWORD "your-password"
setx OPENAI_API_KEY "your-api-key"
```

#### Linux/MacOS 设置
```bash
# 临时设置（当前会话有效）
export DATABASE_PASSWORD=your-password
export OPENAI_API_KEY=your-api-key

# 永久设置（添加到 ~/.bashrc 或 ~/.zshrc）
echo "export DATABASE_PASSWORD=your-password" >> ~/.bashrc
echo "export OPENAI_API_KEY=your-api-key" >> ~/.bashrc
source ~/.bashrc
```

## 🚀 部署环境配置

### Docker 部署
```bash
docker run -d \
  -e DATABASE_PASSWORD=your-password \
  -e OPENAI_API_KEY=your-api-key \
  -e ADMIN_PASSWORD=your-admin-password \
  -p 8080:8080 \
  smooth-sql:latest
```

### Docker Compose
```yaml
version: '3.8'
services:
  smooth-sql:
    image: smooth-sql:latest
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=jdbc:mysql://mysql:3306/smoothsql?useSSL=false&serverTimezone=UTC
      - DATABASE_USERNAME=root
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - ADMIN_PASSWORD=${ADMIN_PASSWORD}
    depends_on:
      - mysql
  
  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=${DATABASE_PASSWORD}
      - MYSQL_DATABASE=smoothsql
    volumes:
      - mysql_data:/var/lib/mysql

volumes:
  mysql_data:
```

### Kubernetes 部署
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: smooth-sql-secrets
type: Opaque
data:
  database-password: <base64-encoded-password>
  openai-api-key: <base64-encoded-api-key>
  admin-password: <base64-encoded-admin-password>

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: smooth-sql
spec:
  replicas: 3
  selector:
    matchLabels:
      app: smooth-sql
  template:
    metadata:
      labels:
        app: smooth-sql
    spec:
      containers:
      - name: smooth-sql
        image: smooth-sql:latest
        ports:
        - containerPort: 8080
        env:
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: smooth-sql-secrets
              key: database-password
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: smooth-sql-secrets
              key: openai-api-key
        - name: ADMIN_PASSWORD
          valueFrom:
            secretKeyRef:
              name: smooth-sql-secrets
              key: admin-password
```

## 🛡️ 安全最佳实践

### 1. API 密钥管理
- **不要**将API密钥直接写在代码中
- **不要**将API密钥提交到版本控制系统
- 使用环境变量或密钥管理服务
- 定期轮换API密钥

### 2. 数据库安全
- 使用强密码
- 限制数据库访问权限
- 启用SSL连接
- 定期备份数据

### 3. 应用安全
- 更改默认管理员密码
- 启用HTTPS
- 配置防火墙
- 定期更新依赖

### 4. 生产环境配置
```yaml
# application-prod.yml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 50
      connection-timeout: 20000
      
  security:
    require-ssl: true
    user:
      name: ${ADMIN_USERNAME}
      password: ${ADMIN_PASSWORD}

logging:
  level:
    com.smoothsql: WARN
    org.springframework.security: INFO
    root: WARN
  file:
    name: /var/log/smooth-sql/application.log
```

## ⚠️ 重要提醒

1. **永远不要**将 `.env` 文件提交到Git仓库
2. 确保 `.gitignore` 文件包含所有敏感文件
3. 在生产环境中使用强密码
4. 定期检查和更新安全配置
5. 监控应用日志中的异常活动

## 🔍 验证配置

启动应用前，可以通过以下命令验证环境变量是否正确设置：

```bash
# Windows
echo %DATABASE_PASSWORD%
echo %OPENAI_API_KEY%

# Linux/MacOS
echo $DATABASE_PASSWORD
echo $OPENAI_API_KEY
```

## 📞 支持

如果在配置过程中遇到问题，请：
1. 检查环境变量是否正确设置
2. 确认网络连接和服务可用性
3. 查看应用日志获取详细错误信息
4. 参考项目文档或联系技术支持