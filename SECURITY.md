# DocuMind 安全配置指南

## ⚠️ 重要：API Key 配置

**请勿将 API Key 直接写入 `application.yml` 文件！**

### 方法 1：使用环境变量（推荐）

在启动应用前设置环境变量：

```bash
export DEEPSEEK_API_KEY=your_actual_api_key_here
export DOCUMIND_ADMIN_PASSWORD=change_this_password
export DOCUMIND_STALE_DAYS=180
mvn spring-boot:run
```

或在 IDE 中配置环境变量。

### 方法 2：使用 application-local.yml（推荐）

1. 复制模板文件：
```bash
cp src/main/resources/application-local.yml.template src/main/resources/application-local.yml
```

2. 编辑 `application-local.yml`，填入你的 API Key 和管理员密码：
```yaml
app:
  deepseek:
    api-key: REDACTED_DEEPSEEK_API_KEY
  security:
    admin-username: admin
    admin-password: change_this_password
```

3. 启动应用，并显式启用 `local` profile：
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**注意**：`application-local.yml` 已添加到 `.gitignore`，不会被提交到 Git。

### 方法 3：使用 Spring Boot 启动参数

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.deepseek.api-key=your_actual_api_key_here --app.security.admin-password=change_this_password"
```

## 🔒 其他安全配置

### 登录和角色

应用使用 Spring Security Basic Auth：

- `ADMIN`：可以聊天，也可以上传、下载、删除、刷新知识库文档和处理知识缺口。
- `USER`：只能聊天，不能管理文档。

默认管理员用户名为 `admin`，密码必须通过 `DOCUMIND_ADMIN_PASSWORD` 或本地配置文件提供。普通用户账号为可选配置：

```bash
export DOCUMIND_USER_USERNAME=user
export DOCUMIND_USER_PASSWORD=user_password
export DOCUMIND_USER_KNOWLEDGE_BASES=default
export DOCUMIND_MIN_PASSWORD_LENGTH=12
```

管理员和普通用户密码默认至少 12 个字符。普通用户账号不能和管理员账号同名。

普通用户默认只能访问 `default` 知识库。允许访问多个知识库时使用英文逗号分隔：

```bash
export DOCUMIND_USER_KNOWLEDGE_BASES=default,HR,Legal
```

如果确实要让普通用户访问所有知识库，可以设置为 `*`。管理员始终可以访问全部知识库。

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

### 文档过期阈值

默认 180 天后标记为可能过期，可通过环境变量调整：

```bash
export DOCUMIND_STALE_DAYS=180
```

这只是提醒，不会自动删除文档。

### 文档内指令

问答提示词会把检索到的资料片段限定为事实材料，而不是系统指令。文档中如果出现“忽略规则”“泄露密钥”“输出内部配置”等内容，模型会被要求忽略这些指令性文本。

这项限制能降低 RAG prompt injection 风险，但不能替代文档准入审核。生产环境仍应限制上传权限，并定期检查外部来源文档。

### 审计记录

应用会把关键操作写入 `documents/.documind-audit.log`：

- 问答请求：账号、知识库、会话 ID、问题长度、是否流式请求、限流记录、清理会话记忆。
- 文档操作：上传、下载、删除、刷新索引。
- 运营操作：生成 FAQ 草稿、处理知识缺口。

审计记录不保存完整问题正文，也不会保存 API Key。`documents/` 目录如果挂载到共享存储或备份系统，需要按内部日志策略控制访问权限。

默认只保留最近 10000 条审计记录，可通过环境变量调整：

```bash
export DOCUMIND_AUDIT_MAX_EVENTS=10000
```

设置为 `0` 表示不在应用内裁剪审计日志，由外部日志系统或文件轮转策略负责。

### 问答频率限制

默认每个账号每分钟最多 30 次问答请求，普通问答超限返回 HTTP 429，流式问答超限返回 SSE `error` 事件。可通过环境变量调整：

```bash
export DOCUMIND_CHAT_RATE_LIMIT_PER_MINUTE=30
```

设置为 `0` 表示关闭。对公网或跨部门部署时不建议关闭。

流式问答使用独立线程池，避免长连接占用通用异步线程。默认配置：

```bash
export DOCUMIND_CHAT_STREAM_TIMEOUT_SECONDS=120
export DOCUMIND_CHAT_STREAM_CORE_POOL_SIZE=4
export DOCUMIND_CHAT_STREAM_MAX_POOL_SIZE=8
export DOCUMIND_CHAT_STREAM_QUEUE_CAPACITY=100
```

DeepSeek 调用默认 60 秒超时，可通过 `DEEPSEEK_TIMEOUT_SECONDS` 调整。反向代理的 read/send timeout 应略高于 `DOCUMIND_CHAT_STREAM_TIMEOUT_SECONDS`。

### 健康检查

- `GET /api/health`：无需登录，只返回整体存活状态和时间，适合负载均衡或进程保活检查。
- `GET /api/health/readiness`：仅管理员可访问，会返回 DeepSeek 配置是否完整、文档目录是否可读写、索引是否初始化、知识库文件统计和问答运行参数。

readiness 不返回 API Key 原文，只返回 `apiKeyConfigured=true/false`；也不返回任何账号密码。

## 📝 获取 DeepSeek API Key

1. 访问 [DeepSeek 平台](https://platform.deepseek.com/)
2. 注册/登录账号
3. 在控制台创建 API Key
4. 按照上述方法配置到应用中
