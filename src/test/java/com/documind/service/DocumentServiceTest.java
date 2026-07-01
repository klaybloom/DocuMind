package com.documind.service;

import com.documind.dto.DocumentFileInfo;
import com.documind.dto.FaqDraftResponse;
import com.documind.dto.KnowledgeBaseStatus;
import com.documind.dto.KnowledgeGapInfo;
import com.documind.exception.InvalidFileException;
import com.documind.model.DocumentFileEntity;
import com.documind.model.KnowledgeGapEntity;
import com.documind.repository.DocumentFileRepository;
import com.documind.repository.KnowledgeGapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentServiceTest {

    @TempDir
    Path tempDir;

    private DocumentService documentService;
    private DocumentFileRepository fileRepository;
    private KnowledgeGapRepository gapRepository;
    private final List<DocumentFileEntity> fileStore = new ArrayList<>();
    private final List<KnowledgeGapEntity> gapStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fileRepository = mock(DocumentFileRepository.class);
        gapRepository = mock(KnowledgeGapRepository.class);
        documentService = new DocumentService(fileRepository, gapRepository);
        ReflectionTestUtils.setField(documentService, "documentsPath", tempDir.toString());
        ReflectionTestUtils.setField(documentService, "staleDays", 180);
        ReflectionTestUtils.setField(documentService, "maxFileSize", DataSize.ofMegabytes(50));

        fileStore.clear();
        gapStore.clear();

        // Wire fileRepository mock
        when(fileRepository.findByKnowledgeBase(any())).thenAnswer(inv -> {
            String kb = inv.getArgument(0);
            return fileStore.stream().filter(e -> kb.equals(e.getKnowledgeBase())).toList();
        });
        when(fileRepository.findByKnowledgeBaseAndFileName(any(), any())).thenAnswer(inv -> {
            String kb = inv.getArgument(0), fn = inv.getArgument(1);
            return fileStore.stream()
                    .filter(e -> kb.equals(e.getKnowledgeBase()) && fn.equals(e.getFileName()))
                    .findFirst();
        });
        when(fileRepository.findDistinctKnowledgeBase()).thenAnswer(inv ->
                fileStore.stream().map(DocumentFileEntity::getKnowledgeBase).distinct().toList());
        when(fileRepository.save(any())).thenAnswer(inv -> {
            DocumentFileEntity e = inv.getArgument(0);
            fileStore.removeIf(existing ->
                    e.getKnowledgeBase().equals(existing.getKnowledgeBase())
                            && e.getFileName().equals(existing.getFileName()));
            fileStore.add(e);
            return e;
        });
        doAnswer(inv -> {
            String kb = inv.getArgument(0), fn = inv.getArgument(1);
            fileStore.removeIf(e -> kb.equals(e.getKnowledgeBase()) && fn.equals(e.getFileName()));
            return 1L;
        }).when(fileRepository).deleteByKnowledgeBaseAndFileName(any(), any());

        // Wire gapRepository mock
        when(gapRepository.findByKnowledgeBaseOrderByLastAskedAtDesc(any())).thenAnswer(inv -> {
            String kb = inv.getArgument(0);
            return gapStore.stream()
                    .filter(e -> kb.equals(e.getKnowledgeBase()))
                    .sorted(Comparator.comparing(KnowledgeGapEntity::getLastAskedAt).reversed())
                    .toList();
        });
        when(gapRepository.findByKnowledgeBaseAndQuestionIgnoreCase(any(), any())).thenAnswer(inv -> {
            String kb = inv.getArgument(0), q = inv.getArgument(1);
            return gapStore.stream()
                    .filter(e -> kb.equals(e.getKnowledgeBase()) && q.equalsIgnoreCase(e.getQuestion()))
                    .findFirst();
        });
        when(gapRepository.save(any())).thenAnswer(inv -> {
            KnowledgeGapEntity e = inv.getArgument(0);
            gapStore.removeIf(existing -> e.getId().equals(existing.getId()));
            gapStore.add(e);
            return e;
        });
        when(gapRepository.findByKnowledgeBaseAndId(any(), any())).thenAnswer(inv -> {
            String kb = inv.getArgument(0), id = inv.getArgument(1);
            return gapStore.stream()
                    .filter(e -> kb.equals(e.getKnowledgeBase()) && id.equals(e.getId()))
                    .findFirst();
        });
        doAnswer(inv -> {
            gapStore.remove(inv.getArgument(0));
            return null;
        }).when(gapRepository).delete(any());
    }

    @Test
    void storeFileCreatesKnowledgeBaseManifestAndOwnerMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../policy.txt", "text/plain",
                "报销流程".getBytes(StandardCharsets.UTF_8));

        String storedName = documentService.storeFile(file, "HR", " Alice ", " admin ");

        assertThat(storedName).isEqualTo("policy.txt");
        assertThat(tempDir.resolve("HR/policy.txt")).exists();

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
                "file", "policy.txt", "text/plain",
                "报销流程".getBytes(StandardCharsets.UTF_8));

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
                "file", "handbook.txt", "text/plain",
                "员工手册".getBytes(StandardCharsets.UTF_8));
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
                "file", "too-large.txt", "text/plain",
                "12345".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> documentService.storeFile(file, "HR", "Alice", "admin"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("4 字节");
    }

    @Test
    void rejectsInvalidKnowledgeBaseNameInsteadOfSanitizingIt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.txt", "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8));

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
                "file", "policy.txt", "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8));
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
                "file", "policy.txt", "text/plain",
                "员工制度".getBytes(StandardCharsets.UTF_8));
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
    void listDocumentFilesMarksOldEntriesAsStale() {
        ReflectionTestUtils.setField(documentService, "staleDays", 1);
        fileStore.add(new DocumentFileEntity(
                "HR", "old-policy.txt", 100, "text/plain",
                "Alice", "admin", "2025-01-01T00:00:00Z",
                null, "PENDING", 0, null));

        List<DocumentFileInfo> files = documentService.listDocumentFiles("HR");
        assertThat(files).hasSize(1);
        assertThat(files.get(0).isStale()).isTrue();
        assertThat(files.get(0).getDaysSinceUpload()).isGreaterThan(1);
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

    @Test
    void resolveKnowledgeGapDoesNotDeleteGapFromDifferentKnowledgeBase() {
        documentService.recordKnowledgeGap("Legal", "合同模板在哪里申请？", "session-1");
        String legalGapId = documentService.listKnowledgeGaps("Legal").get(0).getId();

        KnowledgeGapInfo removed = documentService.resolveKnowledgeGap("HR", legalGapId);

        assertThat(removed).isNull();
        assertThat(documentService.listKnowledgeGaps("Legal")).hasSize(1);
        assertThat(documentService.listKnowledgeGaps("HR")).isEmpty();
    }

    @Test
    void storeFileRejectsDuplicateHashInSameKnowledgeBase() {
        byte[] content = "相同内容的文件".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file1 = new MockMultipartFile("file", "doc1.txt", "text/plain", content);
        MockMultipartFile file2 = new MockMultipartFile("file", "doc2.txt", "text/plain", content);

        documentService.storeFile(file1, "HR", "Alice", "admin");

        assertThatThrownBy(() -> documentService.storeFile(file2, "HR", "Alice", "admin"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("该知识库中已存在相同内容的文件")
                .hasMessageContaining("doc1.txt");
    }

    @Test
    void storeFileAllowsSameHashInDifferentKnowledgeBase() {
        byte[] content = "相同内容的文件".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file1 = new MockMultipartFile("file", "doc.txt", "text/plain", content);
        MockMultipartFile file2 = new MockMultipartFile("file", "doc.txt", "text/plain", content);

        documentService.storeFile(file1, "HR", "Alice", "admin");
        documentService.storeFile(file2, "Finance", "Bob", "admin");

        assertThat(documentService.listDocumentFiles("HR")).hasSize(1);
        assertThat(documentService.listDocumentFiles("Finance")).hasSize(1);
    }

    @Test
    void storeFileRecordsHashInManifest() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.txt", "text/plain",
                "报销流程".getBytes(StandardCharsets.UTF_8));

        documentService.storeFile(file, "HR", "Alice", "admin");

        DocumentFileInfo info = documentService.listDocumentFiles("HR").get(0);
        assertThat(info.getFileHash()).isNotBlank();
        assertThat(info.getFileHash()).hasSize(64); // SHA-256 hex is 64 chars
    }
}
