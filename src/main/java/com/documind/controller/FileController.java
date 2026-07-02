package com.documind.controller;

import com.documind.dto.AuditEvent;
import com.documind.dto.DocumentFileInfo;
import com.documind.dto.FaqDraftResponse;
import com.documind.dto.FileUploadResponse;
import com.documind.dto.KnowledgeBaseStatus;
import com.documind.dto.KnowledgeGapInfo;
import com.documind.exception.FileStorageException;
import com.documind.exception.InvalidFileException;
import com.documind.service.AuditService;
import com.documind.service.DocumentService;
import com.documind.service.KnowledgeBaseAccessService;
import com.documind.service.KnowledgeBaseManagementService;
import com.documind.service.RagService;
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
import java.util.Set;

/**
 * 文件与知识库接口，处理上传、列表、下载、删除、索引和知识缺口查询。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final DocumentService documentService;
    private final RagService ragService;
    private final AuditService auditService;
    private final KnowledgeBaseAccessService knowledgeBaseAccessService;
    private final KnowledgeBaseManagementService knowledgeBaseManagementService;

    public FileController(DocumentService documentService,
                          RagService ragService,
                          AuditService auditService,
                          KnowledgeBaseAccessService knowledgeBaseAccessService,
                          KnowledgeBaseManagementService knowledgeBaseManagementService) {
        this.documentService = documentService;
        this.ragService = ragService;
        this.auditService = auditService;
        this.knowledgeBaseAccessService = knowledgeBaseAccessService;
        this.knowledgeBaseManagementService = knowledgeBaseManagementService;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                                         @RequestParam(value = "owner", required = false) String owner,
                                                         Authentication authentication,
                                                         Principal principal) {
        try {
            logger.info("Uploading file: {} to knowledge base: {}", file.getOriginalFilename(), knowledgeBase);
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(FileUploadResponse.error("当前账号没有该知识库管理权限"));
            }
            knowledgeBaseManagementService.ensureMetadata(kb, authentication);
            String uploadedBy = principal == null ? null : principal.getName();
            String fileName = documentService.storeFile(file, knowledgeBase, owner, uploadedBy);
            ragService.reindexDocument(fileName, kb);
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
    public ResponseEntity<?> listFiles(@RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                       Authentication authentication) {
        try {
            List<DocumentFileInfo> files;
            if (knowledgeBase == null || knowledgeBase.trim().isEmpty()) {
                List<String> manageable = knowledgeBaseManagementService.manageableKnowledgeBases(authentication);
                if (manageable.isEmpty()) {
                    return forbidden();
                }
                Set<String> allowed = Set.copyOf(manageable);
                files = documentService.listDocumentFiles()
                        .stream()
                        .filter(file -> allowed.contains(file.getKnowledgeBase()))
                        .toList();
            } else {
                String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
                if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                    return forbidden();
                }
                files = documentService.listDocumentFiles(kb);
            }
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
            knowledgeBaseManagementService.syncKnownKnowledgeBases();
            List<String> knowledgeBases = knowledgeBaseManagementService.allKnowledgeBaseNames();
            if (knowledgeBases.isEmpty()) {
                knowledgeBases = documentService.listKnowledgeBases();
            }
            return ResponseEntity.ok(knowledgeBaseAccessService.filterAccessible(
                    authentication,
                    knowledgeBases
            ));
        } catch (Exception e) {
            logger.error("Error listing knowledge bases", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> listKnowledgeBaseStatuses(Authentication authentication) {
        try {
            List<String> manageable = knowledgeBaseManagementService.manageableKnowledgeBases(authentication);
            if (manageable.isEmpty()) {
                return forbidden();
            }
            Set<String> allowed = Set.copyOf(manageable);
            return ResponseEntity.ok(documentService.listKnowledgeBaseStatuses()
                    .stream()
                    .filter(status -> allowed.contains(status.getKnowledgeBase()))
                    .toList());
        } catch (Exception e) {
            logger.error("Error listing knowledge base statuses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/gaps")
    public ResponseEntity<?> listKnowledgeGaps(@RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                               Authentication authentication) {
        try {
            List<KnowledgeGapInfo> gaps;
            if (knowledgeBase == null || knowledgeBase.trim().isEmpty()) {
                List<String> manageable = knowledgeBaseManagementService.manageableKnowledgeBases(authentication);
                if (manageable.isEmpty()) {
                    return forbidden();
                }
                Set<String> allowed = Set.copyOf(manageable);
                gaps = documentService.listKnowledgeGaps()
                        .stream()
                        .filter(gap -> allowed.contains(gap.getKnowledgeBase()))
                        .toList();
            } else {
                String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
                if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                    return forbidden();
                }
                gaps = documentService.listKnowledgeGaps(kb);
            }
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
                                              Authentication authentication,
                                              Principal principal) {
        try {
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                return forbidden();
            }
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
                                                                   Authentication authentication,
                                                                   Principal principal) {
        try {
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                return forbidden();
            }
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
    public ResponseEntity<Map<String, String>> refreshIndex(Authentication authentication,
                                                           Principal principal) {
        try {
            if (!isAdmin(authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "只有管理员可以全量刷新索引"));
            }
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
                                                 Authentication authentication,
                                                 Principal principal) {
        try {
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
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
                                                          Authentication authentication,
                                                          Principal principal) {
        try {
            logger.info("Deleting file: {} from knowledge base: {}", filename, knowledgeBase);
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                return forbidden();
            }
            boolean deleted = documentService.deleteFile(filename, knowledgeBase);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "文件不存在"));
            }
            ragService.removeDocument(filename, kb);
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

    @PostMapping("/{filename}/reindex")
    public ResponseEntity<Map<String, String>> reindexFile(@PathVariable String filename,
                                                            @RequestParam(value = "knowledgeBase", required = false) String knowledgeBase,
                                                            Authentication authentication,
                                                            Principal principal) {
        try {
            logger.info("Reindexing file: {} from knowledge base: {}", filename, knowledgeBase);
            String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
            if (!knowledgeBaseManagementService.canManage(authentication, kb)) {
                return forbidden();
            }
            ragService.reindexDocument(filename, kb);
            auditService.record(
                    actor(principal),
                    "REINDEX_DOCUMENT",
                    kb,
                    filename,
                    Map.of()
            );
            return ResponseEntity.ok(Map.of("message", "文件重新索引成功"));
        } catch (InvalidFileException e) {
            logger.warn("Invalid file reindex attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("File reindex error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "重新索引失败，请稍后重试"));
        }
    }

    @GetMapping("/audit")
    public ResponseEntity<?> listAuditEvents(@RequestParam(value = "limit", required = false, defaultValue = "100") int limit,
                                             Authentication authentication) {
        try {
            if (isAdmin(authentication)) {
                return ResponseEntity.ok(auditService.listRecent(limit));
            }
            List<String> manageable = knowledgeBaseManagementService.manageableKnowledgeBases(authentication);
            if (manageable.isEmpty()) {
                return forbidden();
            }
            Set<String> allowed = Set.copyOf(manageable);
            return ResponseEntity.ok(auditService.listRecent(Math.max(limit, 100))
                    .stream()
                    .filter(event -> ownerVisibleAuditEvent(event, allowed))
                    .limit(limit)
                    .toList());
        } catch (Exception e) {
            logger.error("Error listing audit events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(KnowledgeBaseManagementService.KnowledgeBaseManagementException.class)
    public ResponseEntity<Map<String, String>> handleKnowledgeBaseException(
            KnowledgeBaseManagementService.KnowledgeBaseManagementException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
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

    private ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "当前账号没有知识库管理权限"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private boolean ownerVisibleAuditEvent(AuditEvent event, Set<String> allowedKnowledgeBases) {
        if (event.getKnowledgeBase() == null || !allowedKnowledgeBases.contains(event.getKnowledgeBase())) {
            return false;
        }
        return !AuditService.ACTION_CREATE_USER.equals(event.getAction())
                && !AuditService.ACTION_UPDATE_USER.equals(event.getAction())
                && !AuditService.ACTION_RESET_USER_PASSWORD.equals(event.getAction());
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
