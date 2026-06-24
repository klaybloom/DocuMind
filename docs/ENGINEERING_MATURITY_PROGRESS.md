# DocuMind 工程化完成度优化进度

**分支**: `feat/engineering-maturity-and-evaluation`
**开始时间**: 2026-06-24

## 优化计划

详见: `~/.claude/plans/glimmering-crunching-coral.md`

## Phase 1：最高优先级（1-2 天）

| # | 任务 | 阶段 | 状态 | 变更文件 | 说明 |
|---|------|------|------|----------|------|
| 1 | 文件 Hash 去重 | Phase 1 | ✅ 完成 | `DocumentFileInfo.java`, `DocumentService.java`, `DocumentServiceTest.java` | SHA-256 去重，同 KB 下相同内容拒绝上传 |
| 2 | 文档状态前端增强 | Phase 1 | ✅ 完成 | `app.js`, `style.css` | 展示 lastIndexedAt 时间 |
| 3 | 检索结果可视化 | Phase 1 | ✅ 完成 | `RetrievalDebugInfo.java`, `RagService.java`, `RagAnswer.java`, `ChatController.java`, `app.js`, `index.html`, `style.css`, `ChatControllerHttpTest.java`, `RagServiceTest.java` | Debug 视图展示所有候选片段 |
| 4 | README 增强 | Phase 1 | ✅ 完成 | `README.md` | API 表格、环境变量表、徽章、测试覆盖更新 |

## Phase 2：第二优先级（3-5 天）

| # | 任务 | 阶段 | 状态 | 变更文件 | 说明 |
|---|------|------|------|----------|------|
| 5 | 单文档重新索引 | Phase 2 | ✅ 完成 | `RagService.java`, `FileController.java`, `app.js`, `style.css`, `FileControllerHttpTest.java` | POST /api/files/{filename}/reindex |
| 6 | 删除文档同步清理向量 | Phase 2 | ✅ 完成 | `RagService.java`, `FileController.java`, `FileControllerHttpTest.java` | 精准移除 chunk，替代全量 refreshIndex |
| 7 | Prompt 模板外置 | Phase 2 | ⏳ 待开始 | `PromptTemplateService.java`, `prompts/*.md`, `RagService.java`, `application.yml` | 可配置的提示词模板 |
| 8 | 问答测试集 | Phase 2 | ⏳ 待开始 | `docs/test-set.json`, `RagEvaluationRunner.java`, `RAG_EVALUATION.md` | 30 题结构化评测集 |

## Phase 3：后续增强（暂不实施）

- 异步文档处理
- Embedding 模型对比
- 混合检索参数调优
- 完整评测表格

## 变更日志

### 2026-06-24
- 创建分支 `feat/engineering-maturity-and-evaluation`
- 完成文件 Hash 去重功能（Phase 1.1）
  - `DocumentFileInfo.java`: 新增 `fileHash` 字段，扩展构造函数为 12 参数
  - `DocumentService.java`: 新增 `computeFileHash()` 方法，在 `storeFile()` 中计算 SHA-256 并检查同 KB 下重复
  - `DocumentServiceTest.java`: 新增 3 个测试（同 KB 去重、不同 KB 允许、hash 记录验证）
- 完成文档状态前端增强（Phase 1.2）
  - `app.js`: 新增 `statusDetailText()` 函数，展示索引时间
  - `style.css`: 新增 `.file-status-detail` 样式
- 完成检索结果可视化（Phase 1.3）
  - `RetrievalDebugInfo.java`: 新增 DTO，包含 CandidateDebug 内部类
  - `RagAnswer.java`: 新增可选字段 `debugInfo`
  - `RagService.java`: 重构 `retrieveSources()` 返回 `RetrievalResult`，新增 `ask()` 和 `askStream()` 的 debug 重载
  - `ChatController.java`: 新增 `debug` 查询参数，新增 `sendDebugEvent()` 方法
  - `app.js`: 新增 debug 模式切换、`renderDebugPanel()` 函数、SSE debug 事件处理
  - `index.html`: 新增 Debug 复选框开关
  - `style.css`: 新增 debug 面板完整样式（表格、得分标签、匹配类型标签）
  - `RagServiceTest.java`: 修复 `sessionMemories` 初始化，添加 `cleanUp()` 调用
  - `ChatControllerHttpTest.java`: 更新 FakeRagService 方法签名
- **全部 69 个测试通过**

### Phase 1.4 完成
- README 增强
  - 添加 Java/Spring Boot/LangChain4j/License 徽章
  - API 接口改为分类表格（问答、文件管理、知识缺口、健康检查）
  - 新增环境变量参考表（必填 2 项 + 可选 19 项）
  - 更新测试覆盖说明（69 个测试，13 个测试类）
  - 新增功能说明（文件 Hash 去重、检索调试视图）

## 测试状态

- **当前测试数**: 69
- **通过**: 69
- **失败**: 0

### Phase 2.1-2.2 完成
- 单文档重新索引（Phase 2.1）
  - `RagService.java`: 新增 `reindexDocument()`、`removeDocument()`、`rebuildStoreFromSegments()` 方法
  - `FileController.java`: 新增 `POST /{filename}/reindex` 端点，删除文档改用 `removeDocument()`
  - `app.js`: 新增 `reindexFile()` 函数，文件列表增加重新索引按钮
  - `style.css`: 新增 `.reindex-file-btn` 样式
  - `FileControllerHttpTest.java`: 更新 FakeRagService 追踪 removeCalls
- 删除文档同步清理向量（Phase 2.2）
  - `RagService.removeDocument()`: 过滤指定文件的 chunk，重建 store
  - `FileController`: 删除后调用 `removeDocument()` 替代 `refreshIndex()`
- **全部 69 个测试通过**
