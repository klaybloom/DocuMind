# DocuMind 工程化成熟度审计

> 审计日期：2026-06-24
> 最后更新：2026-06-24
> 审计范围：全部源码、配置、测试、部署流程

## 进度总览

| 优先级 | 总数 | 已完成 | 进行中 | 未开始 |
|--------|------|--------|--------|--------|
| P0 — 生产部署必须解决 | 5 | ✅ 5 | 0 | 0 |
| P1 — 近期必须补齐 | 5 | ✅ 5 | 0 | 0 |
| P2 — 质量与可维护性 | 8 | 0 | 0 | 8 |
| **合计** | **18** | **10** | **0** | **8** |

### 已完成清单

| # | 任务 | 核心变更 |
|---|------|---------|
| P0-1 | 持久化向量库 | `InMemoryEmbeddingStore` 文件序列化 + 原子写入 |
| P0-2 | 用户管理持久化 | `UserAccount` Entity + `DatabaseUserDetailsService` |
| P0-3 | 会话内存有界化 | `ConcurrentHashMap` → Caffeine Cache（1000 max / 60min TTL） |
| P0-4 | 容器化 + CI | Dockerfile 多阶段构建 + GitHub Actions CI |
| P0-5 | 版本管理与发布 | CHANGELOG.md + release workflow（tag → JAR + Release） |
| P1-1 | 补全集成测试 | `@SpringBootTest` + H2 内存模式，5 个集成测试（74 total） |
| P1-2 | 引入数据库层 | H2 + JPA，3 Entity + 3 Repository + JSON 迁移服务 |
| P1-3 | 包名重构 | `com.demo.ragchat` → `com.documind`，主类重命名 |
| P1-4 | 升级核心依赖 | Spring Boot 3.2→3.4.5，LangChain4j 0.27→1.16.3 |
| P1-5 | 前端工程化 | ES Modules 拆分（utils.js + api.js） |

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
  - [x] `.github/workflows/release.yml`：tag push (`v*`) → test → build JAR → GitHub Release
  - [x] Release workflow 自动更新 pom.xml 版本号
  - [x] Release 包含 Docker 和 JAR 两种快速启动方式
- **发布流程**: `git tag v1.0.0 && git push origin v1.0.0` → 自动触发 CI + Release
- **影响文件**: `CHANGELOG.md`, `.github/workflows/release.yml`

---

## P1 — 近期必须补齐

### P1-1 补全集成测试

- **状态**: 🟩 已完成
- **已完成**:
  - [x] `IntegrationTest.java` — `@SpringBootTest` + `@ActiveProfiles("test")` + H2 内存模式
  - [x] `application-test.yml` — 测试专用配置（H2 内存、dummy API key）
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
  - [x] ES Modules 拆分: `app.js` (1199 行) + `utils.js` (124 行) + `api.js` (126 行)
  - [x] `index.html` script 标签改为 `type="module"`
  - [x] 13 个纯工具函数提取到 `utils.js`
  - [x] 3 个 API 函数提取到 `api.js`
  - [x] 语法检查通过，后端测试不受影响（69/69 pass）
- **影响文件**: `app.js`, `utils.js` (新), `api.js` (新), `index.html`

---

## P2 — 质量与可维护性提升

### P2-1 Lombok 使用或移除

- **状态**: ⬜ 未开始
- **问题**: `pom.xml` 引入 Lombok 依赖，但所有 DTO 手写 getter/setter。
- **建议**: 要么全部 DTO 加 `@Data`/`@Value`，要么删掉 Lombok 依赖。保持一致。
- **影响文件**: 所有 DTO 类（约 10 个）、`pom.xml`

### P2-2 OpenAPI/Swagger 文档

- **状态**: ⬜ 未开始
- **问题**: API 文档仅靠 `AGENTS.md` 口头描述，无类型化接口文档。
- **建议**: 引入 `springdoc-openapi`，自动生成 `/swagger-ui.html`。
- **影响文件**: `pom.xml`, 新增 OpenAPI 配置类

### P2-3 Tokenizer 配置修正

- **状态**: ⬜ 未开始
- **问题**: `LangChainConfig` 中 tokenizer 硬编码 `gpt-3.5-turbo`，DeepSeek tokenizer 与 OpenAI 不完全一致。
- **建议**: 升级 LangChain4j 后使用 DeepSeek 专用 tokenizer，或至少更新为更接近的模型名。
- **影响文件**: `LangChainConfig.java`

### P2-4 清理死代码

- **状态**: ⬜ 未开始
- **问题**:
  - `application.yml` 中 `app.chroma.*` 配置项未使用
  - `LangChainConfig` 注入的 `chromaUrl`/`collectionName` 字段未使用（如果有的话）
- **建议**: 删除所有 ChromaDB 相关死代码和配置。
- **影响文件**: `application.yml`, `LangChainConfig.java`（如有）

### P2-5 统一错误消息语言

- **状态**: ⬜ 未开始
- **问题**: 用户面向的错误消息中英混杂（"文件不能为空" vs Bean Validation 默认英文消息）。
- **建议**: 统一为中文，配置 `messages_zh_CN.properties` 覆盖 Bean Validation 默认消息。
- **影响文件**: `src/main/resources/messages_zh_CN.properties`

### P2-6 减小 synchronized 粒度

- **状态**: ⬜ 未开始
- **问题**: `DocumentService` 多数方法为 `synchronized`，文件操作频繁时成为瓶颈。
- **建议**: 引入数据库层后自然解决。短期可改为读写锁（`ReentrantReadWriteLock`）。
- **影响文件**: `DocumentService.java`

### P2-7 API 版本控制

- **状态**: ⬜ 未开始
- **问题**: 所有接口挂在 `/api/` 下，无版本前缀，breaking change 时无路可退。
- **建议**: 迁移至 `/api/v1/`。旧路径保留 301 重定向过渡期。
- **影响文件**: 全部 Controller、前端 `app.js`

### P2-8 Profile 分层配置

- **状态**: ⬜ 未开始
- **问题**: 只有 `application.yml` + 可选 `application-local.yml`，无生产专用配置。
- **建议**: 增加 `application-prod.yml`（日志级别 WARN、CORS 收紧、连接池调优等）。
- **影响文件**: `src/main/resources/application-prod.yml`, 部署文档

---

## 已知技术债（非紧急，记录备查）

| 项目 | 说明 |
|------|------|
| Excel 解析效果有限 | POI 纯数字表格文本抽取质量一般 |
| Tika vs POI | LangChain4j 0.27.1 无 Tika 解析器模块，升级后可考虑切换 |
| 嵌入模型局限 | All-MiniLM-L6-V2 是英文模型，中文语义检索质量受限 |
| 无分布式锁 | 当前 `synchronized` 仅限单实例，多实例部署需要分布式锁 |
