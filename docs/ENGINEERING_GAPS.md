# DocuMind 工程化成熟度审计

> 审计日期：2026-06-24
> 最后更新：2026-06-25
> 审计范围：全部源码、配置、测试、部署流程

## 进度总览

| 优先级 | 总数 | 已完成 | 进行中 | 未开始 |
|--------|------|--------|--------|--------|
| P0 — 生产部署必须解决 | 7 | ✅ 5 | 0 | 2 |
| P1 — 近期必须补齐 | 5 | ✅ 5 | 0 | 0 |
| P2 — 质量与可维护性 | 10 | ✅ 9 | 0 | 1 |
| **合计** | **22** | **19** | **0** | **3** |

### 已完成清单

| # | 任务 | 核心变更 |
|---|------|---------|
| P0-1 | 持久化向量库 | `InMemoryEmbeddingStore` 文件序列化 + 原子写入 |
| P0-2 | 用户管理持久化 | `UserAccount` Entity + `DatabaseUserDetailsService` |
| P0-3 | 会话内存有界化 | `ConcurrentHashMap` → Caffeine Cache（1000 max / 60min TTL） |
| P0-4 | 容器化 + CI | Dockerfile 多阶段构建 + GitHub Actions CI |
| P0-5 | 版本管理与发布 | CHANGELOG.md + release workflow（tag → JAR + GHCR image + Release） |
| P1-1 | 补全集成测试 | `@SpringBootTest` + H2 内存模式，5 个集成测试（74 total） |
| P1-2 | 引入数据库层 | H2 + JPA，3 Entity + 3 Repository + JSON 迁移服务 |
| P1-3 | 包名重构 | `com.demo.ragchat` → `com.documind`，主类重命名 |
| P1-4 | 升级核心依赖 | Spring Boot 3.2→3.4.5，LangChain4j 0.27→1.16.3 |
| P1-5 | 前端工程化 | 提取 utils.js/api.js，保留普通 app.js 入口 |
| P2-1 | Lombok 使用 | 13 个 DTO 全部加 `@Data`/`@NoArgsConstructor`，删除手写 getter/setter |
| P2-2 | OpenAPI/Swagger 文档 | 引入 `springdoc-openapi`，自动生成 `/swagger-ui.html` |
| P2-3 | Tokenizer 配置修正 | P1-4 升级时已移除 `OpenAiTokenizer`，无需额外处理 |
| P2-4 | 清理死代码 | ChromaDB 相关代码和配置不存在，无需清理 |
| P2-5 | 统一错误消息语言 | 新增 `messages_zh_CN.properties` 覆盖 Bean Validation 默认消息 |
| P2-7 | API 版本控制 | 全部接口迁移至 `/api/v1/`，前端和测试同步更新 |
| P2-8 | Profile 分层配置 | 新增 `application-prod.yml`（WARN 日志、连接池调优、ddl-auto: validate） |
| P2-9 | 测试 profile 密码策略对齐 | `min-password-length` 恢复为 8 |
| P2-10 | 观测能力与交付安全 | Actuator + Micrometer、traceId MDC、logback-spring.xml、CI 依赖扫描、SBOM 生成 |

### P2 待办（非阻塞，按需推进）

P2 项为质量与可维护性提升，不影响生产部署，可按需安排。

---

## P0 — 生产部署必须解决

### P0-1 持久化向量库

- **状态**: 🟩 已完成
- **问题**: `InMemoryEmbeddingStore` 进程重启即丢失全部索引，需从文档重新解析。文档量大时启动时间不可接受，且重启期间服务不可用。
- **已完成**:
  - [x] `InMemoryEmbeddingStore.serializeToFile()` / `fromFile()` 实现落盘
  - [x] 索引缓存（`indexedDocumentCache`）同步持久化，支持增量刷新
  - [x] 原子写入（temp file + atomic move）防损坏
  - [x] 加载失败自动降级为全量重建
  - [x] 新增单元测试验证序列化/反序列化
- **存储位置**: `{documentsPath}/.documind-vectors.json` + `{documentsPath}/.documind-index-cache.json`
- **影响文件**: `RagService.java`, `RagServiceTest.java`

### P0-2 用户管理持久化

- **状态**: 🟩 已完成
- **已完成**:
  - [x] `UserAccount` JPA Entity + `UserAccountRepository`
  - [x] `DatabaseUserDetailsService` 替代 `InMemoryUserDetailsManager`
  - [x] `UserAccountInitializer`: 启动时从环境变量创建/更新账号，存入数据库
  - [x] `SecurityConfig` 简化为纯路由配置，不再管理用户数据
  - [x] 测试全部更新（69/69 pass）
- **影响文件**: `SecurityConfig.java`, `UserAccountInitializer.java`, `DatabaseUserDetailsService.java`, `SecurityConfigTest.java`

### P0-3 会话内存有界化

- **状态**: 🟩 已完成
- **问题**: `RagService.sessionMemories`（`ConcurrentHashMap`）无 TTL、无容量上限，长时间运行必然 OOM。
- **已完成**:
  - [x] 替换 `ConcurrentHashMap` 为 Caffeine `Cache`
  - [x] `maximumSize` 限制最大会话数（默认 1000）
  - [x] `expireAfterAccess` 自动清理不活跃会话（默认 60 分钟）
  - [x] 两项参数均可通过 `app.chat.session.max` / `app.chat.session.ttl-minutes` 配置
  - [x] 新增单元测试验证容量驱逐
- **影响文件**: `RagService.java`, `RagServiceTest.java`, `pom.xml`, `application.yml`

### P0-4 容器化与 CI 流水线

- **状态**: 🟩 已完成
- **问题**: 无 Dockerfile、无 CI 配置，部署全靠手动 SSH + `mvn package`，不可复现。
- **已完成**:
  - [x] `Dockerfile` 多阶段构建（JDK 17 Maven build → slim runtime）
  - [x] `.dockerignore` 排除无关文件
  - [x] `.github/workflows/ci.yml`：push/PR 触发 `mvn test` + Docker image 构建
- **影响文件**: `Dockerfile`, `.dockerignore`, `.github/workflows/ci.yml`

### P0-5 版本管理与发布流程

- **状态**: 🟩 已完成
- **问题**: `pom.xml` 版本固定为 `1.0.0-SNAPSHOT`，无 release 分支、无 tag、无 changelog。
- **已完成**:
  - [x] `CHANGELOG.md` 遵循 Keep a Changelog 格式
  - [x] `.github/workflows/release.yml`：tag push (`v*`) → test → build JAR → push GHCR image → GitHub Release
  - [x] Release workflow 自动更新 pom.xml 版本号
  - [x] Release 包含已推送的 GHCR Docker 镜像和 JAR 两种快速启动方式
- **发布流程**: `git tag v1.0.0 && git push origin v1.0.0` → 自动触发 CI + Release
- **影响文件**: `CHANGELOG.md`, `.github/workflows/release.yml`

### P0-6 恢复生产配置安全默认值

- **状态**: ⬜ 未开始
- **问题**: 当前工作区的 `src/main/resources/application.yml` 有未提交改动，把 `DEEPSEEK_API_KEY`、管理员密码、普通用户账号密码写成默认值，并把 `DOCUMIND_MIN_PASSWORD_LENGTH` 默认值改为 `2`。这会破坏“缺少密钥/管理员密码时启动失败”的生产安全边界。
- **建议**:
  - `app.deepseek.api-key` 恢复为 `${DEEPSEEK_API_KEY:}`
  - `app.security.admin-password` 恢复为 `${DOCUMIND_ADMIN_PASSWORD:}`
  - `app.security.user-username` / `user-password` 恢复为空默认值
  - `app.security.min-password-length` 恢复为 `${DOCUMIND_MIN_PASSWORD_LENGTH:12}`
  - 修复后运行 `mvn test`，并确认 `docs/DEPLOYMENT.md` 与实际配置一致
- **影响文件**: `src/main/resources/application.yml`

### P0-7 企业级持久化与可迁移存储方案

- **状态**: ⬜ 未开始
- **问题**: 当前架构仍依赖 H2 文件数据库、本地 `documents/` 目录和本地 `.documind-vectors.json` 向量索引文件。该方案适合单实例内部部署，但不适合多实例、容器滚动发布、统一备份、灾备恢复和云原生部署。
- **目标**:
  - H2 文件数据库升级为 PostgreSQL，配置连接池和生产 profile
  - 本地文档目录升级为对象存储，至少抽象出 storage service，支持 S3 兼容服务或云厂商对象存储
  - 本地向量索引升级为可迁移向量库或托管向量服务，支持重建、备份和版本化迁移
  - 明确数据迁移路径：H2 → PostgreSQL，`documents/` → 对象存储，`.documind-vectors.json` → 新向量库
  - 增加部署文档、回滚步骤和迁移验证脚本
- **建议**: 先做接口抽象和迁移设计，再实现 PostgreSQL；对象存储和向量库作为后续子任务分阶段切换，避免一次性重写 RAG 主流程。
- **影响文件**: `pom.xml`, `application.yml`, 新增生产配置与存储/向量服务实现，部署文档，迁移工具或脚本

---

## P1 — 近期必须补齐

### P1-1 补全集成测试

- **状态**: 🟩 已完成
- **已完成**:
  - [x] `IntegrationTest.java` — `@SpringBootTest` + `@ActiveProfiles("test")` + H2 内存模式
  - [x] `application-test.yml` — 测试专用配置（H2 内存、测试 profile 专用 API key 占位值）
  - [x] 5 个集成测试: context 加载、DB schema 创建、文档 CRUD、知识缺口记录、RAG 问答（mock LLM）
  - [x] `@MockitoBean` mock LLM 避免调用真实 API
  - [x] 修复 `@Autowired` 注解确保 Spring 使用正确的构造函数
  - [x] 全部 74 测试通过（69 原有 + 5 集成）
- **影响文件**: `IntegrationTest.java` (新), `application-test.yml` (新), `DocumentService.java`, `AuditService.java`

### P1-2 引入数据库层

- **状态**: 🟩 已完成
- **已完成**:
  - [x] H2 嵌入式数据库（`spring-boot-starter-data-jpa` + `com.h2database:h2`）
  - [x] 3 个 JPA Entity: `DocumentFileEntity`, `KnowledgeGapEntity`, `AuditEventEntity`
  - [x] 3 个 Spring Data Repository
  - [x] `DocumentService` 改造: repository 注入，`@Transactional` 替代 `synchronized`
  - [x] `AuditService` 改造: repository 注入，删除 JSONL 文件 I/O
  - [x] `JsonMigrationService`: 启动时自动迁移旧 JSON → 数据库
  - [x] 测试使用 Mockito mock repository（69/69 pass）
- **影响文件**: `DocumentService.java`, `AuditService.java`, `pom.xml`, `application.yml`, 全部测试
- **解锁**: P0-2 用户管理持久化

### P1-3 包名重构

- **状态**: 🟩 已完成
- **已完成**:
  - [x] 包名 `com.demo.ragchat` → `com.documind`（全部 44 个 Java 文件）
  - [x] 主类 `RagChatApplication` → `DocuMindApplication`
  - [x] `pom.xml` groupId 更新为 `com.documind`
  - [x] `application.yml` 日志路径更新为 `com.documind`
  - [x] `AGENTS.md` 更新
  - [x] linter 同步提取 `PromptTemplateService`（prompt 模板外部化到 classpath 文件）
- **影响文件**: 全部 Java 源文件（44 个）、`pom.xml`、`application.yml`、`AGENTS.md`

### P1-4 升级核心依赖

- **状态**: 🟩 已完成
- **已完成**:
  - [x] Spring Boot `3.2.1` → `3.4.5`
  - [x] LangChain4j `0.27.1` → `1.16.3` (parsers/embeddings `1.16.3-beta26`)
  - [x] JDK 保持 17（本机仅安装 17，21 待环境升级）
  - [x] API 迁移: `ChatLanguageModel`→`ChatModel`, `StreamingChatLanguageModel`→`StreamingChatModel`, `generate()`→`chat()`, `StreamingResponseHandler`→`StreamingChatResponseHandler`, `findRelevant()`→`search()`, `metadata.add()`→`metadata.put()`, `UserMessage.text()`→`singleText()`, `OpenAiTokenizer` 移除, `AllMiniLmL6V2EmbeddingModel` 包路径变更
  - [x] 测试全部更新（69/69 pass）
- **影响文件**: `pom.xml`, `LangChainConfig.java`, `RagService.java`, `RagServiceTest.java`

### P1-5 前端工程化

- **状态**: 🟩 已完成
- **已完成**:
  - [x] `index.html` 改为 `type="module"` 加载 `app.js`
  - [x] `app.js` 接入 `api.js`，认证 fetch、当前用户校验和 SSE 解析不再内联
  - [x] `app.js` 接入 `utils.js`，HTML 转义、回答格式化、文件类型、状态文案、时间格式化集中复用
  - [x] 新增 `npm run test:frontend`，用 Vitest 覆盖 API/SSE 和前端工具函数
  - [x] CI test job 增加 `npm ci` + `npm run test:frontend`
  - [x] 前端依赖 npm audit 通过，后端测试不受影响（78/78 pass）
- **影响文件**: `app.js`, `utils.js`, `api.js`, `index.html`, `package.json`, `package-lock.json`, `src/test/frontend/*`, `.github/workflows/ci.yml`

---

## P2 — 质量与可维护性提升

### P2-1 Lombok 使用或移除

- **状态**: 🟩 已完成
- **已完成**:
  - [x] 全部 13 个 DTO 添加 `@Data` + `@NoArgsConstructor`
  - [x] 删除所有手写 getter/setter（保留含防御逻辑的 setter 和静态工厂方法）
  - [x] `maven-compiler-plugin` 显式配置 Lombok annotation processor
  - [x] 全部 78 测试通过
- **影响文件**: 全部 13 个 DTO 类、`pom.xml`

### P2-2 OpenAPI/Swagger 文档

- **状态**: 🟩 已完成
- **已完成**:
  - [x] 引入 `springdoc-openapi-starter-webmvc-ui:2.8.6`
  - [x] `OpenApiConfig` 配置 Basic Auth 认证方案
  - [x] Swagger UI 访问路径: `/swagger-ui.html`
  - [x] SecurityConfig 放行 `/swagger-ui/**` 和 `/v3/api-docs/**`
- **影响文件**: `pom.xml`, `OpenApiConfig.java`, `SecurityConfig.java`

### P2-3 Tokenizer 配置修正

- **状态**: 🟩 已完成（P1-4 升级时已解决）
- **说明**: P1-4 升级 LangChain4j 至 1.16.3 时已移除 `OpenAiTokenizer`，当前不再有硬编码 tokenizer 引用。

### P2-4 清理死代码

- **状态**: 🟩 已完成（无需清理）
- **说明**: 审计确认 `application.yml` 无 `app.chroma.*` 配置，`LangChainConfig` 无 ChromaDB 相关字段。ChromaDB 死代码不存在。

### P2-5 统一错误消息语言

- **状态**: 🟩 已完成
- **已完成**:
  - [x] 新增 `messages_zh_CN.properties` 覆盖 Bean Validation 默认消息
  - [x] `application.yml` 配置 `spring.messages.basename=messages`，`fallback-to-system-locale=false`
- **影响文件**: `messages_zh_CN.properties`（新）, `application.yml`

### P2-6 减小 synchronized 粒度

- **状态**: ⬜ 未开始
- **问题**: `DocumentService` 多数方法为 `synchronized`，文件操作频繁时成为瓶颈。
- **建议**: 引入数据库层后自然解决。短期可改为读写锁（`ReentrantReadWriteLock`）。
- **影响文件**: `DocumentService.java`

### P2-7 API 版本控制

- **状态**: 🟩 已完成
- **已完成**:
  - [x] 全部 4 个 Controller 的 `@RequestMapping` 迁移至 `/api/v1/`
  - [x] SecurityConfig 路径匹配器同步更新
  - [x] 前端 `app.js` 全部 API 调用更新为 `/api/v1/` 路径
  - [x] 测试文件同步更新（`FileControllerHttpTest`, `ChatControllerHttpTest`）
  - [x] 全部 78 测试通过
- **影响文件**: 全部 4 个 Controller、`SecurityConfig.java`、`app.js`、测试文件

### P2-8 Profile 分层配置

- **状态**: 🟩 已完成
- **已完成**:
  - [x] 新增 `application-prod.yml`：WARN 日志级别、连接池调优（HikariCP）、`ddl-auto: validate`
  - [x] 通过 `--spring.profiles.active=prod` 激活
- **影响文件**: `application-prod.yml`（新）

### P2-9 测试 profile 密码策略对齐

- **状态**: 🟩 已完成
- **已完成**:
  - [x] `application-test.yml` 的 `min-password-length` 从 2 恢复为 8
- **影响文件**: `src/test/resources/application-test.yml`

### P2-10 观测能力与交付安全

- **状态**: 🟩 已完成
- **已完成**:
  - [x] 引入 `spring-boot-starter-actuator`，暴露 health/info/metrics/prometheus 端点
  - [x] `TraceIdFilter` 为每个请求生成 traceId 并写入 MDC + 响应头 `X-Trace-Id`
  - [x] `logback-spring.xml` 结构化日志（prod: JSON 格式，非 prod: 控制台格式），均含 traceId
  - [x] `application.yml` 恢复 `min-password-length` 默认值为 12
  - [x] CI 增加 OWASP dependency-check 扫描步骤
  - [x] Release workflow 增加 CycloneDX SBOM 生成并随 GitHub Release 发布
  - [x] `pom.xml` 新增 `cyclonedx-maven-plugin`，`package` 阶段自动生成 BOM
  - [x] SecurityConfig 放行 `/actuator/**`（ADMIN 角色）、`/swagger-ui/**`
- **影响文件**: `pom.xml`, `application.yml`, `TraceIdFilter.java`（新）, `logback-spring.xml`（新）, `SecurityConfig.java`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`

---

## 已知技术债（非紧急，记录备查）

| 项目 | 说明 |
|------|------|
| Excel 解析效果有限 | POI 纯数字表格文本抽取质量一般 |
| Tika vs POI | LangChain4j 0.27.1 无 Tika 解析器模块，升级后可考虑切换 |
| 嵌入模型局限 | All-MiniLM-L6-V2 是英文模型，中文语义检索质量受限 |
| 无分布式锁 | 当前 `synchronized` 仅限单实例，多实例部署需要分布式锁 |
