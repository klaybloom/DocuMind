# DocuMind 安全配置指南

## ⚠️ 重要：API Key 配置

**请勿将 API Key 直接写入 `application.yml` 文件！**

### 方法 1：使用环境变量（推荐）

在启动应用前设置环境变量：

```bash
export DEEPSEEK_API_KEY=your_actual_api_key_here
mvn spring-boot:run
```

或在 IDE 中配置环境变量。

### 方法 2：使用 application-local.yml（推荐）

1. 复制模板文件：
```bash
cp src/main/resources/application-local.yml.template src/main/resources/application-local.yml
```

2. 编辑 `application-local.yml`，填入你的 API Key：
```yaml
app:
  deepseek:
    api-key: REDACTED_DEEPSEEK_API_KEY
```

3. 启动应用（Spring Boot 会自动加载 `application-local.yml`）：
```bash
mvn spring-boot:run
```

**注意**：`application-local.yml` 已添加到 `.gitignore`，不会被提交到 Git。

### 方法 3：使用 Spring Boot 启动参数

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.deepseek.api-key=your_actual_api_key_here"
```

## 🔒 其他安全配置

### CORS 配置

默认只允许 `http://localhost:8080` 访问。如需修改，设置环境变量：

```bash
export ALLOWED_ORIGINS=http://localhost:8080,https://yourdomain.com
```

或在 `application-local.yml` 中配置：
```yaml
app:
  cors:
    allowed-origins: http://localhost:8080,https://yourdomain.com
```

## 📝 获取 DeepSeek API Key

1. 访问 [DeepSeek 平台](https://platform.deepseek.com/)
2. 注册/登录账号
3. 在控制台创建 API Key
4. 按照上述方法配置到应用中
