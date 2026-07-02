# DocuMind 部署手册

这份手册面向内部服务器部署。当前版本会把文档、向量索引和 H2 数据库文件保存在持久化目录中；本文不包含 Docker Compose 或外置向量库部署。

## 1. 运行环境

- JDK 17
- Maven 3.8+
- 可访问 DeepSeek API 的网络环境
- 一个可持久保存的数据目录，例如 `/opt/documind/documents`
- 一个低权限系统用户，例如 `documind`

检查 Java 版本：

```bash
java -version
```

macOS 本机开发时可指定 JDK 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## 2. 必填配置

生产环境不要把密钥写进仓库文件。建议使用环境变量或由服务管理器注入。

| 变量 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `DEEPSEEK_API_KEY` | 是 | 由密钥管理器注入 | DeepSeek API Key |
| `DOCUMIND_ADMIN_PASSWORD` | 是 | 由密钥管理器注入 | 管理员密码 |
| `SPRING_PROFILES_ACTIVE` | 是 | `prod` | 生产 profile，避免加载本地开发配置 |
| `DOCUMIND_ADMIN_USERNAME` | 否 | `admin` | 管理员用户名，默认 `admin` |
| `DOCUMIND_USER_USERNAME` | 否 | `reader` | 只读问答账号，不配置则不启用 |
| `DOCUMIND_USER_PASSWORD` | 否 | 由密钥管理器注入 | 只读问答账号密码 |
| `DOCUMIND_USER_KNOWLEDGE_BASES` | 否 | `default,HR` | 普通用户可访问的知识库，默认 `default`，`*` 表示全部 |
| `DOCUMIND_MIN_PASSWORD_LENGTH` | 否 | `12` | 管理员和普通用户密码最小长度，默认 `12`，应用内部最低接受 `8` |
| `DOCUMIND_STALE_DAYS` | 否 | `180` | 文档过期提醒天数 |
| `DOCUMIND_MAX_FILE_SIZE` | 否 | `50MB` | 单个上传文件大小上限，同时用于 Spring multipart 和业务校验 |
| `DOCUMIND_AUDIT_MAX_EVENTS` | 否 | `10000` | 审计日志保留条数，`0` 表示不由应用裁剪 |
| `DOCUMIND_DB_PATH` | 否 | `/opt/documind/documents/.documind-db` | H2 文件数据库路径；生产部署必须放在持久化目录 |
| `DOCUMIND_DB_USERNAME` | 否 | `sa` | H2 数据库用户名 |
| `DOCUMIND_DB_PASSWORD` | 否 | - | H2 数据库密码 |
| `DOCUMIND_JPA_DDL_AUTO` | 否 | `update` | H2 表结构处理方式；首次内网部署用默认值，表结构稳定后可改为 `validate` |
| `DOCUMIND_CHAT_RATE_LIMIT_PER_MINUTE` | 否 | `30` | 每个账号每分钟最多问答次数，`0` 表示关闭 |
| `DOCUMIND_CHAT_STREAM_TIMEOUT_SECONDS` | 否 | `120` | 流式问答 SSE 连接超时，应用内部最低接受 `30` |
| `DOCUMIND_CHAT_STREAM_CORE_POOL_SIZE` | 否 | `4` | 流式问答线程池常驻线程数 |
| `DOCUMIND_CHAT_STREAM_MAX_POOL_SIZE` | 否 | `8` | 流式问答线程池最大线程数 |
| `DOCUMIND_CHAT_STREAM_QUEUE_CAPACITY` | 否 | `100` | 流式问答等待队列容量 |
| `ALLOWED_ORIGINS` | 否 | `https://kb.example.com` | CORS 允许来源 |
| `DOCUMIND_RAG_MAX_RESULTS` | 否 | `3` | 每次问答最多带入的文档片段数 |
| `DOCUMIND_RAG_MIN_SCORE` | 否 | `0.65` | 向量检索最低相似度，值越高越严格 |
| `DOCUMIND_RAG_KEYWORD_MIN_HIT_RATIO` | 否 | `0.25` | 关键词检索最低命中比例，值越高越严格 |
| `DOCUMIND_RAG_RETRIEVAL_POOL_SIZE` | 否 | `50` | 向量检索候选池大小 |
| `DOCUMIND_RAG_CHUNK_SIZE` | 否 | `500` | 文档切分片段长度 |
| `DOCUMIND_RAG_CHUNK_OVERLAP` | 否 | `50` | 相邻片段重叠长度 |
| `DEEPSEEK_BASE_URL` | 否 | `https://api.deepseek.com` | DeepSeek OpenAI 兼容地址 |
| `DEEPSEEK_MODEL` | 否 | `deepseek-v4-flash` | 使用的 DeepSeek 模型 |
| `DEEPSEEK_TIMEOUT_SECONDS` | 否 | `60` | DeepSeek 调用超时，应用内部最低接受 `5` |

`application.yml` 默认激活 `dev`，生产环境必须设置 `SPRING_PROFILES_ACTIVE=prod`。仓库中的 `application-prod.yml` 只保留环境变量占位，不包含可直接用于生产的默认 API Key 或默认密码；缺少 `DEEPSEEK_API_KEY` 或 `DOCUMIND_ADMIN_PASSWORD` 时应用会启动失败。生产环境建议显式设置 `DOCUMIND_DB_PATH`，避免数据库文件落在临时工作目录。

## 3. 构建

在项目根目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn test
mvn package
```

构建产物位于：

```text
target/documind-1.0.0-SNAPSHOT.jar
```

## 4. 首次启动

示例目录：

```bash
mkdir -p /opt/documind/documents
```

启动示例：

```bash
export DEEPSEEK_API_KEY="<deepseek-api-key-from-secret-store>"
export SPRING_PROFILES_ACTIVE=prod
export DOCUMIND_ADMIN_USERNAME=admin
export DOCUMIND_ADMIN_PASSWORD="<admin-password-from-secret-store>"
export DOCUMIND_MIN_PASSWORD_LENGTH=12
export DOCUMIND_STALE_DAYS=180
export DOCUMIND_AUDIT_MAX_EVENTS=10000
export DOCUMIND_DB_PATH=/opt/documind/documents/.documind-db
export DOCUMIND_JPA_DDL_AUTO=update
export DOCUMIND_CHAT_RATE_LIMIT_PER_MINUTE=30
export DOCUMIND_CHAT_STREAM_TIMEOUT_SECONDS=120
export DOCUMIND_CHAT_STREAM_CORE_POOL_SIZE=4
export DOCUMIND_CHAT_STREAM_MAX_POOL_SIZE=8
export DOCUMIND_CHAT_STREAM_QUEUE_CAPACITY=100
export DOCUMIND_USER_KNOWLEDGE_BASES=default
export ALLOWED_ORIGINS=https://kb.example.com
export DOCUMIND_RAG_MAX_RESULTS=3
export DOCUMIND_RAG_MIN_SCORE=0.65
export DEEPSEEK_TIMEOUT_SECONDS=60

java -jar target/documind-1.0.0-SNAPSHOT.jar \
  --server.port=8080 \
  --app.documents-path=/opt/documind/documents
```

访问：

```text
http://服务器地址:8080
```

## 5. 运行检查

存活检查无需登录：

```bash
curl -i http://127.0.0.1:8080/api/v1/health
```

预期返回 `200`，响应体类似：

```json
{"status":"UP","timestamp":"2026-06-04T00:00:00Z","checks":[]}
```

readiness 需要管理员账号：

```bash
curl -u "admin:${DOCUMIND_ADMIN_PASSWORD}" \
  http://127.0.0.1:8080/api/v1/health/readiness
```

重点看：

- `deepseek-config` 是否 `UP`
- `documents-storage` 是否 `UP`
- `rag-index` 是否 `UP`
- `knowledge-bases` 是否 `UP` 或 `DEGRADED`
- `chat-runtime` 是否包含预期的限流、超时和流式线程池参数

`knowledge-bases=DEGRADED` 且 `totalFiles=0` 通常表示还没有上传文档，不代表服务不可用。

## 6. 反向代理建议

对外提供访问时建议放在 Nginx、Caddy 或企业网关后面，并启用 HTTPS。

需要保留：

- `Authorization` header，用于 Basic Auth。
- 流式问答接口 `/api/v1/chat/stream` 的长连接能力。
- 上传接口的请求体大小，需要不小于 `DOCUMIND_MAX_FILE_SIZE`。

Nginx 示例片段：

```nginx
client_max_body_size 50m;
proxy_read_timeout 130s;
proxy_send_timeout 130s;

location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Authorization $http_authorization;
    proxy_buffering off;
}
```

## 7. 数据目录和备份

需要备份的目录：

```text
documents/
```

目录里包含：

- 原始文档文件
- `.documind-vectors.json`：向量索引快照
- `.documind-index-cache.json`：索引缓存
- `.documind-db.*`：H2 数据库文件，包含文件状态、知识缺口、审计和账号数据

旧版本的 `.documind-files.json`、`.documind-gaps.json`、`.documind-audit.log` 会在首次启动时迁移到数据库；迁移后仍建议随目录一起备份。

建议：

- 每天备份 `documents/`
- 备份文件加密保存
- 限制 `documents/` 目录访问权限
- 大量文档场景先在预发布环境测试启动耗时

## 8. 升级流程

1. 在新版本代码上执行 `mvn test`。
2. 备份 `documents/`。
3. 停止旧进程。
4. 替换 JAR。
5. 使用相同环境变量、`app.documents-path` 和 `DOCUMIND_DB_PATH` 启动。
6. 调用 `/api/v1/health/readiness` 检查状态。
7. 用固定问题集执行一次 RAG 质量检查，参考 [RAG_EVALUATION.md](./RAG_EVALUATION.md)。

## 9. 日常运营

管理员可以定期查看：

- `/admin.html`：后台管理页，包含用户权限、知识库文档、知识缺口和审计入口
- `/api/v1/files/status`：每个知识库的文件、索引、过期、缺口统计
- `/api/v1/files/gaps?knowledgeBase=HR`：未命中文档的问题
- `/api/v1/files/faq-draft?knowledgeBase=HR`：根据缺口生成 FAQ 草稿
- `/api/v1/files/{filename}/download?knowledgeBase=HR`：下载原始文档
- `/api/v1/files/gaps/{gapId}?knowledgeBase=HR`：标记知识缺口为已处理
- `/api/v1/files/audit?limit=100`：最近操作记录

用户删除对话记录时，前端会调用 `/api/v1/chat/sessions/{sessionId}` 清理服务端会话记忆。

文档过期提醒不会自动删除文件。负责人需要定期确认过期文档是否仍有效。

## 10. 常见故障

### 启动失败：必须配置 DEEPSEEK_API_KEY

检查环境变量是否注入到实际运行进程：

```bash
echo "$DEEPSEEK_API_KEY"
```

### 启动失败：必须配置 DOCUMIND_ADMIN_USERNAME 和 DOCUMIND_ADMIN_PASSWORD

至少需要配置管理员密码：

```bash
export DOCUMIND_ADMIN_PASSWORD="<admin-password-from-secret-store>"
```

### readiness 显示 documents-storage DOWN

检查文档目录是否存在、是否是目录、运行用户是否可读写：

```bash
ls -ld /opt/documind/documents
```

### 问答没有引用来源

可能原因：

- 当前知识库没有相关文档
- 文档解析失败
- 文档内容和问题相似度不足
- 用户选错知识库

先查：

```bash
curl -u "admin:${DOCUMIND_ADMIN_PASSWORD}" \
  http://127.0.0.1:8080/api/v1/files/status
```

### 上传后回答仍旧

上传成功后应用会刷新索引。若仍旧异常，管理员可手动刷新：

```bash
curl -u "admin:${DOCUMIND_ADMIN_PASSWORD}" \
  -X POST http://127.0.0.1:8080/api/v1/files/refresh
```

### 审计日志增长过快

调整保留条数：

```bash
export DOCUMIND_AUDIT_MAX_EVENTS=5000
```

如果设置为 `0`，应用不会裁剪 `.documind-audit.log`，需要使用外部文件轮转策略。
