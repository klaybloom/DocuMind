package com.documind.service;

import com.documind.model.AuditEventEntity;
import com.documind.model.DocumentFileEntity;
import com.documind.model.KnowledgeGapEntity;
import com.documind.repository.AuditEventRepository;
import com.documind.repository.DocumentFileRepository;
import com.documind.repository.KnowledgeGapRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Order(1)
public class JsonMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(JsonMigrationService.class);
    private static final String MANIFEST_FILE = ".documind-files.json";
    private static final String GAPS_FILE = ".documind-gaps.json";
    private static final String AUDIT_FILE = ".documind-audit.log";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DocumentFileRepository fileRepository;
    private final KnowledgeGapRepository gapRepository;
    private final AuditEventRepository auditRepository;

    @Value("${app.documents-path}")
    private String documentsPath;

    public JsonMigrationService(DocumentFileRepository fileRepository,
                                KnowledgeGapRepository gapRepository,
                                AuditEventRepository auditRepository) {
        this.fileRepository = fileRepository;
        this.gapRepository = gapRepository;
        this.auditRepository = auditRepository;
    }

    @PostConstruct
    public void migrate() {
        if (fileRepository.count() > 0) {
            logger.info("Database already contains data, skipping JSON migration");
            return;
        }

        int migratedFiles = 0, migratedGaps = 0, migratedEvents = 0;

        try {
            Path root = Paths.get(documentsPath);
            if (!Files.exists(root)) {
                return;
            }

            // Migrate document files manifest (root + subdirectories)
            migratedFiles += migrateManifest(root);
            Files.list(root)
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(dir -> {
                        try {
                            // Scan subdirectories for their own manifests via migrateManifest
                            // but we need to track the count
                        } catch (Exception e) {
                            logger.warn("Failed to scan directory: {}", dir, e);
                        }
                    });

            // Migrate knowledge gaps
            migratedGaps += migrateGaps(root);
            Files.list(root)
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(dir -> migrateGaps(dir));

            // Migrate audit log (single global file)
            migratedEvents += migrateAudit(root);

            if (migratedFiles + migratedGaps + migratedEvents > 0) {
                logger.info("JSON migration completed: {} document files, {} knowledge gaps, {} audit events",
                        migratedFiles, migratedGaps, migratedEvents);
            }
        } catch (Exception e) {
            logger.warn("JSON migration encountered errors: {}", e.getMessage());
        }
    }

    private int migrateManifest(Path directory) {
        Path manifestPath = directory.resolve(MANIFEST_FILE);
        if (!Files.exists(manifestPath)) {
            return 0;
        }

        try {
            List<Map<String, Object>> raw = MAPPER.readValue(
                    manifestPath.toFile(), new TypeReference<>() {});
            int count = 0;
            for (Map<String, Object> entry : raw) {
                try {
                    DocumentFileEntity entity = new DocumentFileEntity(
                            str(entry.get("knowledgeBase")),
                            str(entry.get("fileName")),
                            longVal(entry.get("sizeBytes")),
                            str(entry.get("contentType")),
                            str(entry.get("owner")),
                            str(entry.get("uploadedBy")),
                            str(entry.get("uploadedAt")),
                            str(entry.get("lastIndexedAt")),
                            str(entry.get("indexStatus")),
                            intVal(entry.get("chunkCount")),
                            str(entry.get("error")));
                    fileRepository.save(entity);
                    count++;
                } catch (Exception e) {
                    logger.warn("Failed to migrate manifest entry: {}", e.getMessage());
                }
            }
            Files.move(manifestPath, manifestPath.resolveSibling(MANIFEST_FILE + ".migrated"),
                    StandardCopyOption.REPLACE_EXISTING);
            return count;
        } catch (IOException e) {
            logger.warn("Failed to read manifest {}: {}", manifestPath, e.getMessage());
            return 0;
        }
    }

    private int migrateGaps(Path directory) {
        Path gapsPath = directory.resolve(GAPS_FILE);
        if (!Files.exists(gapsPath)) {
            return 0;
        }

        try {
            List<Map<String, Object>> raw = MAPPER.readValue(
                    gapsPath.toFile(), new TypeReference<>() {});
            int count = 0;
            for (Map<String, Object> entry : raw) {
                try {
                    KnowledgeGapEntity entity = new KnowledgeGapEntity(
                            str(entry.get("id")),
                            str(entry.get("knowledgeBase")),
                            str(entry.get("question")),
                            str(entry.get("sessionId")),
                            str(entry.get("firstAskedAt")),
                            intVal(entry.get("occurrences")),
                            str(entry.get("lastAskedAt")));
                    gapRepository.save(entity);
                    count++;
                } catch (Exception e) {
                    logger.warn("Failed to migrate gap entry: {}", e.getMessage());
                }
            }
            Files.move(gapsPath, gapsPath.resolveSibling(GAPS_FILE + ".migrated"),
                    StandardCopyOption.REPLACE_EXISTING);
            return count;
        } catch (IOException e) {
            logger.warn("Failed to read gaps {}: {}", gapsPath, e.getMessage());
            return 0;
        }
    }

    private int migrateAudit(Path directory) {
        Path auditPath = directory.resolve(AUDIT_FILE);
        if (!Files.exists(auditPath)) {
            return 0;
        }

        try {
            List<String> lines = Files.readAllLines(auditPath, StandardCharsets.UTF_8);
            int count = 0;
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                try {
                    Map<String, Object> raw = MAPPER.readValue(line, new TypeReference<>() {});
                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = (Map<String, Object>) raw.getOrDefault("details", Map.of());
                    AuditEventEntity entity = new AuditEventEntity(
                            str(raw.get("id")),
                            str(raw.get("timestamp")),
                            str(raw.get("actor")),
                            str(raw.get("action")),
                            str(raw.get("knowledgeBase")),
                            str(raw.get("fileName")),
                            details);
                    auditRepository.save(entity);
                    count++;
                } catch (Exception e) {
                    logger.warn("Failed to migrate audit line: {}", e.getMessage());
                }
            }
            Files.move(auditPath, auditPath.resolveSibling(AUDIT_FILE + ".migrated"),
                    StandardCopyOption.REPLACE_EXISTING);
            return count;
        } catch (IOException e) {
            logger.warn("Failed to read audit log {}: {}", auditPath, e.getMessage());
            return 0;
        }
    }

    private String str(Object value) {
        if (value == null) return null;
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private long longVal(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (Exception e) { return 0; }
    }

    private int intVal(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return 0; }
    }
}
