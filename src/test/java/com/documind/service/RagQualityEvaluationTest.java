package com.documind.service;

import com.documind.dto.DocumentFileInfo;
import com.documind.dto.KnowledgeGapInfo;
import com.documind.dto.RagAnswer;
import com.documind.dto.SourceReference;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RagQualityEvaluationTest {

    private static final double PASS_THRESHOLD = 0.90;
    private static final Path TEST_SET_PATH = Path.of("docs/test-set.json");
    private static final Path FIXTURES_PATH = Path.of("src/test/resources/rag-fixtures");
    private static final Path REPORT_PATH = Path.of("target/rag-evaluation-report.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void evaluatesRagQualityFromStructuredTestSet() throws Exception {
        List<EvaluationCase> cases = MAPPER.readValue(
                TEST_SET_PATH.toFile(),
                new TypeReference<>() {
                });
        FixtureDocumentService documentService = new FixtureDocumentService(tempDir);
        documentService.loadFixtures(FIXTURES_PATH);

        RagService ragService = service(documentService);
        ragService.refreshIndex();

        List<EvaluationResult> results = new ArrayList<>();
        for (EvaluationCase testCase : cases) {
            RagAnswer answer = ragService.ask(
                    testCase.question(),
                    "rag-eval-" + testCase.id(),
                    testCase.knowledgeBase(),
                    true);
            results.add(evaluate(testCase, answer));
        }

        writeReport(results);

        long passed = results.stream().filter(EvaluationResult::passed).count();
        double passRate = passed / (double) results.size();
        assertThat(passRate)
                .withFailMessage(() -> failureSummary(results, passed, passRate))
                .isGreaterThanOrEqualTo(PASS_THRESHOLD);
    }

    private RagService service(DocumentService documentService) {
        PromptTemplateService promptTemplateService = new PromptTemplateService();
        ReflectionTestUtils.setField(promptTemplateService, "systemDocumentPath", "classpath:prompts/system-document.md");
        ReflectionTestUtils.setField(promptTemplateService, "systemGeneralPath", "classpath:prompts/system-general.md");
        ReflectionTestUtils.setField(promptTemplateService, "userWithSourcesPath", "classpath:prompts/user-with-sources.md");
        promptTemplateService.init();

        RagService ragService = new RagService(
                new EvaluationChatModel(),
                noOpStreamingModel(),
                new FeatureEmbeddingModel(),
                new InMemoryEmbeddingStore<>(),
                documentService,
                promptTemplateService
        );
        return ragService;
    }

    private EvaluationResult evaluate(EvaluationCase testCase, RagAnswer answer) {
        List<String> reasons = new ArrayList<>();
        List<String> sourceFiles = answer.getSources().stream()
                .map(SourceReference::getFileName)
                .toList();

        if (testCase.expectedSourceFile() == null) {
            if (answer.isFromDocuments() || !answer.getSources().isEmpty()) {
                reasons.add("expected no document source");
            }
        } else {
            if (!answer.isFromDocuments()) {
                reasons.add("expected document answer");
            }
            if (!sourceFiles.contains(testCase.expectedSourceFile())) {
                reasons.add("missing expected source file: " + testCase.expectedSourceFile());
            }
        }

        if (!isBlank(testCase.expectedAnswerContains())
                && !containsIgnoreCase(answer.getAnswer(), testCase.expectedAnswerContains())) {
            reasons.add("answer missing expected text: " + testCase.expectedAnswerContains());
        }

        return new EvaluationResult(
                testCase.id(),
                testCase.category(),
                testCase.knowledgeBase(),
                testCase.question(),
                testCase.expectedAnswerContains(),
                testCase.expectedSourceFile(),
                sourceFiles,
                abbreviate(answer.getAnswer(), 320),
                reasons.isEmpty(),
                reasons
        );
    }

    private void writeReport(List<EvaluationResult> results) throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());
        long passed = results.stream().filter(EvaluationResult::passed).count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("threshold", PASS_THRESHOLD);
        report.put("total", results.size());
        report.put("passed", passed);
        report.put("passRate", passed / (double) results.size());
        report.put("results", results);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);
    }

    private String failureSummary(List<EvaluationResult> results, long passed, double passRate) {
        StringBuilder builder = new StringBuilder();
        builder.append("RAG evaluation pass rate ")
                .append(String.format(Locale.ROOT, "%.2f%%", passRate * 100))
                .append(" is below ")
                .append(String.format(Locale.ROOT, "%.2f%%", PASS_THRESHOLD * 100))
                .append(" (")
                .append(passed)
                .append("/")
                .append(results.size())
                .append("). Report: ")
                .append(REPORT_PATH)
                .append('\n');
        results.stream()
                .filter(result -> !result.passed())
                .forEach(result -> builder.append("#")
                        .append(result.id())
                        .append(" ")
                        .append(result.category())
                        .append(" - ")
                        .append(String.join("; ", result.reasons()))
                        .append('\n'));
        return builder.toString();
    }

    private StreamingChatModel noOpStreamingModel() {
        return new StreamingChatModel() {
            @Override
            public void chat(List<ChatMessage> messages,
                             dev.langchain4j.model.chat.response.StreamingChatResponseHandler handler) {
            }
        };
    }

    private boolean containsIgnoreCase(String text, String expected) {
        if (text == null || expected == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private record EvaluationCase(int id,
                                  String category,
                                  String knowledgeBase,
                                  String question,
                                  String expectedAnswerContains,
                                  String expectedSourceFile,
                                  String notes) {
    }

    private record EvaluationResult(int id,
                                    String category,
                                    String knowledgeBase,
                                    String question,
                                    String expectedAnswerContains,
                                    String expectedSourceFile,
                                    List<String> actualSourceFiles,
                                    String actualAnswerExcerpt,
                                    boolean passed,
                                    List<String> reasons) {
    }

    private static class EvaluationChatModel implements ChatModel {
        @Override
        public dev.langchain4j.model.chat.response.ChatResponse chat(List<ChatMessage> messages) {
            String lastUserMessage = messages.stream()
                    .filter(UserMessage.class::isInstance)
                    .map(UserMessage.class::cast)
                    .reduce((first, second) -> second)
                    .map(UserMessage::singleText)
                    .orElse("");
            String response = lastUserMessage.contains("文件：")
                    ? "基于资料回答。\n" + lastUserMessage
                    : "未找到相关资料。";
            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
        }
    }

    private static class FeatureEmbeddingModel implements EmbeddingModel {

        private static final List<String> FEATURES = List.of(
                "鸿蒙", "harmonyos", "分布式", "软总线", "设备协同", "多设备", "arkui", "arkts",
                "原子化", "免安装", "微内核", "hap", "harmonyos ability package", "ability",
                "fa", "pa", "feature ability", "particle ability", "方舟", "编译器", "提前编译",
                "开发流程", "需求分析", "设计", "编码", "调试", "测试", "生命周期", "创建",
                "后台", "安全", "权限", "证书", "加密", "应用市场", "分发", "android",
                "生态伙伴", "设备厂商", "行业集成商", "产品数据", "产品代号", "alpha", "beta",
                "gamma", "灰度验证", "正式发布", "内部实验", "运营状态"
        );

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                    .map(segment -> Embedding.from(vectorize(segment.text())))
                    .toList();
            return Response.from(embeddings);
        }

        private float[] vectorize(String text) {
            String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
            float[] vector = new float[FEATURES.size() + 1];
            boolean matched = false;
            for (int i = 0; i < FEATURES.size(); i++) {
                String feature = FEATURES.get(i).toLowerCase(Locale.ROOT);
                if (normalized.contains(feature)) {
                    vector[i] = 1.0f;
                    matched = true;
                }
            }
            if (!matched) {
                vector[FEATURES.size()] = 1.0f;
            }
            return vector;
        }
    }

    private static class FixtureDocumentService extends DocumentService {
        private final Path root;
        private final List<DocumentFileInfo> files = new ArrayList<>();
        private final List<String> recordedQuestions = new ArrayList<>();

        private FixtureDocumentService(Path root) {
            this.root = root;
        }

        private void loadFixtures(Path fixturesRoot) throws IOException {
            try (Stream<Path> stream = Files.walk(fixturesRoot)) {
                for (Path source : stream.filter(Files::isRegularFile).toList()) {
                    Path relative = fixturesRoot.relativize(source);
                    Path target = root.resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    String knowledgeBase = relative.getName(0).toString();
                    files.add(new DocumentFileInfo(
                            knowledgeBase,
                            source.getFileName().toString(),
                            Files.size(target),
                            "text/plain",
                            null,
                            "rag-evaluation",
                            "2026-06-25T00:00:00Z",
                            "2026-06-25T00:00:00Z",
                            STATUS_PENDING,
                            0,
                            null,
                            null
                    ));
                }
            }
        }

        @Override
        public String normalizeKnowledgeBase(String knowledgeBase) {
            return knowledgeBase == null || knowledgeBase.trim().isEmpty()
                    ? DEFAULT_KNOWLEDGE_BASE
                    : knowledgeBase.trim();
        }

        @Override
        public synchronized List<DocumentFileInfo> listDocumentFiles() {
            return List.copyOf(files);
        }

        @Override
        public synchronized List<DocumentFileInfo> listDocumentFiles(String knowledgeBase) {
            String kb = normalizeKnowledgeBase(knowledgeBase);
            return files.stream()
                    .filter(file -> kb.equals(file.getKnowledgeBase()))
                    .toList();
        }

        @Override
        public Path resolveDocumentPath(DocumentFileInfo fileInfo) {
            return root.resolve(fileInfo.getKnowledgeBase()).resolve(fileInfo.getFileName());
        }

        @Override
        public synchronized void markIndexing(DocumentFileInfo fileInfo) {
            fileInfo.setIndexStatus(STATUS_INDEXING);
        }

        @Override
        public synchronized void markIndexed(DocumentFileInfo fileInfo, int chunkCount) {
            fileInfo.setIndexStatus(STATUS_INDEXED);
            fileInfo.setChunkCount(chunkCount);
            fileInfo.setLastIndexedAt("2026-06-25T00:00:00Z");
        }

        @Override
        public synchronized void markIndexFailed(DocumentFileInfo fileInfo, String error) {
            fileInfo.setIndexStatus(STATUS_FAILED);
            fileInfo.setError(error);
        }

        @Override
        public synchronized void recordKnowledgeGap(String knowledgeBase, String question, String sessionId) {
            recordedQuestions.add(question);
        }

        @Override
        public synchronized List<KnowledgeGapInfo> listKnowledgeGaps(String knowledgeBase) {
            return Collections.emptyList();
        }

        @Override
        public synchronized List<String> suggestOwners(String knowledgeBase) {
            return Collections.emptyList();
        }
    }
}
