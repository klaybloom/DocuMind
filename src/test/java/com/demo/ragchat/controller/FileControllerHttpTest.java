package com.demo.ragchat.controller;

import com.demo.ragchat.exception.InvalidFileException;
import com.demo.ragchat.service.AuditService;
import com.demo.ragchat.service.DocumentService;
import com.demo.ragchat.service.KnowledgeBaseAccessService;
import com.demo.ragchat.service.RagService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerHttpTest {

    private MockMvc mockMvc;
    private FakeDocumentService documentService;
    private FakeRagService ragService;
    private RecordingAuditService auditService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        documentService = new FakeDocumentService();
        ragService = new FakeRagService(documentService);
        auditService = new RecordingAuditService();
        FileController controller = new FileController(
                documentService,
                ragService,
                auditService,
                new KnowledgeBaseAccessService(documentService)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void downloadDocumentReturnsContentAndRecordsAuditEvent() throws Exception {
        Path document = tempDir.resolve("policy.txt");
        Files.writeString(document, "员工制度", StandardCharsets.UTF_8);
        documentService.downloadPath = document;

        mockMvc.perform(get("/api/files/policy.txt/download")
                        .param("knowledgeBase", "HR")
                        .principal(named("admin")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("policy.txt")))
                .andExpect(content().bytes("员工制度".getBytes(StandardCharsets.UTF_8)));

        assertThat(auditService.recordCalls).isEqualTo(1);
        assertThat(auditService.actor).isEqualTo("admin");
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_DOWNLOAD_DOCUMENT);
        assertThat(auditService.knowledgeBase).isEqualTo("HR");
        assertThat(auditService.fileName).isEqualTo("policy.txt");
        assertThat(auditService.details).containsKey("sizeBytes");
    }

    @Test
    void invalidDownloadReturnsNotFoundWithoutAuditEvent() throws Exception {
        documentService.downloadError = new InvalidFileException("文件不存在");

        mockMvc.perform(get("/api/files/missing.txt/download")
                        .param("knowledgeBase", "HR")
                        .principal(named("admin")))
                .andExpect(status().isNotFound());

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void invalidDownloadInputReturnsBadRequestWithoutAuditEvent() throws Exception {
        documentService.downloadError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(get("/api/files/policy.txt/download")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest());

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void deletingMissingDocumentReturnsNotFoundWithoutRefreshingIndex() throws Exception {
        documentService.deleteResult = false;

        mockMvc.perform(delete("/api/files/missing.txt")
                        .param("knowledgeBase", "HR")
                        .principal(named("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("文件不存在"));

        assertThat(documentService.deleteCalls).isEqualTo(1);
        assertThat(ragService.refreshCalls).isZero();
        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void deletingStoredDocumentRefreshesIndexAndRecordsAuditEvent() throws Exception {
        documentService.deleteResult = true;

        mockMvc.perform(delete("/api/files/policy.txt")
                        .param("knowledgeBase", "HR")
                        .principal(named("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("文件删除成功"));

        assertThat(documentService.deleteCalls).isEqualTo(1);
        assertThat(ragService.refreshCalls).isEqualTo(1);
        assertThat(auditService.recordCalls).isEqualTo(1);
        assertThat(auditService.actor).isEqualTo("admin");
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_DELETE_DOCUMENT);
        assertThat(auditService.knowledgeBase).isEqualTo("HR");
        assertThat(auditService.fileName).isEqualTo("policy.txt");
    }

    @Test
    void deletingWithInvalidKnowledgeBaseReturnsBadRequestWithoutSideEffects() throws Exception {
        documentService.normalizeError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(delete("/api/files/policy.txt")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("知识库名称无效"));

        assertThat(documentService.deleteCalls).isZero();
        assertThat(ragService.refreshCalls).isZero();
        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void listFilesWithInvalidKnowledgeBaseReturnsBadRequest() throws Exception {
        documentService.listError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(get("/api/files/list")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("知识库名称无效"));

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void listGapsWithInvalidKnowledgeBaseReturnsBadRequest() throws Exception {
        documentService.gapsError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(get("/api/files/gaps")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("知识库名称无效"));

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void faqDraftWithInvalidKnowledgeBaseReturnsBadRequestWithoutAuditEvent() throws Exception {
        documentService.faqError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(get("/api/files/faq-draft")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("知识库名称无效"));

        assertThat(auditService.recordCalls).isZero();
    }


    private Principal named(String name) {
        return () -> name;
    }

    static class FakeDocumentService extends DocumentService {

        private Path downloadPath;
        private RuntimeException downloadError;
        private RuntimeException normalizeError;
        private RuntimeException listError;
        private RuntimeException gapsError;
        private RuntimeException faqError;
        private boolean deleteResult;
        private int deleteCalls;

        @Override
        public String normalizeKnowledgeBase(String knowledgeBase) {
            if (normalizeError != null) {
                throw normalizeError;
            }
            return knowledgeBase == null || knowledgeBase.trim().isEmpty() ? DEFAULT_KNOWLEDGE_BASE : knowledgeBase.trim();
        }

        @Override
        public synchronized Path resolveDownloadPath(String filename, String knowledgeBase) {
            if (downloadError != null) {
                throw downloadError;
            }
            return downloadPath;
        }

        @Override
        public synchronized java.util.List<com.demo.ragchat.dto.DocumentFileInfo> listDocumentFiles(String knowledgeBase) {
            if (listError != null) {
                throw listError;
            }
            return java.util.List.of();
        }

        @Override
        public synchronized java.util.List<com.demo.ragchat.dto.KnowledgeGapInfo> listKnowledgeGaps(String knowledgeBase) {
            if (gapsError != null) {
                throw gapsError;
            }
            return java.util.List.of();
        }

        @Override
        public synchronized com.demo.ragchat.dto.FaqDraftResponse generateFaqDraft(String knowledgeBase) {
            if (faqError != null) {
                throw faqError;
            }
            return new com.demo.ragchat.dto.FaqDraftResponse("HR", "2026-06-04T00:00:00Z", 0, "");
        }

        @Override
        public synchronized boolean deleteFile(String filename, String knowledgeBase) {
            deleteCalls++;
            return deleteResult;
        }
    }

    static class FakeRagService extends RagService {

        private int refreshCalls;

        FakeRagService(DocumentService documentService) {
            super(null, null, null, new InMemoryEmbeddingStore<TextSegment>(), documentService);
        }

        @Override
        public synchronized void refreshIndex() {
            refreshCalls++;
        }
    }

    static class RecordingAuditService extends AuditService {

        private int recordCalls;
        private String actor;
        private String action;
        private String knowledgeBase;
        private String fileName;
        private Map<String, Object> details;

        @Override
        public synchronized void record(String actor,
                                        String action,
                                        String knowledgeBase,
                                        String fileName,
                                        Map<String, Object> details) {
            recordCalls++;
            this.actor = actor;
            this.action = action;
            this.knowledgeBase = knowledgeBase;
            this.fileName = fileName;
            this.details = details;
        }
    }
}
