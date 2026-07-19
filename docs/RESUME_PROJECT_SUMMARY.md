# DocuMind 项目说明

DocuMind 是一个基于 Spring Boot 和 LangChain4j 的 RAG 文档问答系统，支持多格式文档解析、向量检索与关键词混合召回、SSE 流式回答、来源引用、知识库权限和审计。

- 使用 MySQL + Flyway 管理账号、知识库元数据、审计、会话和长期记忆数据。
- 使用 Redis 管理活动会话、账号级限流和幂等状态；Redis 缓存失效后从 MySQL 恢复最近会话窗口。
- 使用本地 All-MiniLM-L6-V2 生成 embedding，并通过 Qdrant 持久化向量与片段元数据。
- CI 包含后端 MySQL/Redis 集成测试、前端 Vitest、Gitleaks、OWASP dependency-check、Docker 构建和镜像扫描。

当前多实例部署仍需将文档向量索引替换为共享向量存储，并把原始文档放入对象存储或共享挂载目录。
