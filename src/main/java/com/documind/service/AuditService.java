package com.documind.service;

import com.documind.dto.AuditEvent;
import com.documind.model.AuditEventEntity;
import com.documind.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 审计服务，统一记录上传、问答、权限变更等关键操作。
 */
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
    public static final String ACTION_CREATE_USER = "CREATE_USER";
    public static final String ACTION_UPDATE_USER = "UPDATE_USER";
    public static final String ACTION_RESET_USER_PASSWORD = "RESET_USER_PASSWORD";
    public static final String ACTION_CREATE_KNOWLEDGE_BASE = "CREATE_KNOWLEDGE_BASE";
    public static final String ACTION_UPDATE_KNOWLEDGE_BASE_OWNERS = "UPDATE_KNOWLEDGE_BASE_OWNERS";
    public static final String ACTION_ADD_KNOWLEDGE_BASE_OWNERS = "ADD_KNOWLEDGE_BASE_OWNERS";
    public static final String ACTION_TRANSFER_KNOWLEDGE_BASE_OWNER = "TRANSFER_KNOWLEDGE_BASE_OWNER";
    public static final String ACTION_UPDATE_KNOWLEDGE_BASE_MEMBERS = "UPDATE_KNOWLEDGE_BASE_MEMBERS";

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_MAX_EVENTS = 10_000;
    private static final int MAX_DETAIL_KEY_LENGTH = 60;
    private static final int MAX_DETAIL_STRING_LENGTH = 200;

    private final AuditEventRepository repository;

    @Value("${app.audit.max-events:10000}")
    private int maxEvents;

    public AuditService() {
        this.repository = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actor,
                       String action,
                       String knowledgeBase,
                       String fileName,
                       Map<String, Object> details) {
        if (repository == null) {
            return;
        }
        try {
            AuditEventEntity entity = new AuditEventEntity(
                    UUID.randomUUID().toString(),
                    Instant.now().toString(),
                    actorName(actor),
                    action,
                    knowledgeBaseName(knowledgeBase),
                    cleanFileName(fileName),
                    cleanDetails(details));
            repository.save(entity);
            trimAuditLog();
        } catch (Exception e) {
            logger.warn("Failed to write audit event: {}", action, e);
        }
    }

    public List<AuditEvent> listRecent(int requestedLimit) {
        if (repository == null) {
            return Collections.emptyList();
        }
        int limit = normalizeLimit(requestedLimit);
        try {
            return repository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit))
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Failed to read audit events", e);
            return Collections.emptyList();
        }
    }

    private void trimAuditLog() {
        int limit = normalizedMaxEvents();
        if (limit <= 0 || repository == null) {
            return;
        }
        long total = repository.count();
        if (total > limit) {
            int excess = (int) (total - limit);
            try {
                repository.deleteOldest(excess);
            } catch (Exception e) {
                logger.warn("Failed to trim audit log: {}", e.getMessage());
            }
        }
    }

    private AuditEvent toDto(AuditEventEntity entity) {
        return new AuditEvent(
                entity.getId(),
                entity.getTimestamp(),
                entity.getActor(),
                entity.getAction(),
                entity.getKnowledgeBase(),
                entity.getFileName(),
                entity.getDetails());
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
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
