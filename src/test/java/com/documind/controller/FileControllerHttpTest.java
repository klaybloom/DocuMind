package com.documind.controller;

import com.documind.exception.InvalidFileException;
import com.documind.service.AuditService;
import com.documind.service.DocumentService;
import com.documind.service.KnowledgeBaseAccessService;
import com.documind.service.KnowledgeBaseManagementService;
import com.documind.service.RagService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerHttpTest {

    private MockMvc mockMvc;
    private FakeDocumentService documentService;
    private FakeRagService ragService;
    private RecordingAuditService auditService;
    private KnowledgeBaseManagementService managementService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        documentService = new FakeDocumentService();
        ragService = new FakeRagService(documentService);
        auditService = new RecordingAuditService();
        KnowledgeBaseAccessService accessService = mock(KnowledgeBaseAccessService.class);
        managementService = mock(KnowledgeBaseManagementService.class);
        when(managementService.canManage(any(), anyString())).thenReturn(true);
        FileController controller = new FileController(
                documentService,
                ragService,
                auditService,
                accessService,
                managementService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void downloadDocumentReturnsContentAndRecordsAuditEvent() throws Exception {
        Path document = tempDir.resolve("policy.txt");
        Files.writeString(document, "员工制度", StandardCharsets.UTF_8);
        documentService.downloadPath = document;

        mockMvc.perform(get("/api/v1/files/policy.txt/download")
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

        mockMvc.perform(get("/api/v1/files/missing.txt/download")
                        .param("knowledgeBase", "HR")
                        .principal(named("admin")))
                .andExpect(status().isNotFound());

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void invalidDownloadInputReturnsBadRequestWithoutAuditEvent() throws Exception {
        documentService.downloadError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(get("/api/v1/files/policy.txt/download")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest());

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void deletingMissingDocumentReturnsNotFoundWithoutRefreshingIndex() throws Exception {
        documentService.deleteResult = false;

        mockMvc.perform(delete("/api/v1/files/missing.txt")
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

        mockMvc.perform(delete("/api/v1/files/policy.txt")
                        .param("knowledgeBase", "HR")
                        .principal(named("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("文件删除成功"));

        assertThat(documentService.deleteCalls).isEqualTo(1);
        assertThat(ragService.removeCalls).isEqualTo(1);
        assertThat(auditService.recordCalls).isEqualTo(1);
        assertThat(auditService.actor).isEqualTo("admin");
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_DELETE_DOCUMENT);
        assertThat(auditService.knowledgeBase).isEqualTo("HR");
        assertThat(auditService.fileName).isEqualTo("policy.txt");
    }

    @Test
    void deletingWithInvalidKnowledgeBaseReturnsBadRequestWithoutSideEffects() throws Exception {
        documentService.normalizeError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(delete("/api/v1/files/policy.txt")
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

        mockMvc.perform(get("/api/v1/files/list")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("知识库名称无效"));

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void listGapsWithInvalidKnowledgeBaseReturnsBadRequest() throws Exception {
        documentService.gapsError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(get("/api/v1/files/gaps")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("知识库名称无效"));

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void faqDraftWithInvalidKnowledgeBaseReturnsBadRequestWithoutAuditEvent() throws Exception {
        documentService.faqError = new InvalidFileException("知识库名称无效");

        mockMvc.perform(get("/api/v1/files/faq-draft")
                        .param("knowledgeBase", "../HR")
                        .principal(named("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("知识库名称无效"));

        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void ownerCanUploadDocumentToManagedKnowledgeBase() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                .param("knowledgeBase", "HR")
                        .principal(named("owner", "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("文件上传成功"));

        assertThat(documentService.storeCalls).isEqualTo(1);
        assertThat(ragService.reindexCalls).isEqualTo(1);
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_UPLOAD_DOCUMENT);
        assertThat(auditService.knowledgeBase).isEqualTo("HR");
    }

    @Test
    void nonOwnerCannotUploadDocumentToUnmanagedKnowledgeBase() throws Exception {
        when(managementService.canManage(any(), anyString())).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                .param("knowledgeBase", "HR")
                        .principal(named("reader", "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("当前账号没有该知识库管理权限"));

        assertThat(documentService.storeCalls).isZero();
        assertThat(ragService.reindexCalls).isZero();
        assertThat(auditService.recordCalls).isZero();
    }


    private Authentication named(String name) {
        return named(name, "ROLE_ADMIN");
    }

    private Authentication named(String name, String role) {
        return new UsernamePasswordAuthenticationToken(
                name,
                "password",
                java.util.List.of(new SimpleGrantedAuthority(role))
        );
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
        private int storeCalls;

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
        public synchronized java.util.List<com.documind.dto.DocumentFileInfo> listDocumentFiles(String knowledgeBase) {
            if (listError != null) {
                throw listError;
            }
            return java.util.List.of();
        }

        @Override
        public synchronized java.util.List<com.documind.dto.KnowledgeGapInfo> listKnowledgeGaps(String knowledgeBase) {
            if (gapsError != null) {
                throw gapsError;
            }
            return java.util.List.of();
        }

        @Override
        public synchronized com.documind.dto.FaqDraftResponse generateFaqDraft(String knowledgeBase) {
            if (faqError != null) {
                throw faqError;
            }
            return new com.documind.dto.FaqDraftResponse("HR", "2026-06-04T00:00:00Z", 0, "");
        }

        @Override
        public synchronized boolean deleteFile(String filename, String knowledgeBase) {
            deleteCalls++;
            return deleteResult;
        }

        @Override
        public synchronized String storeFile(org.springframework.web.multipart.MultipartFile file,
                                             String knowledgeBase,
                                             String owner,
                                             String uploadedBy) {
            storeCalls++;
            return file.getOriginalFilename();
        }
    }

    static class FakeRagService extends RagService {

        private int refreshCalls;
        private int removeCalls;
        private int reindexCalls;

        FakeRagService(DocumentService documentService) {
            super(null, null, null, new InMemoryEmbeddingStore<TextSegment>(), documentService, null);
        }

        @Override
        public synchronized void refreshIndex() {
            refreshCalls++;
        }

        @Override
        public synchronized void removeDocument(String filename, String knowledgeBase) {
            removeCalls++;
        }

        @Override
        public synchronized void reindexDocument(String filename, String knowledgeBase) {
            reindexCalls++;
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
