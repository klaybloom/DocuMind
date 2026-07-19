# AGENTS.md

面向 AI 编码助手的项目指南。本文件描述 DocuMind 当前代码的运行方式、依赖、架构与已知限制。改动代码前以源码为准，文档只作为入口。

## 项目简介

DocuMind 是一个基于 RAG 的智能文档问答助手。用户上传文档后，应用会保存原始文件、记录文件元数据、切分文档、生成本地 embedding，并建立检索索引。提问时先检索当前知识库的相关片段，再让 LLM 基于片段回答；没有命中文档时会使用通用提示词回答，并记录知识缺口。

## 技术栈

- **语言/运行时**: Java 17
- **框架**: Spring Boot 3.4.5，单体 Maven 项目，`jar` 打包
- **RAG 框架**: LangChain4j 1.16.3，部分 parser / embedding 依赖为 1.16.3-beta26
- **嵌入模型**: `AllMiniLmL6V2EmbeddingModel`，本地运行，无需外部 embedding 服务
- **大语言模型**: DeepSeek API，走 OpenAI 兼容接口
- **关系数据库**: MySQL + Spring Data JPA，Flyway 管理 schema
- **向量存储**: Qdrant，通过 gRPC `6334` 读写；HTTP `6333/healthz` 用于启动检查
- **前端**: 原生 HTML/CSS/JS，位于 `src/main/resources/static/`

## 构建与运行

启动入口：`com.documind.DocuMindApplication`，默认端口 `8080`，访问 `http://localhost:8080`。

### 关键前置条件

1. 必须用 JDK 17 编译运行。`pom.xml` 设置 `java.version=17`。
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 17)
   ```
2. 必须配置 DeepSeek API Key，否则 LLM Bean 初始化会失败。
3. 必须配置管理员密码，否则用户初始化会失败。
4. 生产部署必须为 MySQL、Redis、Qdrant 和原始文档配置持久化。

### 必填配置

推荐用环境变量注入：

```bash
export DEEPSEEK_API_KEY="<deepseek-api-key-from-secret-store>"
export DOCUMIND_ADMIN_PASSWORD="<admin-password-from-secret-store>"
```

本地开发也可以复制模板：

```bash
cp src/main/resources/application-local.yml.template src/main/resources/application-local.yml
```

注意：`application-local.yml` 不会自动启用，需要显式设置 `-Dspring.profiles.active=local` 或在 IDE 的 Active profiles 填 `local`。

### 常用命令

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn compile
mvn test
mvn package
mvn spring-boot:run
```

当前测试基线：`mvn test` 应通过 89 个测试。

## 主要配置

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `DEEPSEEK_API_KEY` | 无 | DeepSeek API Key，必须配置 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek OpenAI 兼容接口地址 |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | LLM 模型名 |
| `DOCUMIND_ADMIN_USERNAME` | `admin` | 管理员账号 |
| `DOCUMIND_ADMIN_PASSWORD` | 无 | 管理员密码，必须配置 |
| `DOCUMIND_USER_USERNAME` | 无 | 普通用户账号，不配置则不启用 |
| `DOCUMIND_USER_PASSWORD` | 无 | 普通用户密码 |
| `DOCUMIND_USER_KNOWLEDGE_BASES` | `default` | 普通用户可访问知识库，`,` 分隔，`*` 表示全部 |
| `DOCUMIND_MIN_PASSWORD_LENGTH` | `12` | 密码最小长度，代码内部最低接受 8 |
| `DOCUMIND_MYSQL_URL` | `jdbc:mysql://127.0.0.1:3306/documind...` | MySQL JDBC URL |
| `DOCUMIND_QDRANT_HOST` | `127.0.0.1` | Qdrant gRPC 主机 |
| `DOCUMIND_QDRANT_GRPC_PORT` | `6334` | Qdrant gRPC 端口 |
| `DOCUMIND_QDRANT_HEALTH_URL` | `http://127.0.0.1:6333/healthz` | Qdrant HTTP 健康检查地址 |
| `DOCUMIND_QDRANT_COLLECTION` | `documind-segments` | 项目专用 Qdrant collection |
| `DOCUMIND_QDRANT_REBUILD` | `false` | 启动时清空项目 collection 并从原始文档重建 |
| `DOCUMIND_MAX_FILE_SIZE` | `50MB` | 上传大小上限 |
| `ALLOWED_ORIGINS` | `http://localhost:8080` | CORS 来源 |
| `DOCUMIND_CHAT_RATE_LIMIT_PER_MINUTE` | `30` | 每账号每分钟问答次数，`0` 表示关闭 |
| `DOCUMIND_RAG_MAX_RESULTS` | `3` | 最终带入回答的片段数 |
| `DOCUMIND_RAG_RETRIEVAL_POOL_SIZE` | `50` | 向量检索候选池大小 |
| `DOCUMIND_RAG_MIN_SCORE` | `0.65` | 向量检索最低相似度 |
| `DOCUMIND_RAG_KEYWORD_MIN_HIT_RATIO` | `0.25` | 关键词检索最低命中比例 |
| `DOCUMIND_RAG_CHUNK_SIZE` | `500` | 文档切分大小 |
| `DOCUMIND_RAG_CHUNK_OVERLAP` | `50` | 文档切分重叠 |

安全要求：不要把 API Key 或生产密码写进受版本控制的文件。`application-local.yml` 已被忽略，模板文件不能填真实密钥。

## 架构概览

```text
controller/   ChatController  FileController  AuthController  HealthController
service/      RagService  DocumentService  AuditService  HealthService
              RateLimitService  PromptTemplateService
              UserAccountInitializer  DatabaseUserDetailsService
              KnowledgeBaseAccessService  JsonMigrationService
config/       LangChainConfig  SecurityConfig  WebConfig  ChatExecutionConfig
repository/   DocumentFileRepository  KnowledgeGapRepository
              AuditEventRepository  UserAccountRepository
model/        DocumentFileEntity  KnowledgeGapEntity
              AuditEventEntity  UserAccount
dto/          请求、响应、健康检查、检索调试、来源引用等 DTO
```

## 核心流程

### 索引

- `RagService.init()` 先确认 Qdrant 健康和 collection；首次创建 collection 或 `DOCUMIND_QDRANT_REBUILD=true` 时从原始文档重建。
- `refreshIndex()` 会遍历数据库中的文档记录，按扩展名选择 parser，使用 `DocumentSplitters.recursive(chunkSize, chunkOverlap)` 切分，并把新增或待索引文档写入 Qdrant。
- 上传文件后会刷新索引；删除文件后会调用 `removeDocument()` 移除对应 chunk；单文件重新索引用 `reindexDocument()`。

### 问答

- `RagService.ask()` 和 `askStream()` 都按知识库检索。
- 检索包括向量检索和关键词补充匹配。
- 命中片段时使用文档问答 prompt；未命中时使用通用 prompt，并记录知识缺口。
- 活跃会话窗口由 Redis 保存，完整会话历史由 MySQL 保存；单个会话使用 `MessageWindowChatMemory` 保留最近 10 条消息。

### 用户与权限

- 用户账号存入 H2 数据库。
- 启动时 `UserAccountInitializer` 用环境变量创建或更新管理员账号，可选创建普通用户账号。
- 管理员可通过 `admin.html` 管理用户、知识库权限、文件、索引、审计和健康 readiness。
- 普通用户只能访问用户表中允许的知识库并进行问答；启动环境变量只负责初始化默认账号。

### 审计和健康检查

- 审计事件写入数据库，不再写 JSONL 文件。
- `/api/v1/health` 是公开 liveness。
- `/api/v1/health/readiness` 需要管理员权限，会检查 DeepSeek 配置、文档目录、索引、知识库状态和问答运行参数。

## HTTP 接口

### 认证

- `GET /api/v1/auth/me`

### 问答

- `POST /api/v1/chat`
- `POST /api/v1/chat/stream`
- `DELETE /api/v1/chat/sessions/{sessionId}`

### 文件与知识库

- `POST /api/v1/files/upload`
- `GET /api/v1/files/list`
- `GET /api/v1/files/knowledge-bases`
- `GET /api/v1/files/status`
- `POST /api/v1/files/refresh`
- `GET /api/v1/files/{filename}/download`
- `DELETE /api/v1/files/{filename}`
- `POST /api/v1/files/{filename}/reindex`

### 知识缺口与审计

- `GET /api/v1/files/gaps`
- `DELETE /api/v1/files/gaps/{gapId}`
- `GET /api/v1/files/faq-draft`
- `GET /api/v1/files/audit`

### 后台用户管理

- `GET /api/v1/admin/users`
- `POST /api/v1/admin/users`
- `PUT /api/v1/admin/users/{id}`
- `PUT /api/v1/admin/users/{id}/password`

### 健康检查

- `GET /api/v1/health`
- `GET /api/v1/health/readiness`

当前接口都挂在 `/api/v1/` 下。

## 支持的文档格式

后端白名单在 `DocumentService.ALLOWED_EXTENSIONS`，前端上传限制在 `index.html`。

| 格式 | 扩展名 | 解析器 |
|------|--------|--------|
| PDF | `.pdf` | `ApachePdfBoxDocumentParser` |
| 文本 | `.txt` | `TextDocumentParser` |
| Word | `.doc` `.docx` | `ApachePoiDocumentParser` |
| PPT | `.ppt` `.pptx` | `ApachePoiDocumentParser` |
| Excel | `.xls` `.xlsx` | `ApachePoiDocumentParser` |

新增格式时至少同步三处：

1. `DocumentService.ALLOWED_EXTENSIONS`
2. `RagService.loadDocument()`
3. 前端 `index.html` 的 `accept` 和提示文案

## 部署状态

项目已有：

- `Dockerfile`
- `.dockerignore`
- GitHub Actions CI：`.github/workflows/ci.yml`
- GitHub Actions Release：`.github/workflows/release.yml`
- 部署手册：`docs/DEPLOYMENT.md`

当前可使用 Qdrant 共享向量索引，但原始文档仍在本地文件系统；多实例部署前还需要对象存储、分布式任务/锁和上传一致性方案。

## 已知限制

- MySQL、Redis 和 Qdrant 都需要独立持久化与备份策略。
- Qdrant collection 的容量、召回质量和重建耗时仍需要基于真实文档集压测。
- `AllMiniLmL6V2EmbeddingModel` 对中文语义检索不是最佳选择，中文知识库需要用评测集验证召回质量。
- Office 文档解析依赖 POI，纯数字 Excel 或复杂格式文档的抽取质量有限。
- 前端入口已改为 ES Module，`app.js` 已接入 `api.js` 和 `utils.js`；目前仍是原生静态前端，尚未引入 Vite/Webpack 这类打包器。
- 没有 OpenAPI/Swagger。
- 没有 `application-prod.yml`。
- 没有 Flyway/Liquibase 这类数据库迁移工具。
- 没有完整的可观测性方案，例如 metrics、trace id、结构化日志聚合。
- `docs/test-set.json` 还是评测数据，不是自动化 RAG 质量测试。

## 企业级差距

生产前必须确认：

1. `application.yml` 中没有默认 API Key、默认生产密码或过低密码长度。
2. `DOCUMIND_DB_PATH` 和 `app.documents-path` 指向持久化目录。
3. `ALLOWED_ORIGINS` 设置为实际域名。
4. 反向代理支持 SSE 长连接和上传大小。
5. 备份范围包含原始文档、H2 数据库文件、向量索引和索引缓存。
6. 发布前执行 `mvn test`，并用 `docs/test-set.json` 做一轮 RAG 质量检查。

更高成熟度需要补齐：

- PostgreSQL 或其他正式数据库
- 对象存储
- 向量数据库或托管向量服务
- OpenAPI
- API 版本
- 生产 profile
- 数据库迁移
- 自动化 RAG 评测
- 依赖漏洞扫描、镜像扫描和 SBOM
- 日志、指标、告警和审计留存策略

## 代码改动注意事项

- 不要把密钥、真实密码或本地私有路径提交进仓库。
- 修改 RAG 检索、切分、parser、embedding、prompt 时，同步更新 `docs/RAG_EVALUATION.md` 或测试集。
- 修改文件格式支持时，同步后端白名单、parser 分支和前端上传限制。
- 修改认证、权限、限流和审计时，要补对应 controller/service 测试。
- 修改部署相关配置时，同步 `docs/DEPLOYMENT.md`。
- 当前工作区可能有未提交配置改动，处理前先看 `git status --short` 和 `git diff`。
