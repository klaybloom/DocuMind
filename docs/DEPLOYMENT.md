# DocuMind 部署手册

DocuMind 使用 MySQL 保存账号、知识库元数据、审计和会话历史，使用 Redis 保存活动会话、限流与幂等状态，使用 Qdrant 保存文档向量。原始文档仍位于 `APP_DOCUMENTS_PATH`。

## 运行环境

- JDK 17
- MySQL 8.4
- Redis 7.4
- Qdrant 1.18，HTTP `6333` 与 gRPC `6334`
- 可访问 DeepSeek API 的网络
- 可持久化的文档目录，例如 `/opt/documind/documents`

## 必填配置

```bash
export SPRING_PROFILES_ACTIVE=mysql
export DEEPSEEK_API_KEY=<deepseek-api-key-from-secret-store>
export DOCUMIND_ADMIN_PASSWORD=<admin-password-from-secret-store>
export DOCUMIND_MYSQL_URL='jdbc:mysql://mysql:3306/documind?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export DOCUMIND_MYSQL_USERNAME=<mysql-user>
export DOCUMIND_MYSQL_PASSWORD=<mysql-password>
export DOCUMIND_REDIS_HOST=redis
export DOCUMIND_REDIS_PORT=6379
export DOCUMIND_REDIS_PASSWORD=<redis-password-if-configured>
export DOCUMIND_QDRANT_HOST=qdrant
export DOCUMIND_QDRANT_GRPC_PORT=6334
export DOCUMIND_QDRANT_HEALTH_URL=http://qdrant:6333/healthz
export DOCUMIND_QDRANT_COLLECTION=documind-segments
export APP_DOCUMENTS_PATH=/opt/documind/documents
```

不要把 API Key、数据库密码或 Redis 密码写入版本控制文件。`application-mysql.yml.template` 只用于说明配置字段。

## 启动与验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn test
mvn package
java -jar target/documind-1.0.0-SNAPSHOT.jar
curl -fsS http://127.0.0.1:8080/api/v1/health
```

首次连接空 MySQL schema 时，Flyway 会执行 `src/main/resources/db/migration/mysql/V1__init_schema.sql`。已有 V1 schema 会被 Flyway 基线纳入管理，Hibernate 仅执行 `validate`，不会自行修改表结构。

## 数据与备份

备份范围包括：

- MySQL `documind` schema；
- Redis 配置与持久化数据（Redis 可由 MySQL 重建会话窗口，但限流与幂等状态会丢失）；
- Qdrant 的持久化 storage 或 collection snapshot；
- `APP_DOCUMENTS_PATH` 下的原始文档。

Qdrant 是文档向量的唯一存储，应用不会回退到内存索引。旧 `.documind-vectors.json` 与 `.documind-index-cache.json` 不会自动删除；完成一次 Qdrant 重建并确认检索正确后，可按运维策略归档。
