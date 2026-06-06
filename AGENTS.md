# AGENTS.md

面向 AI 编码助手的项目指南。本文件描述 DocuMind 的运行方式、依赖、架构与已知坑点。

## 项目简介

DocuMind 是一个基于 RAG（检索增强生成）的智能文档问答助手。用户上传文档后，应用将其切分、向量化并建立索引；提问时先在向量库检索相关片段，命中则让 LLM 基于文档内容作答，未命中则回退到 LLM 通用知识。

## 技术栈

- **语言/运行时**: Java 17
- **框架**: Spring Boot 3.2.1（单体 Maven 项目，`jar` 打包）
- **RAG 框架**: LangChain4j 0.27.1
- **嵌入模型**: All-MiniLM-L6-V2（本地运行，jar 依赖，无需联网或外部服务）
- **大语言模型**: DeepSeek API（走 OpenAI 兼容接口）
- **向量存储**: `InMemoryEmbeddingStore`（进程内内存，**非持久化**）
- **前端**: 原生 HTML/CSS/JS，位于 `src/main/resources/static/`

## 构建与运行

启动入口：`com.demo.ragchat.RagChatApplication`，端口 `8080`，访问 http://localhost:8080。

### 关键前置条件

1. **必须用 JDK 17 编译运行**。`pom.xml` 锁定 `java.version=17`。
   - 注意：本机命令行默认 `mvn` 可能挂在更高版本 JDK（如 23）上，直接 `mvn` 会用错版本。命令行运行前先切换：
     ```bash
     export JAVA_HOME=$(/usr/libexec/java_home -v 17)
     ```
   - 在 IntelliJ IDEA 里则在 Project Structure → SDK 选 17。
2. **必须配置 DeepSeek API Key**，否则 LLM 调用失败。这是唯一的外部网络依赖。

### 配置 API Key（二选一）

方式 A —— 环境变量（推荐）：
```bash
export DEEPSEEK_API_KEY=your_key_here
```

方式 B —— 本地配置文件：
```bash
cp src/main/resources/application-local.yml.template src/main/resources/application-local.yml
# 编辑填入 key
```
注意：`local` profile **不会自动激活**，需在运行时显式指定 `-Dspring.profiles.active=local`（或在 IDEA 运行配置的 Active profiles 填 `local`）。因此一般首选方式 A。

### 命令

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn compile              # 编译
mvn spring-boot:run      # 启动
mvn test                 # 测试
```

## 外部服务依赖

| 依赖 | 是否必须 | 说明 |
|------|---------|------|
| DeepSeek API | ✅ 必须 | LLM 推理，需联网 + key |
| JDK 17 | ✅ 必须 | 编译运行 |
| 嵌入模型 | 自动 | All-MiniLM-L6-V2 随依赖打包，本地跑，无需部署 |
| ChromaDB / Docker | ❌ 不需要 | 见下方「已知问题」 |

## 架构概览

```
controller/   ChatController(/api/chat)  FileController(/api/files)
service/      RagService(检索+问答+会话记忆)  DocumentService(文件存储+白名单校验)
config/       LangChainConfig(LLM/嵌入/向量库 Bean)  WebConfig(CORS)
dto/          ChatRequest/Response  FileUploadResponse
exception/    GlobalExceptionHandler + 自定义异常
```

### 核心流程

- **索引**：`RagService.refreshIndex()` 遍历 `documents/` 目录，按扩展名选解析器，切分（`DocumentSplitters.recursive(500, 50)`）后写入向量库。应用启动时（`@PostConstruct`）和文件上传/刷新后触发。
- **问答**：`RagService.ask()` 先用 `ContentRetriever`（`maxResults=3`, `minScore=0.65`）检索；无命中直接调 LLM 通用知识，有命中则交给带会话记忆（`MessageWindowChatMemory`, 10 条）的 `Assistant`。
- **会话**：按 `sessionId` 维护独立 `Assistant` 实例（`ConcurrentHashMap`）。

### HTTP 接口

- `POST /api/chat` —— 提问
- `POST /api/files/upload` —— 上传文档（form-data，字段名 `file`）
- `GET  /api/files/list` —— 列出文档
- `POST /api/files/refresh` —— 重建索引
- `DELETE /api/files/{filename}` —— 删除文档

## 支持的文档格式

后端白名单在 `DocumentService.ALLOWED_EXTENSIONS`，单文件上限默认 50MB，可通过 `DOCUMIND_MAX_FILE_SIZE` 调整：

| 格式 | 扩展名 | 解析器 |
|------|--------|--------|
| PDF | `.pdf` | `ApachePdfBoxDocumentParser` |
| 文本 | `.txt` | `TextDocumentParser` |
| Word | `.doc` `.docx` | `ApachePoiDocumentParser` |
| PPT | `.ppt` `.pptx` | `ApachePoiDocumentParser` |
| Excel | `.xls` `.xlsx` | `ApachePoiDocumentParser` |

新增格式需同步改三处：`DocumentService.ALLOWED_EXTENSIONS`、`RagService.refreshIndex()` 的解析分支、前端 `index.html` 的 `accept` 与提示文案。

## 已知问题与注意事项

- **向量库不持久化**：用的是 `InMemoryEmbeddingStore`，应用一重启，已索引的文档全部丢失，需重新上传或调用 `/api/files/refresh`（注意上传的原始文件仍在 `documents/` 目录，刷新会重建索引）。
- **Chroma 配置是死代码**：`application.yml` 里有 `app.chroma.*` 配置，`LangChainConfig` 也注入了 `chromaUrl`/`collectionName` 两个字段，但 `embeddingStore()` Bean 实际返回的是内存实现，Chroma 相关配置与字段**未被使用**。原因见 `LangChainConfig.java` 注释（ChromaDB Docker 的 Numpy 2.0 / API 405 兼容性问题）。**无需启动 Docker 或 ChromaDB。**
- **Tika vs POI**：Office 解析用的是 `langchain4j-document-parser-apache-poi`，而非更通用的 Tika 解析器——因为 Tika 解析器模块在 LangChain4j 0.29.0 之后才发布，本项目锁定的 0.27.1 拉不到。若将来升级版本，可考虑换成 Tika。
- **Excel 解析效果有限**：POI 对纯数字表格的文本抽取效果一般（按单元格拼文本），大量数值型 Excel 的 RAG 检索质量可能不理想。
- **包名与项目名不一致**：项目已更名为 DocuMind，但 Java 包名仍是 `com.demo.ragchat`，主类仍叫 `RagChatApplication`（历史遗留，未重构）。

## 安全

- 切勿将 API Key 写入受版本控制的文件。`application-local.yml` 已在 `.gitignore` 中。
- 文件上传已做路径穿越防护（`DocumentService` 用 `Paths.get(name).getFileName()` 清洗文件名）。
- 详见 `SECURITY.md`。
