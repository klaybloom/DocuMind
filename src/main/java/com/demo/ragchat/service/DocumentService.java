package com.demo.ragchat.service;

import com.demo.ragchat.dto.DocumentFileInfo;
import com.demo.ragchat.dto.FaqDraftResponse;
import com.demo.ragchat.dto.KnowledgeBaseStatus;
import com.demo.ragchat.dto.KnowledgeGapInfo;
import com.demo.ragchat.exception.FileStorageException;
import com.demo.ragchat.exception.InvalidFileException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    public static final String DEFAULT_KNOWLEDGE_BASE = "default";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_INDEXING = "INDEXING";
    public static final String STATUS_INDEXED = "INDEXED";
    public static final String STATUS_FAILED = "FAILED";

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "pdf", "txt", "doc", "docx", "ppt", "pptx", "xls", "xlsx");
    private static final String MANIFEST_FILE = ".documind-files.json";
    private static final String GAPS_FILE = ".documind-gaps.json";
    private static final int MAX_OWNER_LENGTH = 80;
    private static final int MAX_UPLOADED_BY_LENGTH = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.documents-path}")
    private String documentsPath;

    @Value("${app.documents.stale-days:180}")
    private int staleDays;

    @Value("${app.documents.max-file-size:50MB}")
    private DataSize maxFileSize;

    public void init() {
        try {
            Path path = Paths.get(documentsPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created documents directory: {}", documentsPath);
            }
        } catch (IOException e) {
            logger.error("Failed to initialize storage directory: {}", documentsPath, e);
            throw new FileStorageException("无法初始化存储目录", e);
        }
    }

    public String storeFile(MultipartFile file) {
        return storeFile(file, DEFAULT_KNOWLEDGE_BASE, null, null);
    }

    public synchronized String storeFile(MultipartFile file, String knowledgeBase) {
        return storeFile(file, knowledgeBase, null, null);
    }

    public synchronized String storeFile(MultipartFile file, String knowledgeBase, String owner, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("文件不能为空");
        }

        if (file.getSize() > maxFileSizeBytes()) {
            throw new InvalidFileException("文件大小不能超过 " + maxFileSizeText());
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new InvalidFileException("文件名无效");
        }

        String sanitizedFilename = Paths.get(originalFilename).getFileName().toString();
        String extension = getFileExtension(sanitizedFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("不支持的文件类型，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String kb = sanitizeKnowledgeBase(knowledgeBase);
        String normalizedOwner = normalizeMetadataField(owner, "负责人", MAX_OWNER_LENGTH);
        String normalizedUploadedBy = normalizeMetadataField(uploadedBy, "上传人", MAX_UPLOADED_BY_LENGTH);
        try {
            init();
            Path targetDirectory = knowledgeBasePath(kb);
            Files.createDirectories(targetDirectory);
            Path targetLocation = targetDirectory.resolve(sanitizedFilename);

            if (Files.exists(targetLocation)) {
                logger.warn("File already exists, will be replaced: {}", sanitizedFilename);
            }

            Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            upsertFileInfo(new DocumentFileInfo(
                    kb,
                    sanitizedFilename,
                    file.getSize(),
                    file.getContentType(),
                    firstNonBlank(normalizedOwner, normalizedUploadedBy),
                    normalizedUploadedBy,
                    Instant.now().toString(),
                    null,
                    STATUS_PENDING,
                    0,
                    null
            ));
            logger.info("File stored successfully: {}/{}", kb, sanitizedFilename);
            return sanitizedFilename;
        } catch (IOException ex) {
            logger.error("Failed to store file: {}", sanitizedFilename, ex);
            throw new FileStorageException("无法存储文件: " + sanitizedFilename, ex);
        }
    }

    public List<String> listFiles() {
        return listDocumentFiles(DEFAULT_KNOWLEDGE_BASE)
                .stream()
                .map(DocumentFileInfo::getFileName)
                .collect(Collectors.toList());
    }

    public synchronized List<DocumentFileInfo> listDocumentFiles() {
        return listKnowledgeBases()
                .stream()
                .flatMap(kb -> listDocumentFiles(kb).stream())
                .sorted(Comparator.comparing(DocumentFileInfo::getKnowledgeBase)
                        .thenComparing(DocumentFileInfo::getFileName))
                .collect(Collectors.toList());
    }

    public synchronized List<DocumentFileInfo> listDocumentFiles(String knowledgeBase) {
        try {
            init();
            String kb = sanitizeKnowledgeBase(knowledgeBase);
            Path directory = knowledgeBasePath(kb);
            if (!Files.exists(directory)) {
                return new ArrayList<>();
            }

            Map<String, DocumentFileInfo> manifest = readManifest(kb);
            List<DocumentFileInfo> files = Files.list(directory)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .filter(name -> ALLOWED_EXTENSIONS.contains(getFileExtension(name).toLowerCase()))
                    .map(name -> withStaleInfo(reconcileFileInfo(kb, directory.resolve(name), manifest.get(name))))
                    .sorted(Comparator.comparing(DocumentFileInfo::getFileName))
                    .collect(Collectors.toList());

            writeManifest(kb, files.stream().collect(Collectors.toMap(
                    DocumentFileInfo::getFileName,
                    info -> info,
                    (first, second) -> first,
                    LinkedHashMap::new
            )));
            logger.debug("Listed {} files from knowledge base {}", files.size(), kb);
            return files;
        } catch (IOException e) {
            logger.error("Failed to list files from directory: {}", documentsPath, e);
            return new ArrayList<>();
        }
    }

    public synchronized List<String> listKnowledgeBases() {
        try {
            init();
            List<String> knowledgeBases = new ArrayList<>();
            knowledgeBases.add(DEFAULT_KNOWLEDGE_BASE);
            Files.list(Paths.get(documentsPath))
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .forEach(knowledgeBases::add);
            return knowledgeBases.stream().distinct().collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to list knowledge bases", e);
            return List.of(DEFAULT_KNOWLEDGE_BASE);
        }
    }

    public synchronized List<KnowledgeBaseStatus> listKnowledgeBaseStatuses() {
        return listKnowledgeBases()
                .stream()
                .map(kb -> {
                    List<DocumentFileInfo> files = listDocumentFiles(kb);
                    int indexed = countStatus(files, STATUS_INDEXED);
                    int pending = countStatus(files, STATUS_PENDING);
                    int indexing = countStatus(files, STATUS_INDEXING);
                    int failed = countStatus(files, STATUS_FAILED);
                    int stale = (int) files.stream().filter(DocumentFileInfo::isStale).count();
                    int gaps = listKnowledgeGaps(kb).size();
                    return new KnowledgeBaseStatus(
                            kb,
                            files.size(),
                            indexed,
                            pending,
                            indexing,
                            failed,
                            stale,
                            gaps
                    );
                })
                .collect(Collectors.toList());
    }

    public void deleteFile(String filename) {
        deleteFile(filename, DEFAULT_KNOWLEDGE_BASE);
    }

    public synchronized Path resolveDownloadPath(String filename, String knowledgeBase) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new InvalidFileException("文件名不能为空");
        }

        String requestedFilename = filename.trim();
        String sanitizedFilename = Paths.get(requestedFilename).getFileName().toString();
        if (!requestedFilename.equals(sanitizedFilename)) {
            throw new InvalidFileException("文件名无效");
        }
        String extension = getFileExtension(sanitizedFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("不支持的文件类型");
        }

        String kb = sanitizeKnowledgeBase(knowledgeBase);
        Path filePath = knowledgeBasePath(kb).resolve(sanitizedFilename).normalize();
        Path kbPath = knowledgeBasePath(kb).normalize();
        if (!filePath.startsWith(kbPath)) {
            throw new InvalidFileException("文件名无效");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new InvalidFileException("文件不存在");
        }
        return filePath;
    }

    public synchronized boolean deleteFile(String filename, String knowledgeBase) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new InvalidFileException("文件名不能为空");
        }

        String requestedFilename = filename.trim();
        String sanitizedFilename = Paths.get(requestedFilename).getFileName().toString();
        if (!requestedFilename.equals(sanitizedFilename)) {
            throw new InvalidFileException("文件名无效");
        }
        String extension = getFileExtension(sanitizedFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("不支持的文件类型");
        }
        String kb = sanitizeKnowledgeBase(knowledgeBase);

        try {
            Path filePath = knowledgeBasePath(kb).resolve(sanitizedFilename).normalize();
            Path kbPath = knowledgeBasePath(kb).normalize();
            if (!filePath.startsWith(kbPath)) {
                throw new InvalidFileException("文件名无效");
            }
            boolean deleted = Files.deleteIfExists(filePath);
            Map<String, DocumentFileInfo> manifest = readManifest(kb);
            manifest.remove(sanitizedFilename);
            writeManifest(kb, manifest);
            if (deleted) {
                logger.info("File deleted successfully: {}/{}", kb, sanitizedFilename);
            } else {
                logger.warn("File not found for deletion: {}/{}", kb, sanitizedFilename);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", sanitizedFilename, e);
            throw new FileStorageException("无法删除文件: " + sanitizedFilename, e);
        }
    }

    public Path resolveDocumentPath(DocumentFileInfo fileInfo) {
        String kb = sanitizeKnowledgeBase(fileInfo.getKnowledgeBase());
        String filename = Paths.get(fileInfo.getFileName()).getFileName().toString();
        return knowledgeBasePath(kb).resolve(filename);
    }

    public synchronized void markIndexing(DocumentFileInfo fileInfo) {
        updateIndexState(fileInfo.getKnowledgeBase(), fileInfo.getFileName(), STATUS_INDEXING, 0, null);
    }

    public synchronized void markIndexed(DocumentFileInfo fileInfo, int chunkCount) {
        updateIndexState(fileInfo.getKnowledgeBase(), fileInfo.getFileName(), STATUS_INDEXED, chunkCount, null);
    }

    public synchronized void markIndexFailed(DocumentFileInfo fileInfo, String error) {
        updateIndexState(fileInfo.getKnowledgeBase(), fileInfo.getFileName(), STATUS_FAILED, 0, error);
    }

    public String normalizeKnowledgeBase(String knowledgeBase) {
        return sanitizeKnowledgeBase(knowledgeBase);
    }

    public synchronized void recordKnowledgeGap(String knowledgeBase, String question, String sessionId) {
        if (question == null || question.trim().isEmpty()) {
            return;
        }

        try {
            String kb = sanitizeKnowledgeBase(knowledgeBase);
            String normalizedQuestion = question.trim().replaceAll("\\s+", " ");
            List<KnowledgeGapInfo> gaps = readKnowledgeGaps(kb);
            String now = Instant.now().toString();
            KnowledgeGapInfo existing = gaps.stream()
                    .filter(gap -> normalizedQuestion.equalsIgnoreCase(gap.getQuestion()))
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                gaps.add(new KnowledgeGapInfo(
                        UUID.randomUUID().toString(),
                        kb,
                        normalizedQuestion,
                        sessionId,
                        now,
                        1,
                        now
                ));
            } else {
                existing.setOccurrences(existing.getOccurrences() + 1);
                existing.setLastAskedAt(now);
                existing.setSessionId(sessionId);
            }
            writeKnowledgeGaps(kb, gaps);
        } catch (IOException e) {
            logger.error("Failed to record knowledge gap, knowledgeBase={}, questionLength={}",
                    knowledgeBase,
                    question == null ? 0 : question.length(),
                    e);
        }
    }

    public synchronized List<KnowledgeGapInfo> listKnowledgeGaps(String knowledgeBase) {
        try {
            String kb = sanitizeKnowledgeBase(knowledgeBase);
            return readKnowledgeGaps(kb)
                    .stream()
                    .sorted(Comparator.comparing(KnowledgeGapInfo::getLastAskedAt).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to list knowledge gaps", e);
            return new ArrayList<>();
        }
    }

    public synchronized List<KnowledgeGapInfo> listKnowledgeGaps() {
        return listKnowledgeBases()
                .stream()
                .flatMap(kb -> listKnowledgeGaps(kb).stream())
                .sorted(Comparator.comparing(KnowledgeGapInfo::getLastAskedAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized KnowledgeGapInfo resolveKnowledgeGap(String knowledgeBase, String gapId) {
        if (gapId == null || gapId.trim().isEmpty()) {
            throw new InvalidFileException("知识缺口 ID 不能为空");
        }

        try {
            String kb = sanitizeKnowledgeBase(knowledgeBase);
            List<KnowledgeGapInfo> gaps = readKnowledgeGaps(kb);
            KnowledgeGapInfo removed = null;
            List<KnowledgeGapInfo> retained = new ArrayList<>();
            for (KnowledgeGapInfo gap : gaps) {
                if (removed == null && gapId.trim().equals(gap.getId())) {
                    removed = gap;
                } else {
                    retained.add(gap);
                }
            }
            if (removed != null) {
                writeKnowledgeGaps(kb, retained);
            }
            return removed;
        } catch (IOException e) {
            logger.error("Failed to resolve knowledge gap, knowledgeBase={}, gapId={}", knowledgeBase, gapId, e);
            throw new FileStorageException("无法处理知识缺口", e);
        }
    }

    public synchronized FaqDraftResponse generateFaqDraft(String knowledgeBase) {
        String kb = sanitizeKnowledgeBase(knowledgeBase);
        List<KnowledgeGapInfo> gaps = listKnowledgeGaps(kb);
        List<String> owners = suggestOwners(kb);
        String ownerText = owners.isEmpty() ? "待指定" : String.join("、", owners);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ")
                .append(kb)
                .append(" 知识库 FAQ 草稿\n\n");
        markdown.append("> 由 DocuMind 根据知识缺口生成。答案需要业务负责人补充确认。\n\n");

        if (gaps.isEmpty()) {
            markdown.append("当前暂无知识缺口。\n");
        } else {
            for (int i = 0; i < gaps.size(); i++) {
                KnowledgeGapInfo gap = gaps.get(i);
                markdown.append("## ")
                        .append(i + 1)
                        .append(". ")
                        .append(gap.getQuestion())
                        .append("\n\n");
                markdown.append("- 出现次数：")
                        .append(gap.getOccurrences())
                        .append("\n");
                markdown.append("- 最近提问：")
                        .append(gap.getLastAskedAt())
                        .append("\n");
                markdown.append("- 建议负责人：")
                        .append(ownerText)
                        .append("\n");
                markdown.append("- 待补充答案：\n\n\n");
            }
        }

        return new FaqDraftResponse(kb, Instant.now().toString(), gaps.size(), markdown.toString());
    }

    public synchronized List<String> suggestOwners(String knowledgeBase) {
        String kb = sanitizeKnowledgeBase(knowledgeBase);
        return listDocumentFiles(kb)
                .stream()
                .map(DocumentFileInfo::getOwner)
                .filter(value -> value != null && !value.trim().isEmpty())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private void upsertFileInfo(DocumentFileInfo fileInfo) throws IOException {
        Map<String, DocumentFileInfo> manifest = readManifest(fileInfo.getKnowledgeBase());
        manifest.put(fileInfo.getFileName(), fileInfo);
        writeManifest(fileInfo.getKnowledgeBase(), manifest);
    }

    private void updateIndexState(String knowledgeBase,
                                  String filename,
                                  String status,
                                  int chunkCount,
                                  String error) {
        try {
            String kb = sanitizeKnowledgeBase(knowledgeBase);
            String sanitizedFilename = Paths.get(filename).getFileName().toString();
            Map<String, DocumentFileInfo> manifest = readManifest(kb);
            DocumentFileInfo info = manifest.getOrDefault(
                    sanitizedFilename,
                    discoveredFileInfo(kb, knowledgeBasePath(kb).resolve(sanitizedFilename))
            );
            info.setIndexStatus(status);
            info.setLastIndexedAt(Instant.now().toString());
            info.setChunkCount(chunkCount);
            info.setError(error);
            manifest.put(sanitizedFilename, info);
            writeManifest(kb, manifest);
        } catch (IOException e) {
            logger.error("Failed to update index status: {}/{}", knowledgeBase, filename, e);
        }
    }

    private DocumentFileInfo discoveredFileInfo(String knowledgeBase, Path filePath) {
        try {
            return new DocumentFileInfo(
                    knowledgeBase,
                    filePath.getFileName().toString(),
                    Files.exists(filePath) ? Files.size(filePath) : 0,
                    Files.exists(filePath) ? Files.probeContentType(filePath) : null,
                    null,
                    null,
                    Files.exists(filePath) ? Files.getLastModifiedTime(filePath).toInstant().toString() : Instant.now().toString(),
                    null,
                    STATUS_PENDING,
                    0,
                    null
            );
        } catch (IOException e) {
            return new DocumentFileInfo(
                    knowledgeBase,
                    filePath.getFileName().toString(),
                    0,
                    null,
                    null,
                    null,
                    Instant.now().toString(),
                    null,
                    STATUS_PENDING,
                    0,
                    null
            );
        }
    }

    private DocumentFileInfo reconcileFileInfo(String knowledgeBase, Path filePath, DocumentFileInfo existing) {
        DocumentFileInfo discovered = discoveredFileInfo(knowledgeBase, filePath);
        if (existing == null) {
            return discovered;
        }

        existing.setKnowledgeBase(knowledgeBase);
        existing.setFileName(filePath.getFileName().toString());
        existing.setContentType(discovered.getContentType());
        if (existing.getOwner() == null || existing.getOwner().trim().isEmpty()) {
            existing.setOwner(existing.getUploadedBy());
        }
        if (existing.getSizeBytes() != discovered.getSizeBytes()) {
            existing.setSizeBytes(discovered.getSizeBytes());
            existing.setUploadedAt(discovered.getUploadedAt());
            existing.setIndexStatus(STATUS_PENDING);
            existing.setChunkCount(0);
            existing.setError(null);
        }
        return existing;
    }

    private DocumentFileInfo withStaleInfo(DocumentFileInfo fileInfo) {
        long days = daysSince(fileInfo.getUploadedAt());
        fileInfo.setDaysSinceUpload(days);
        fileInfo.setStale(staleDays > 0 && days >= staleDays);
        return fileInfo;
    }

    private long daysSince(String instantValue) {
        if (instantValue == null || instantValue.trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Duration.between(Instant.parse(instantValue), Instant.now()).toDays());
        } catch (Exception e) {
            return 0;
        }
    }

    private int countStatus(List<DocumentFileInfo> files, String status) {
        return (int) files.stream()
                .filter(file -> status.equals(file.getIndexStatus()))
                .count();
    }

    private Map<String, DocumentFileInfo> readManifest(String knowledgeBase) throws IOException {
        String kb = sanitizeKnowledgeBase(knowledgeBase);
        Path manifestPath = knowledgeBasePath(kb).resolve(MANIFEST_FILE);
        if (!Files.exists(manifestPath)) {
            return new LinkedHashMap<>();
        }

        List<DocumentFileInfo> infos = objectMapper.readValue(
                manifestPath.toFile(),
                new TypeReference<List<DocumentFileInfo>>() {
                }
        );
        return infos.stream().collect(Collectors.toMap(
                DocumentFileInfo::getFileName,
                info -> info,
                (first, second) -> first,
                LinkedHashMap::new
        ));
    }

    private void writeManifest(String knowledgeBase, Map<String, DocumentFileInfo> manifest) throws IOException {
        Path directory = knowledgeBasePath(knowledgeBase);
        Files.createDirectories(directory);
        Path manifestPath = directory.resolve(MANIFEST_FILE);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), new ArrayList<>(manifest.values()));
    }

    private List<KnowledgeGapInfo> readKnowledgeGaps(String knowledgeBase) throws IOException {
        Path gapsPath = knowledgeBasePath(knowledgeBase).resolve(GAPS_FILE);
        if (!Files.exists(gapsPath)) {
            return new ArrayList<>();
        }

        return objectMapper.readValue(
                gapsPath.toFile(),
                new TypeReference<List<KnowledgeGapInfo>>() {
                }
        );
    }

    private void writeKnowledgeGaps(String knowledgeBase, List<KnowledgeGapInfo> gaps) throws IOException {
        Path directory = knowledgeBasePath(knowledgeBase);
        Files.createDirectories(directory);
        Path gapsPath = directory.resolve(GAPS_FILE);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(gapsPath.toFile(), gaps);
    }

    private Path knowledgeBasePath(String knowledgeBase) {
        String kb = sanitizeKnowledgeBase(knowledgeBase);
        Path root = Paths.get(documentsPath);
        if (DEFAULT_KNOWLEDGE_BASE.equals(kb)) {
            return root;
        }
        return root.resolve(kb);
    }

    private String sanitizeKnowledgeBase(String knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.trim().isEmpty()) {
            return DEFAULT_KNOWLEDGE_BASE;
        }

        String requestedName = knowledgeBase.trim();
        String sanitizedName = Paths.get(requestedName).getFileName().toString();
        if (!requestedName.equals(sanitizedName)) {
            throw new InvalidFileException("知识库名称无效");
        }
        if (sanitizedName.isBlank() || ".".equals(sanitizedName) || "..".equals(sanitizedName)) {
            throw new InvalidFileException("知识库名称无效");
        }
        if (sanitizedName.length() > 60) {
            throw new InvalidFileException("知识库名称长度不能超过60字符");
        }
        if (!sanitizedName.matches("[\\p{L}\\p{N}._-]+")) {
            throw new InvalidFileException("知识库名称只能包含文字、数字、点、下划线和连字符");
        }
        return sanitizedName;
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }

    private String normalizeMetadataField(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new InvalidFileException(fieldName + "长度不能超过" + maxLength + "字符");
        }
        return normalized;
    }

    private long maxFileSizeBytes() {
        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            return DataSize.ofMegabytes(50).toBytes();
        }
        return maxFileSize.toBytes();
    }

    private String maxFileSizeText() {
        long bytes = maxFileSizeBytes();
        long megabyte = 1024L * 1024L;
        if (bytes >= megabyte && bytes % megabyte == 0) {
            return (bytes / megabyte) + "MB";
        }
        return bytes + " 字节";
    }
}
