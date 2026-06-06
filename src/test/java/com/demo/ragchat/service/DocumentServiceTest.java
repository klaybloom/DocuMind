package com.demo.ragchat.service;

import com.demo.ragchat.dto.DocumentFileInfo;
import com.demo.ragchat.dto.FaqDraftResponse;
import com.demo.ragchat.dto.KnowledgeBaseStatus;
import com.demo.ragchat.dto.KnowledgeGapInfo;
import com.demo.ragchat.exception.InvalidFileException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceTest {

    @TempDir
    Path tempDir;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService();
        ReflectionTestUtils.setField(documentService, "documentsPath", tempDir.toString());
        ReflectionTestUtils.setField(documentService, "staleDays", 180);
        ReflectionTestUtils.setField(documentService, "maxFileSize", DataSize.ofMegabytes(50));
    }

    @Test
    void storeFileCreatesKnowledgeBaseManifestAndOwnerMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../policy.txt",
                "text/plain",
                "报销流程".getBytes(StandardCharsets.UTF_8)
        );

        String storedName = documentService.storeFile(file, "HR", " Alice ", " admin ");

        assertThat(storedName).isEqualTo("policy.txt");
        assertThat(tempDir.resolve("HR/policy.txt")).exists();
        assertThat(tempDir.resolve("HR/.documind-files.json")).exists();

        List<DocumentFileInfo> files = documentService.listDocumentFiles("HR");
        assertThat(files).hasSize(1);
        DocumentFileInfo info = files.get(0);
        assertThat(info.getKnowledgeBase()).isEqualTo("HR");
        assertThat(info.getFileName()).isEqualTo("policy.txt");
        assertThat(info.getOwner()).isEqualTo("Alice");
        assertThat(info.getUploadedBy()).isEqualTo("admin");
        assertThat(info.getIndexStatus()).isEqualTo(DocumentService.STATUS_PENDING);
    }

    @Test
    void storeFileUsesUploaderAsOwnerWhenOwnerIsBlankAndRejectsOverlongOwner() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "报销流程".getBytes(StandardCharsets.UTF_8)
        );

        documentService.storeFile(file, "HR", "   ", "admin");

        DocumentFileInfo info = documentService.listDocumentFiles("HR").get(0);
        assertThat(info.getOwner()).isEqualTo("admin");
        assertThatThrownBy(() -> documentService.storeFile(file, "HR", "x".repeat(81), "admin"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("负责人长度不能超过80字符");
    }

    @Test
    void markIndexedAndStatusSummarizeKnowledgeBase() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "handbook.txt",
                "text/plain",
                "员工手册".getBytes(StandardCharsets.UTF_8)
        );
        documentService.storeFile(file, "HR", "Alice", "admin");
        DocumentFileInfo info = documentService.listDocumentFiles("HR").get(0);

        documentService.markIndexed(info, 3);

        List<DocumentFileInfo> files = documentService.listDocumentFiles("HR");
        assertThat(files.get(0).getIndexStatus()).isEqualTo(DocumentService.STATUS_INDEXED);
        assertThat(files.get(0).getChunkCount()).isEqualTo(3);

        KnowledgeBaseStatus status = documentService.listKnowledgeBaseStatuses()
                .stream()
                .filter(item -> "HR".equals(item.getKnowledgeBase()))
                .findFirst()
                .orElseThrow();
        assertThat(status.getTotalFiles()).isEqualTo(1);
        assertThat(status.getIndexedFiles()).isEqualTo(1);
        assertThat(status.getFailedFiles()).isZero();
    }

    @Test
    void storeFileUsesConfiguredMaxFileSize() {
        ReflectionTestUtils.setField(documentService, "maxFileSize", DataSize.ofBytes(4));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "too-large.txt",
                "text/plain",
                "12345".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.storeFile(file, "HR", "Alice", "admin"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("4 字节");
    }

    @Test
    void rejectsInvalidKnowledgeBaseNameInsteadOfSanitizingIt() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.storeFile(file, "../HR", "Alice", "admin"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("知识库名称无效");
        assertThatThrownBy(() -> documentService.listDocumentFiles("HR 数据"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("知识库名称只能包含");
        assertThatThrownBy(() -> documentService.normalizeKnowledgeBase("x".repeat(61)))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("知识库名称长度不能超过60字符");
    }

    @Test
    void resolveDownloadPathReturnsStoredFileAndRejectsPathTraversal() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8)
        );
        documentService.storeFile(file, "HR", "Alice", "admin");

        Path downloadPath = documentService.resolveDownloadPath("policy.txt", "HR");

        assertThat(downloadPath).exists();
        assertThat(downloadPath).isEqualTo(tempDir.resolve("HR/policy.txt"));
        assertThatThrownBy(() -> documentService.resolveDownloadPath("../policy.txt", "HR"))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void deleteFileReturnsWhetherStoredFileWasDeleted() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8)
        );
        documentService.storeFile(file, "HR", "Alice", "admin");

        boolean deleted = documentService.deleteFile("policy.txt", "HR");
        boolean missing = documentService.deleteFile("policy.txt", "HR");

        assertThat(deleted).isTrue();
        assertThat(missing).isFalse();
        assertThat(documentService.listDocumentFiles("HR")).isEmpty();
        assertThatThrownBy(() -> documentService.deleteFile("../policy.txt", "HR"))
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> documentService.deleteFile("policy.exe", "HR"))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void listDocumentFilesMarksOldManifestEntriesAsStale() throws Exception {
        ReflectionTestUtils.setField(documentService, "staleDays", 1);
        Path kbDir = tempDir.resolve("HR");
        Files.createDirectories(kbDir);
        Path documentPath = kbDir.resolve("old-policy.txt");
        Files.writeString(documentPath, "旧制度", StandardCharsets.UTF_8);

        DocumentFileInfo oldFile = new DocumentFileInfo(
                "HR",
                "old-policy.txt",
                Files.size(documentPath),
                "text/plain",
                "Alice",
                "admin",
                "2025-01-01T00:00:00Z",
                null,
                DocumentService.STATUS_PENDING,
                0,
                null
        );
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(kbDir.resolve(".documind-files.json").toFile(), List.of(oldFile));

        DocumentFileInfo info = documentService.listDocumentFiles("HR").get(0);

        assertThat(info.isStale()).isTrue();
        assertThat(info.getDaysSinceUpload()).isGreaterThan(1);
    }

    @Test
    void recordKnowledgeGapDeduplicatesQuestionsAndGeneratesFaqDraft() {
        documentService.recordKnowledgeGap("Legal", "合同模板在哪里申请？", "session-1");
        documentService.recordKnowledgeGap("Legal", "合同模板在哪里申请？", "session-2");

        List<KnowledgeGapInfo> gaps = documentService.listKnowledgeGaps("Legal");
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).getOccurrences()).isEqualTo(2);
        assertThat(gaps.get(0).getSessionId()).isEqualTo("session-2");

        FaqDraftResponse draft = documentService.generateFaqDraft("Legal");
        assertThat(draft.getQuestionCount()).isEqualTo(1);
        assertThat(draft.getMarkdown())
                .contains("合同模板在哪里申请？")
                .contains("出现次数：2")
                .contains("建议负责人：待指定");

        KnowledgeGapInfo removed = documentService.resolveKnowledgeGap("Legal", gaps.get(0).getId());

        assertThat(removed.getQuestion()).isEqualTo("合同模板在哪里申请？");
        assertThat(documentService.listKnowledgeGaps("Legal")).isEmpty();
        assertThat(documentService.resolveKnowledgeGap("Legal", "missing")).isNull();
    }
}
