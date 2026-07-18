# DocuMind RAG 评测报告

## 评测目标

这份报告说明当前 RAG 自动化测试验证了什么，以及没有验证什么。评测入口是 `RagQualityEvaluationTest`，完整测试会随 `mvn test` 执行，也可以单独运行：

```bash
mvn test -Dtest=RagQualityEvaluationTest
```

## 测试集

评测数据来自 `docs/test-set.json`，共 30 个问题，覆盖 6 类场景：

| 类别 | 数量 | 验证目标 |
|------|------|----------|
| precise_fact | 5 | 精确事实是否能命中正确来源 |
| terminology | 5 | 术语、编号、缩写是否能被召回 |
| multi_chunk | 5 | 多片段信息是否能组合回答 |
| cross_file | 5 | 同知识库内跨文件对比是否能命中 |
| no_hit | 5 | 文档无答案时是否不编造来源 |
| knowledge_isolation | 5 | 不同知识库之间是否隔离 |

测试语料位于 `src/test/resources/rag-fixtures/`。测试中使用确定性 embedding model 和 mock LLM，不调用真实 DeepSeek API。

## 当前通过标准

- 总通过率必须大于等于 90%。
- 有 `expectedSourceFile` 的用例必须返回文档答案，并包含预期来源文件。
- `expectedSourceFile` 为 `null` 的用例不能返回文档来源。
- 有 `expectedAnswerContains` 的用例，回答中必须包含预期文本。

## 当前结论

当前评测适合证明 RAG 检索、知识库隔离、无命中降级和来源引用的工程逻辑是可回归的。它不能证明真实业务文档上的最终回答质量，因为测试没有覆盖真实 PDF/Office 复杂版式，也没有调用真实 DeepSeek 模型。

因此，简历中可以写“建立 RAG 自动化评测集并纳入测试流程”，不应写成“已达到生产级问答准确率”。

## 后续改进

- 每接入一个真实知识库，补充至少 5 个固定业务问题和预期来源。
- 增加真实 PDF/Word/Excel 夹具，覆盖版式复杂、表格密集和扫描件失败场景。
- 对比不同 embedding 模型和参数组合，记录召回率、误召回率和无命中准确率。
- 将 `target/rag-evaluation-report.json` 作为 CI artifact 保存，便于观察版本间质量变化。
