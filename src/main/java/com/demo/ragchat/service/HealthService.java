package com.demo.ragchat.service;

import com.demo.ragchat.dto.HealthCheckItem;
import com.demo.ragchat.dto.HealthCheckResponse;
import com.demo.ragchat.dto.KnowledgeBaseStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthService {

    private final DocumentService documentService;
    private final RagService ragService;

    @Value("${app.documents-path}")
    private String documentsPath;

    @Value("${app.deepseek.api-key}")
    private String apiKey;

    @Value("${app.deepseek.base-url}")
    private String baseUrl;

    @Value("${app.deepseek.model}")
    private String modelName;

    @Value("${app.deepseek.timeout-seconds:60}")
    private long deepSeekTimeoutSeconds;

    @Value("${app.chat.rate-limit-per-minute:30}")
    private int chatRateLimitPerMinute;

    @Value("${app.chat.stream.timeout-seconds:120}")
    private long chatStreamTimeoutSeconds;

    @Value("${app.chat.stream.core-pool-size:4}")
    private int chatStreamCorePoolSize;

    @Value("${app.chat.stream.max-pool-size:8}")
    private int chatStreamMaxPoolSize;

    @Value("${app.chat.stream.queue-capacity:100}")
    private int chatStreamQueueCapacity;

    public HealthService(DocumentService documentService, RagService ragService) {
        this.documentService = documentService;
        this.ragService = ragService;
    }

    public HealthCheckResponse liveness() {
        return new HealthCheckResponse("UP", Instant.now().toString(), List.of());
    }

    public HealthCheckResponse readiness() {
        List<HealthCheckItem> checks = new ArrayList<>();
        checks.add(deepSeekConfigCheck());
        checks.add(documentStorageCheck());
        checks.add(indexCheck());
        checks.add(knowledgeBaseCheck());
        checks.add(chatRuntimeCheck());
        return new HealthCheckResponse(overallStatus(checks), Instant.now().toString(), checks);
    }

    private HealthCheckItem deepSeekConfigCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("baseUrlConfigured", !isBlank(baseUrl));
        details.put("modelConfigured", !isBlank(modelName));
        details.put("apiKeyConfigured", !isBlank(apiKey));
        details.put("model", isBlank(modelName) ? null : modelName);

        if (isBlank(apiKey) || isBlank(baseUrl) || isBlank(modelName)) {
            return HealthCheckItem.down("deepseek-config", "DeepSeek API 配置不完整", details);
        }
        return HealthCheckItem.up("deepseek-config", "DeepSeek API 配置已提供", details);
    }

    private HealthCheckItem documentStorageCheck() {
        Path path = Paths.get(documentsPath);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", path.toString());
        details.put("exists", Files.exists(path));
        details.put("directory", Files.isDirectory(path));
        details.put("readable", Files.isReadable(path));
        details.put("writable", Files.isWritable(path));

        if (!Files.exists(path)) {
            return HealthCheckItem.degraded("documents-storage", "文档目录尚未创建", details);
        }
        if (!Files.isDirectory(path) || !Files.isReadable(path) || !Files.isWritable(path)) {
            return HealthCheckItem.down("documents-storage", "文档目录不可用", details);
        }
        return HealthCheckItem.up("documents-storage", "文档目录可读写", details);
    }

    private HealthCheckItem indexCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ready", ragService.isIndexReady());
        details.put("segments", ragService.indexedSegmentCount());
        if (!ragService.isIndexReady()) {
            return HealthCheckItem.down("rag-index", "索引尚未初始化", details);
        }
        return HealthCheckItem.up("rag-index", "索引已初始化", details);
    }

    private HealthCheckItem knowledgeBaseCheck() {
        List<KnowledgeBaseStatus> statuses = documentService.listKnowledgeBaseStatuses();
        int totalFiles = statuses.stream().mapToInt(KnowledgeBaseStatus::getTotalFiles).sum();
        int failedFiles = statuses.stream().mapToInt(KnowledgeBaseStatus::getFailedFiles).sum();
        int staleFiles = statuses.stream().mapToInt(KnowledgeBaseStatus::getStaleFiles).sum();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("knowledgeBases", statuses.size());
        details.put("totalFiles", totalFiles);
        details.put("failedFiles", failedFiles);
        details.put("staleFiles", staleFiles);

        if (failedFiles > 0) {
            return HealthCheckItem.degraded("knowledge-bases", "存在索引失败文件", details);
        }
        if (totalFiles == 0) {
            return HealthCheckItem.degraded("knowledge-bases", "尚未上传文档", details);
        }
        return HealthCheckItem.up("knowledge-bases", "知识库文件状态正常", details);
    }

    private HealthCheckItem chatRuntimeCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("deepSeekTimeoutSeconds", Math.max(5, deepSeekTimeoutSeconds));
        details.put("chatRateLimitPerMinute", Math.max(0, chatRateLimitPerMinute));
        details.put("streamTimeoutSeconds", Math.max(30, chatStreamTimeoutSeconds));
        details.put("streamCorePoolSize", Math.max(1, chatStreamCorePoolSize));
        details.put("streamMaxPoolSize", Math.max(Math.max(1, chatStreamCorePoolSize), chatStreamMaxPoolSize));
        details.put("streamQueueCapacity", Math.max(0, chatStreamQueueCapacity));
        return HealthCheckItem.up("chat-runtime", "问答运行参数已加载", details);
    }

    private String overallStatus(List<HealthCheckItem> checks) {
        boolean down = checks.stream().anyMatch(check -> "DOWN".equals(check.getStatus()));
        if (down) {
            return "DOWN";
        }
        boolean degraded = checks.stream().anyMatch(check -> "DEGRADED".equals(check.getStatus()));
        return degraded ? "DEGRADED" : "UP";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
