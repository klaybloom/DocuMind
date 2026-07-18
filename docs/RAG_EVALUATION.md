# DocuMind RAG 自动化评测

这份文档用于检查检索和回答质量。每次改检索参数、切分策略、模型或文档解析器后，都应运行自动化评测。

## 自动化运行

评测入口：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn test -Dtest=RagQualityEvaluationTest
```

完整测试也会运行这套评测：

```bash
mvn test
```

评测报告会写入：

```text
target/rag-evaluation-report.json
```

面向维护和简历展示的说明见 [RAG_EVALUATION_REPORT.md](./RAG_EVALUATION_REPORT.md)。

默认通过率门槛为 90%。低于门槛时，`RagQualityEvaluationTest` 会失败，并在失败信息中列出未通过用例。

## 结构化测试集

`docs/test-set.json` 是自动化评测数据源，包含 30 个问题，覆盖 6 类场景：

| 类别 | 数量 | 说明 |
|------|------|------|
| 精确事实 (precise_fact) | 5 | 日期、名称、特性等精确查询 |
| 术语查询 (terminology) | 5 | 缩写、技术术语、核心概念 |
| 多段总结 (multi_chunk) | 5 | 需要综合多个片段回答 |
| 跨文件对比 (cross_file) | 5 | 需要跨文件信息或对比 |
| 无命中降级 (no_hit) | 5 | 文档中无答案，应触发通用回退 |
| 知识隔离 (knowledge_isolation) | 5 | 在其他知识库提问，不应命中 |

测试集 JSON 格式：

```json
{
  "id": 1,
  "category": "precise_fact",
  "knowledgeBase": "default",
  "question": "鸿蒙知识库中提到的系统核心特性有哪些？",
  "expectedAnswerContains": "分布式",
  "expectedSourceFile": "harmony-guide.txt",
  "notes": "精确事实查询"
}
```

## 测试数据

自动化测试使用仓库内固定 TXT 语料，位于 `src/test/resources/rag-fixtures/`：

- `default/harmony-guide.txt`
- `default/product-data.txt`
- `HR/hr-policy.txt`
- `Finance/finance-policy.txt`
- `Legal/legal-policy.txt`

测试不会读取本机真实 `documents/`，不会调用 DeepSeek，也不依赖外部 PDF。测试中使用确定性 embedding model 和 mock LLM。

用例覆盖以下类型：

- 精确事实：制度日期、审批时限、负责人、金额上限。
- 编号和术语：合同编号、工单号、产品名、错误码。
- 多片段汇总：需要从同一文档多个段落合并回答。
- 跨文件对比：同一知识库内多个文件的差异。
- 未命中文档：文档没有答案时，助手应明确说明没有找到。
- 知识缺口：未命中文档的问题应出现在知识缺口列表。
- 权限和隔离：在 A 知识库提问，不能引用 B 知识库的文件。

## 通过标准

- `expectedSourceFile` 有值：必须返回文档答案，并且来源文件必须包含该文件名。
- `expectedSourceFile` 为 `null`：必须不返回文档来源。
- `expectedAnswerContains` 非空：最终 answer 必须包含该文本。
- 总通过率必须大于等于 90%。

## 建议节奏

- 每次发布前运行完整 `mvn test`。
- 每新增一个业务知识库，先补 5 个固定问题。
- 每次发现错误答案，把问题加入 `docs/test-set.json`，并补充对应测试夹具。
