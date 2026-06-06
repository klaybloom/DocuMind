package com.demo.ragchat.controller;

import com.demo.ragchat.dto.AuditEvent;
import com.demo.ragchat.dto.DocumentFileInfo;
import com.demo.ragchat.dto.FaqDraftResponse;
import com.demo.ragchat.dto.FileUploadResponse;
import com.demo.ragchat.dto.KnowledgeBaseStatus;
import com.demo.ragchat.dto.KnowledgeGapInfo;
import com.demo.ragchat.exception.FileStorageException;
import com.demo.ragchat.exception.InvalidFileException;
import com.demo.ragchat.service.AuditService;
import com.demo.ragchat.service.DocumentService;
import com.demo.ragchat.service.KnowledgeBaseAccessService;
import com.demo.ragchat.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final DocumentService documentService;
    private final RagService ragService;
    private final AuditService auditService;
    private final KnowledgeBaseAccessService knowledgeBaseAccessService;

    public FileController(DocumentService documentService,
                          RagService ragService,
                          AuditService auditService,
                          KnowledgeBaseAccessService knowledgeBaseAccessService) {
        this.documentService = documentService;
        this.ragService = ragService;
        this.auditService = auditService;
        this.knowledgeBaseAccessService = knowledgeBaseAccessService;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                                         @RequestParam(value = "owner", required = false) String owner,
                                                         Principal principal) {
        try {
            logger.info("Uploading file: {} to knowledge base: {}", file.getOriginalFilename(), knowledgeBase);
            String uploadedBy = principal == null ? null : principal.getName();
            String fileName = documentService.storeFile(file, knowledgeBase, owner, uploadedBy);
            ragService.refreshIndex();
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            Map<String, Object> details = details();
            putIfPresent(details, "owner", owner);
            putIfPresent(details, "contentType", file.getContentType());
            details.put("sizeBytes", file.getSize());
            auditService.record(
                    actor(principal),
                    AuditService.ACTION_UPLOAD_DOCUMENT,
                    kb,
                    fileName,
                    details
            );
            return ResponseEntity.ok(FileUploadResponse.success("文件上传成功", fileName));
        } catch (InvalidFileException e) {
            logger.warn("Invalid file upload attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(FileUploadResponse.error(e.getMessage()));
        } catch (FileStorageException e) {
            logger.error("File storage error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FileUploadResponse.error("文件存储失败"));
        } catch (Exception e) {
            logger.error("Unexpected error during file upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FileUploadResponse.error("上传失败，请稍后重试"));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listFiles(@RequestParam(value = "knowledgeBase", required = false) String knowledgeBase) {
        try {
            List<DocumentFileInfo> files = knowledgeBase == null || knowledgeBase.trim().isEmpty()
                    ? documentService.listDocumentFiles()
                    : documentService.listDocumentFiles(knowledgeBase);
            return ResponseEntity.ok(files);
        } catch (InvalidFileException e) {
            logger.warn("Invalid file list request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error listing files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/knowledge-bases")
    public ResponseEntity<List<String>> listKnowledgeBases(Authentication authentication) {
        try {
            return ResponseEntity.ok(knowledgeBaseAccessService.filterAccessible(
                    authentication,
                    documentService.listKnowledgeBases()
            ));
        } catch (Exception e) {
            logger.error("Error listing knowledge bases", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<List<KnowledgeBaseStatus>> listKnowledgeBaseStatuses() {
        try {
            return ResponseEntity.ok(documentService.listKnowledgeBaseStatuses());
        } catch (Exception e) {
            logger.error("Error listing knowledge base statuses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/gaps")
    public ResponseEntity<?> listKnowledgeGaps(@RequestParam(value = "knowledgeBase", required = false) String knowledgeBase) {
        try {
            List<KnowledgeGapInfo> gaps = knowledgeBase == null || knowledgeBase.trim().isEmpty()
                    ? documentService.listKnowledgeGaps()
                    : documentService.listKnowledgeGaps(knowledgeBase);
            return ResponseEntity.ok(gaps);
        } catch (InvalidFileException e) {
            logger.warn("Invalid knowledge gap list request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error listing knowledge gaps", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/faq-draft")
    public ResponseEntity<?> generateFaqDraft(@RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                              Principal principal) {
        try {
            FaqDraftResponse draft = documentService.generateFaqDraft(knowledgeBase);
            auditService.record(
                    actor(principal),
                    AuditService.ACTION_GENERATE_FAQ_DRAFT,
                    draft.getKnowledgeBase(),
                    null,
                    Map.of("questionCount", draft.getQuestionCount())
            );
            return ResponseEntity.ok(draft);
        } catch (InvalidFileException e) {
            logger.warn("Invalid FAQ draft request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating FAQ draft", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/gaps/{gapId}")
    public ResponseEntity<Map<String, String>> resolveKnowledgeGap(@PathVariable String gapId,
                                                                   @RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                                                   Principal principal) {
        try {
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            KnowledgeGapInfo removed = documentService.resolveKnowledgeGap(kb, gapId);
            if (removed == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "知识缺口不存在"));
            }
            auditService.record(
                    actor(principal),
                    AuditService.ACTION_RESOLVE_KNOWLEDGE_GAP,
                    kb,
                    null,
                    Map.of(
                            "gapId", removed.getId(),
                            "questionLength", safeLength(removed.getQuestion()),
                            "occurrences", removed.getOccurrences()
                    )
            );
            return ResponseEntity.ok(Map.of("message", "知识缺口已标记为已处理"));
        } catch (InvalidFileException e) {
            logger.warn("Invalid knowledge gap resolve attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error resolving knowledge gap", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "处理知识缺口失败"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshIndex(Principal principal) {
        try {
            logger.info("Refreshing index");
            ragService.refreshIndex();
            auditService.record(
                    actor(principal),
                    AuditService.ACTION_REFRESH_INDEX,
                    null,
                    null,
                    Map.of("knowledgeBases", documentService.listKnowledgeBases().size())
            );
            return ResponseEntity.ok(Map.of("message", "索引刷新成功"));
        } catch (Exception e) {
            logger.error("Error refreshing index", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "索引刷新失败"));
        }
    }

    @GetMapping("/{filename}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename,
                                                 @RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                                 Principal principal) {
        try {
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            Path path = documentService.resolveDownloadPath(filename, knowledgeBase);
            Resource resource = new FileSystemResource(path);
            auditService.record(
                    actor(principal),
                    AuditService.ACTION_DOWNLOAD_DOCUMENT,
                    kb,
                    path.getFileName().toString(),
                    Map.of("sizeBytes", Files.size(path))
            );
            return ResponseEntity.ok()
                    .contentType(downloadMediaType(path))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(path.getFileName().toString())
                            .build()
                            .toString())
                    .body(resource);
        } catch (InvalidFileException e) {
            logger.warn("Invalid file download attempt: {}", e.getMessage());
            if ("文件不存在".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            logger.error("File download error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error during file download", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable String filename,
                                                          @RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                                          Principal principal) {
        try {
            logger.info("Deleting file: {} from knowledge base: {}", filename, knowledgeBase);
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            boolean deleted = documentService.deleteFile(filename, knowledgeBase);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "文件不存在"));
            }
            ragService.refreshIndex();
            auditService.record(
                    actor(principal),
                    AuditService.ACTION_DELETE_DOCUMENT,
                    kb,
                    filename,
                    Map.of()
            );
            return ResponseEntity.ok(Map.of("message", "文件删除成功"));
        } catch (InvalidFileException e) {
            logger.warn("Invalid file deletion attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (FileStorageException e) {
            logger.error("File deletion error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文件删除失败"));
        } catch (Exception e) {
            logger.error("Unexpected error during file deletion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除失败，请稍后重试"));
        }
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditEvent>> listAuditEvents(@RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
        try {
            return ResponseEntity.ok(auditService.listRecent(limit));
        } catch (Exception e) {
            logger.error("Error listing audit events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Map<String, Object> details() {
        return new LinkedHashMap<>();
    }

    private void putIfPresent(Map<String, Object> details, String key, Object value) {
        if (value != null && !(value instanceof String text && text.trim().isEmpty())) {
            details.put(key, value);
        }
    }

    private String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private MediaType downloadMediaType(Path path) throws IOException {
        String contentType = Files.probeContentType(path);
        if (contentType == null || contentType.trim().isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(contentType);
    }
}
