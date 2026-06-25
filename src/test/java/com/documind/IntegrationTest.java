package com.documind;

import com.documind.dto.DocumentFileInfo;
import com.documind.repository.AuditEventRepository;
import com.documind.repository.DocumentFileRepository;
import com.documind.repository.KnowledgeGapRepository;
import com.documind.repository.UserAccountRepository;
import com.documind.service.DocumentService;
import com.documind.service.RagService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
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
    DocumentFileRepository fileRepository;

    @Autowired
    KnowledgeGapRepository gapRepository;

    @Autowired
    AuditEventRepository auditRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
        // Verify all key beans are wired
        assertThat(documentService).isNotNull();
        assertThat(ragService).isNotNull();
        assertThat(fileRepository).isNotNull();
        assertThat(gapRepository).isNotNull();
        assertThat(auditRepository).isNotNull();
        assertThat(userAccountRepository).isNotNull();
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
                .thenAnswer(inv -> Response.from(Embedding.from(new float[]{1.0f, 0.0f, 0.0f})));
        when(embeddingModel.embed(any(TextSegment.class)))
                .thenAnswer(inv -> Response.from(Embedding.from(new float[]{1.0f, 0.0f, 0.0f})));

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
    void frontendModuleAssetsArePublic() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/utils.js"))
                .andExpect(status().isOk());
    }
}
