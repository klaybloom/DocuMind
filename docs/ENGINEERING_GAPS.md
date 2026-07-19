# DocuMind 工程改进清单

## 已完成

- 关系型数据统一到 MySQL 8.4，使用 Flyway 管理 schema，Hibernate 只做 schema 校验。
- Redis 负责活动会话窗口、账号级限流与短期幂等状态；MySQL 保存完整会话记录。
- CI 使用 MySQL 和 Redis service container，后端集成测试不再依赖嵌入式数据库。
- 本机 `mysql-dev` 中已建立独立的 `documind` 与 `documind_test` schema。

## 后续事项

- 文档向量已改由 Qdrant 保存；多实例前仍需补齐原始文档的共享存储和任务协调。
- 原始文档与向量快照仍依赖本地目录；生产多实例前迁移到对象存储或共享卷。
- RBAC、用户长期记忆提炼和用户端会话管理已有表结构基础，后续按业务权限接口逐步启用。
- 增加 MySQL 8.4 对应 Flyway 版本的兼容性回归检查；当前 Flyway migration 已在本机 8.4 实测通过。
