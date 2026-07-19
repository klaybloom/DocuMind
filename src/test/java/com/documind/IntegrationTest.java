package com.documind;

import com.documind.dto.DocumentFileInfo;
import com.documind.config.QdrantConfig.QdrantCollectionState;
import com.documind.repository.AuditEventRepository;
import com.documind.repository.DocumentFileRepository;
import com.documind.repository.KnowledgeGapRepository;
import com.documind.repository.UserAccountRepository;
import com.documind.service.DocumentService;
import com.documind.service.ChatHistoryService;
import com.documind.service.ChatSessionStore;
import com.documind.service.RagService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationTest {

    @TempDir
    static java.nio.file.Path tempDir;

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    StreamingChatModel streamingChatModel;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Autowired
    DocumentService documentService;

    @Autowired
    RagService ragService;

    @Autowired
    ChatHistoryService chatHistoryService;

    @Autowired
    ChatSessionStore chatSessionStore;

    @Autowired
    DocumentFileRepository fileRepository;

    @Autowired
    KnowledgeGapRepository gapRepository;

    @Autowired
    AuditEventRepository auditRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    QdrantCollectionState qdrantCollectionState;

    @BeforeEach
    void clearQdrantCollection() {
        embeddingStore.removeAll();
    }

    @Test
    void contextLoads() {
        // Verify all key beans are wired
        assertThat(documentService).isNotNull();
        assertThat(ragService).isNotNull();
        assertThat(fileRepository).isNotNull();
        assertThat(gapRepository).isNotNull();
        assertThat(auditRepository).isNotNull();
        assertThat(userAccountRepository).isNotNull();
        assertThat(embeddingStore).isInstanceOf(QdrantEmbeddingStore.class);
        assertThat(qdrantCollectionState.collection()).isEqualTo("documind-test-segments");
    }

    @Test
    void databaseSchemaIsCreatedAndRepositoriesWork() {
        // Verify tables exist and repositories can CRUD
        long initialFileCount = fileRepository.count();
        long initialGapCount = gapRepository.count();
        long initialAuditCount = auditRepository.count();

        // UserAccountInitializer should have created the admin account
        assertThat(userAccountRepository.existsByUsername("admin")).isTrue();
    }

    @Test
    void documentServiceStoresAndRetrievesFiles() {
        // Verify repository works directly first
        var directEntity = new com.documind.model.DocumentFileEntity(
                "default", "direct-test.txt", 100, "text/plain",
                "Admin", "admin", java.time.Instant.now().toString(),
                null, "PENDING", 0, null);
        fileRepository.save(directEntity);
        fileRepository.flush();
        assertThat(fileRepository.findByKnowledgeBaseAndFileName("default", "direct-test.txt")).isPresent();

        // Now test via service
        MockMultipartFile file = new MockMultipartFile(
                "file", "integration-test.txt", "text/plain",
                "Hello World".getBytes(StandardCharsets.UTF_8));
        documentService.storeFile(file, "default", "Admin", "admin");

        var entity = fileRepository.findByKnowledgeBaseAndFileName("default", "integration-test.txt");
        assertThat(entity).isPresent();
        assertThat(entity.get().getOwner()).isEqualTo("Admin");

        // Clean up
        documentService.deleteFile("integration-test.txt", "default");
        documentService.deleteFile("direct-test.txt", "default");
    }

    @Test
    void knowledgeGapRecordingAndResolution() {
        documentService.recordKnowledgeGap("default", "Integration test question?", "ig-session-1");
        documentService.recordKnowledgeGap("default", "Integration test question?", "ig-session-2");

        var gaps = documentService.listKnowledgeGaps("default");
        assertThat(gaps).isNotEmpty();
        assertThat(gaps.stream().anyMatch(g -> "Integration test question?".equals(g.getQuestion())
                && g.getOccurrences() == 2)).isTrue();

        // Resolve and verify
        String gapId = gaps.stream()
                .filter(g -> "Integration test question?".equals(g.getQuestion()))
                .findFirst().orElseThrow().getId();
        documentService.resolveKnowledgeGap("default", gapId);

        var remaining = documentService.listKnowledgeGaps("default");
        assertThat(remaining.stream().noneMatch(g -> "Integration test question?".equals(g.getQuestion()))).isTrue();
    }

    @Test
    void ragServiceAskWithMockedLlm() {
        // Setup mock embedding model
        when(embeddingModel.embed(any(String.class)))
                .thenAnswer(inv -> Response.from(Embedding.from(vector())));
        when(embeddingModel.embed(any(TextSegment.class)))
                .thenAnswer(inv -> Response.from(Embedding.from(vector())));

        // Setup mock chat model
        when(chatModel.chat(any(java.util.List.class)))
                .thenAnswer(inv -> ChatResponse.builder()
                        .aiMessage(AiMessage.from("这是一个测试回答。"))
                        .build());

        var answer = ragService.ask("测试问题", "integration-session", "default");

        assertThat(answer).isNotNull();
        assertThat(answer.getAnswer()).contains("测试回答");
    }

    @Test
    void qdrantStoresVectorsAndRemovesOnlyTheSelectedDocument() {
        TextSegment first = qdrantSegment("HR/hr.txt#1", "HR/hr.txt", "考勤制度要求九点前到岗。");
        TextSegment second = qdrantSegment("Finance/finance.txt#1", "Finance/finance.txt", "报销制度要求提交发票。");
        embeddingStore.addAll(
                List.of("8d9245f2-a0e1-4cc0-a0cc-5fcb1c64b701", "8d9245f2-a0e1-4cc0-a0cc-5fcb1c64b702"),
                List.of(Embedding.from(vector()), Embedding.from(vector())),
                List.of(first, second));

        assertThat(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(vector()))
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .matches()).hasSize(2);

        embeddingStore.removeAll(metadataKey("document_key").isEqualTo("HR/hr.txt"));

        assertThat(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(vector()))
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .matches())
                .extracting(match -> match.embedded().metadata().getString("document_key"))
                .containsExactly("Finance/finance.txt");
    }

    private TextSegment qdrantSegment(String chunkId, String documentKey, String text) {
        Metadata metadata = new Metadata();
        metadata.put("chunk_id", chunkId);
        metadata.put("document_key", documentKey);
        return TextSegment.from(text, metadata);
    }

    private float[] vector() {
        float[] vector = new float[384];
        vector[0] = 1.0f;
        return vector;
    }

    @Test
    void redisSessionWindowCanBeRebuiltFromMysqlHistory() {
        String sessionId = "admin:history-" + java.util.UUID.randomUUID();
        chatHistoryService.appendExchange(sessionId, "default", "第一轮问题", "第一轮回答");

        var restored = chatHistoryService.latestMessages(sessionId, "default", 10);
        assertThat(restored)
                .hasSize(2)
                .extracting(Object::getClass)
                .containsExactly(UserMessage.class, AiMessage.class);

        MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        memory.set(restored);
        chatSessionStore.save("default", sessionId, memory);
        assertThat(chatSessionStore.load("default", sessionId).messages()).hasSize(2);

        assertThat(chatSessionStore.clear(sessionId)).isEqualTo(1);
        assertThat(chatSessionStore.load("default", sessionId).messages()).isEmpty();
        assertThat(chatHistoryService.latestMessages(sessionId, "default", 10)).hasSize(2);
    }

    @Test
    void frontendModuleAssetsArePublic() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/utils.js"))
                .andExpect(status().isOk());
    }
}
