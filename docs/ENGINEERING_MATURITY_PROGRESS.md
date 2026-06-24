# DocuMind 工程化完成度优化进度

**分支**: `feat/engineering-maturity-and-evaluation`
**开始时间**: 2026-06-24
**Phase 1-2 完成时间**: 2026-06-24

## 优化计划

详见: `~/.claude/plans/glimmering-crunching-coral.md`

## Phase 1：最高优先级 ✅ 全部完成

| # | 任务 | 阶段 | 状态 | 说明 |
|---|------|------|------|------|
| 1 | 文件 Hash 去重 | Phase 1 | ✅ 完成 | SHA-256 去重，同 KB 下相同内容拒绝上传 |
| 2 | 文档状态前端增强 | Phase 1 | ✅ 完成 | 展示 lastIndexedAt 时间 |
| 3 | 检索结果可视化 | Phase 1 | ✅ 完成 | Debug 视图展示所有候选片段 |
| 4 | README 增强 | Phase 1 | ✅ 完成 | API 表格、环境变量表、徽章 |

## Phase 2：第二优先级 ✅ 全部完成

| # | 任务 | 阶段 | 状态 | 说明 |
|---|------|------|------|------|
| 5 | 单文档重新索引 | Phase 2 | ✅ 完成 | POST /api/files/{filename}/reindex |
| 6 | 删除文档同步清理向量 | Phase 2 | ✅ 完成 | 精准移除 chunk，替代全量 refreshIndex |
| 7 | Prompt 模板外置 | Phase 2 | ✅ 完成 | 可配置的提示词模板 |
| 8 | 问答测试集 | Phase 2 | ✅ 完成 | 30 题结构化评测集 |

## Phase 3：后续增强（暂不实施）

| 项目 | 说明 |
|------|------|
| 异步文档处理 | DocumentIndexingService + 线程池 + 前端轮询状态 |
| Embedding 模型对比 | 同一测试集跑不同模型，对比召回准确率 |
| 混合检索参数调优 | min-score / max-results / keywordMinHitRatio A/B 测试 |
| 完整评测表格 | 多组参数对比结果文档化 |

## 变更日志

### 2026-06-24

#### Phase 1 完成

**1.1 文件 Hash 去重**
- `DocumentFileInfo.java`: 新增 `fileHash` 字段，扩展构造函数为 12 参数
- `DocumentService.java`: 新增 `computeFileHash()` 方法，在 `storeFile()` 中计算 SHA-256 并检查同 KB 下重复
- `DocumentServiceTest.java`: 新增 3 个测试（同 KB 去重、不同 KB 允许、hash 记录验证）

**1.2 文档状态前端增强**
- `app.js`: 新增 `statusDetailText()` 函数，展示索引时间
- `style.css`: 新增 `.file-status-detail` 样式

**1.3 检索结果可视化**
- `RetrievalDebugInfo.java`: 新增 DTO，包含 CandidateDebug 内部类
- `RagAnswer.java`: 新增可选字段 `debugInfo`
- `RagService.java`: 重构 `retrieveSources()` 返回 `RetrievalResult`，新增 `ask()` 和 `askStream()` 的 debug 重载
- `ChatController.java`: 新增 `debug` 查询参数，新增 `sendDebugEvent()` 方法
- `app.js`: 新增 debug 模式切换、`renderDebugPanel()` 函数、SSE debug 事件处理
- `index.html`: 新增 Debug 复选框开关
- `style.css`: 新增 debug 面板完整样式（表格、得分标签、匹配类型标签）
- `RagServiceTest.java`: 修复 `sessionMemories` 初始化，添加 `cleanUp()` 调用
- `ChatControllerHttpTest.java`: 更新 FakeRagService 方法签名

**1.4 README 增强**
- 添加 Java/Spring Boot/LangChain4j/License 徽章
- API 接口改为分类表格（问答、文件管理、知识缺口、健康检查）
- 新增环境变量参考表（必填 2 项 + 可选 19 项）
- 更新测试覆盖说明（69 个测试，13 个测试类）
- 新增功能说明（文件 Hash 去重、检索调试视图）

#### Phase 2 完成

**2.1 单文档重新索引**
- `RagService.java`: 新增 `reindexDocument()`、`rebuildStoreFromSegments()` 方法
- `FileController.java`: 新增 `POST /{filename}/reindex` 端点
- `app.js`: 新增 `reindexFile()` 函数，文件列表增加重新索引按钮
- `style.css`: 新增 `.reindex-file-btn` 样式

**2.2 删除文档同步清理向量**
- `RagService.java`: 新增 `removeDocument()` 方法，过滤指定文件 chunk 后重建 store
- `FileController.java`: 删除后调用 `removeDocument()` 替代 `refreshIndex()`
- `FileControllerHttpTest.java`: 更新 FakeRagService 追踪 removeCalls

**2.3 Prompt 模板外置**
- `PromptTemplateService.java`: 新增模板加载服务，支持 classpath 和配置覆盖
- `prompts/system-document.md`: 文档问答系统提示词
- `prompts/system-general.md`: 无命中时通用问答系统提示词
- `prompts/user-with-sources.md`: 带资料片段的用户提示词模板（支持 `{{question}}` 和 `{{sources}}` 占位符）
- `RagService.java`: 注入 PromptTemplateService，替换内联 prompt 方法
- 更新所有测试文件的 RagService 构造函数调用

**2.4 问答测试集**
- `docs/test-set.json`: 30 个结构化问题，覆盖 6 类场景
  - 精确事实 (5)、术语查询 (5)、多段总结 (5)
  - 跨文件对比 (5)、无命中降级 (5)、知识隔离 (5)
- `docs/RAG_EVALUATION.md`: 链接到测试集，新增分类表格说明

## 测试状态

- **当前测试数**: 69
- **通过**: 69
- **失败**: 0
