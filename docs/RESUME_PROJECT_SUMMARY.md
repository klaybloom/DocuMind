# DocuMind 简历项目说明

## 项目定位

DocuMind 是一个 Java 17 + Spring Boot 3.4.5 实现的 RAG 文档问答系统。用户上传 PDF、Word、Excel、PPT、TXT 文档后，系统会解析文本、切分片段、生成本地 embedding、建立检索索引，并在问答时结合向量检索和关键词补充匹配返回带来源的回答。

## 可写进简历的亮点

- 设计并实现文档上传、解析、切分、embedding、向量检索、来源引用和 SSE 流式问答链路。
- 基于 Spring Security、JPA 和 H2/PostgreSQL profile 实现账号、知识库权限、审计记录和健康检查。
- 引入 Flyway 管理 PostgreSQL schema，保留 H2 作为本地默认数据库，并提供 Docker Compose 本机验证环境。
- 实现 RAG 自动化评测集，覆盖精确事实、术语查询、多片段总结、跨文件对比、无命中降级和知识库隔离。
- 建立 CI：后端测试、前端 Vitest、Gitleaks、OWASP dependency-check、PostgreSQL 集成测试、Docker build、Trivy 镜像扫描和 Release SBOM。

## 技术难点

- RAG 检索不是只做向量 topK，而是先扩大候选池，再叠加关键词匹配，最后输出调试视图，便于分析召回失败。
- 文档索引支持文件 hash 去重、索引状态、单文件重新索引、删除文档同步移除片段和向量索引持久化。
- 权限模型区分管理员、普通用户、知识库 owner 和知识库成员，普通用户只能访问被授权的知识库。
- PostgreSQL profile 使用 Flyway 初始化 schema，再用 Hibernate validate 检查实体和数据库结构一致性。

## 当前边界

- 原始文档和向量索引仍保存在本地文件系统，适合单实例部署；对象存储和外置向量库尚未接入。
- 关系型数据已支持 PostgreSQL profile，但尚未提供 H2 历史数据自动迁移到 PostgreSQL 的工具。
- embedding 模型使用 `AllMiniLmL6V2EmbeddingModel`，中文召回质量需要按真实业务语料继续评估。
- Basic Auth 满足内部工具级访问控制；面向公网或多租户场景还需要更完整的认证、授权和审计策略。

## 推荐简历写法

> DocuMind：基于 Spring Boot 和 LangChain4j 的 RAG 文档问答系统。负责后端核心链路设计与实现，支持多格式文档解析、向量检索 + 关键词混合召回、SSE 流式回答、来源引用、知识库权限、审计和健康检查；引入 PostgreSQL/Flyway profile、RAG 自动化评测、CI 密钥扫描/依赖扫描/镜像扫描和 Docker 发布流程，提升项目工程化与可验证性。
