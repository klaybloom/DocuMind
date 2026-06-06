package com.demo.ragchat.service;

import com.demo.ragchat.dto.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditService {

    public static final String ACTION_CHAT_REQUEST = "CHAT_REQUEST";
    public static final String ACTION_RATE_LIMITED_CHAT_REQUEST = "RATE_LIMITED_CHAT_REQUEST";
    public static final String ACTION_CLEAR_CHAT_SESSION = "CLEAR_CHAT_SESSION";
    public static final String ACTION_UPLOAD_DOCUMENT = "UPLOAD_DOCUMENT";
    public static final String ACTION_DOWNLOAD_DOCUMENT = "DOWNLOAD_DOCUMENT";
    public static final String ACTION_DELETE_DOCUMENT = "DELETE_DOCUMENT";
    public static final String ACTION_REFRESH_INDEX = "REFRESH_INDEX";
    public static final String ACTION_GENERATE_FAQ_DRAFT = "GENERATE_FAQ_DRAFT";
    public static final String ACTION_RESOLVE_KNOWLEDGE_GAP = "RESOLVE_KNOWLEDGE_GAP";

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final String AUDIT_FILE = ".documind-audit.log";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_MAX_EVENTS = 10_000;
    private static final int MAX_DETAIL_KEY_LENGTH = 60;
    private static final int MAX_DETAIL_STRING_LENGTH = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.documents-path}")
    private String documentsPath;

    @Value("${app.audit.max-events:10000}")
    private int maxEvents;

    public synchronized void record(String actor,
                                    String action,
                                    String knowledgeBase,
                                    String fileName,
                                    Map<String, Object> details) {
        try {
            Path auditPath = auditPath();
            Files.createDirectories(auditPath.getParent());

            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now().toString(),
                    actorName(actor),
                    action,
                    knowledgeBaseName(knowledgeBase),
                    cleanFileName(fileName),
                    cleanDetails(details)
            );
            String line = objectMapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(
                    auditPath,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            trimAuditLog(auditPath);
        } catch (IOException e) {
            logger.warn("Failed to write audit event: {}", action, e);
        }
    }

    public synchronized List<AuditEvent> listRecent(int requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        Path auditPath = auditPath();
        if (!Files.exists(auditPath)) {
            return Collections.emptyList();
        }

        try {
            List<String> lines = Files.readAllLines(auditPath, StandardCharsets.UTF_8);
            List<AuditEvent> events = new ArrayList<>();
            for (int i = lines.size() - 1; i >= 0 && events.size() < limit; i--) {
                String line = lines.get(i);
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                try {
                    events.add(objectMapper.readValue(line, AuditEvent.class));
                } catch (IOException parseError) {
                    logger.warn("Skipped malformed audit event at line {}", i + 1);
                }
            }
            return events;
        } catch (IOException e) {
            logger.warn("Failed to read audit events", e);
            return Collections.emptyList();
        }
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private Path auditPath() {
        return Paths.get(documentsPath).resolve(AUDIT_FILE);
    }

    private void trimAuditLog(Path auditPath) throws IOException {
        int limit = normalizedMaxEvents();
        if (limit <= 0 || !Files.exists(auditPath)) {
            return;
        }

        List<String> lines = Files.readAllLines(auditPath, StandardCharsets.UTF_8);
        if (lines.size() <= limit) {
            return;
        }

        List<String> retained = lines.subList(lines.size() - limit, lines.size());
        Files.write(
                auditPath,
                retained,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE
        );
    }

    private int normalizedMaxEvents() {
        return maxEvents < 0 ? DEFAULT_MAX_EVENTS : maxEvents;
    }

    private String actorName(String actor) {
        return actor == null || actor.trim().isEmpty() ? "anonymous" : actor.trim();
    }

    private String knowledgeBaseName(String knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.trim().isEmpty()) {
            return DocumentService.DEFAULT_KNOWLEDGE_BASE;
        }
        String name = Paths.get(knowledgeBase.trim()).getFileName().toString();
        String sanitized = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return DocumentService.DEFAULT_KNOWLEDGE_BASE;
        }
        return sanitized.length() > 60 ? sanitized.substring(0, 60) : sanitized;
    }

    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        return Paths.get(fileName.trim()).getFileName().toString();
    }

    private Map<String, Object> cleanDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }
        return details.entrySet()
                .stream()
                .filter(entry -> isSafeDetailKey(entry.getKey()) && entry.getValue() != null)
                .filter(entry -> !"message".equalsIgnoreCase(entry.getKey()))
                .filter(entry -> !"question".equalsIgnoreCase(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> cleanDetailValue(entry.getValue()),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private Object cleanDetailValue(Object value) {
        if (value instanceof String text) {
            String normalized = text.trim();
            return normalized.length() > MAX_DETAIL_STRING_LENGTH
                    ? normalized.substring(0, MAX_DETAIL_STRING_LENGTH)
                    : normalized;
        }
        return value;
    }

    private boolean isSafeDetailKey(String key) {
        return key != null
                && key.length() <= MAX_DETAIL_KEY_LENGTH
                && key.matches("[A-Za-z0-9._-]+");
    }
}
