package com.documind.service;

import com.documind.dto.HealthCheckItem;
import com.documind.dto.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HealthServiceTest {

    @TempDir
    Path tempDir;

    private DocumentService documentService;
    private RagService ragService;
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService();
        ReflectionTestUtils.setField(documentService, "documentsPath", tempDir.toString());
        ReflectionTestUtils.setField(documentService, "staleDays", 180);

        ragService = new RagService(null, null, null, null, documentService, null);
        ReflectionTestUtils.setField(ragService, "indexReady", true);

        healthService = new HealthService(documentService, ragService);
        ReflectionTestUtils.setField(healthService, "documentsPath", tempDir.toString());
        ReflectionTestUtils.setField(healthService, "baseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(healthService, "modelName", "deepseek-v4-flash");
        ReflectionTestUtils.setField(healthService, "deepSeekTimeoutSeconds", 60L);
        ReflectionTestUtils.setField(healthService, "chatRateLimitPerMinute", 30);
        ReflectionTestUtils.setField(healthService, "chatStreamTimeoutSeconds", 120L);
        ReflectionTestUtils.setField(healthService, "chatStreamCorePoolSize", 4);
        ReflectionTestUtils.setField(healthService, "chatStreamMaxPoolSize", 8);
        ReflectionTestUtils.setField(healthService, "chatStreamQueueCapacity", 100);
    }

    @Test
    void livenessReturnsUpWithoutDetailedChecks() {
        HealthCheckResponse response = healthService.liveness();

        assertThat(response.getStatus()).isEqualTo("UP");
        assertThat(response.getChecks()).isEmpty();
    }

    @Test
    void readinessReportsDownWhenDeepSeekKeyIsMissing() {
        ReflectionTestUtils.setField(healthService, "apiKey", "");

        HealthCheckResponse response = healthService.readiness();

        assertThat(response.getStatus()).isEqualTo("DOWN");
        HealthCheckItem deepSeek = find(response, "deepseek-config");
        assertThat(deepSeek.getStatus()).isEqualTo("DOWN");
        assertThat(deepSeek.getDetails())
                .containsEntry("apiKeyConfigured", false)
                .doesNotContainKey("apiKey");
    }

    @Test
    void readinessIsDegradedWhenConfigurationIsPresentButNoDocumentsExist() throws Exception {
        Files.createDirectories(tempDir);
        ReflectionTestUtils.setField(healthService, "apiKey", "sk-test");

        HealthCheckResponse response = healthService.readiness();

        assertThat(response.getStatus()).isEqualTo("DEGRADED");
        assertThat(find(response, "deepseek-config").getStatus()).isEqualTo("UP");
        assertThat(find(response, "documents-storage").getStatus()).isEqualTo("UP");
        assertThat(find(response, "rag-index").getStatus()).isEqualTo("UP");
        HealthCheckItem knowledgeBases = find(response, "knowledge-bases");
        assertThat(knowledgeBases.getStatus()).isEqualTo("DEGRADED");
        assertThat(knowledgeBases.getDetails()).containsEntry("totalFiles", 0);
        HealthCheckItem chatRuntime = find(response, "chat-runtime");
        assertThat(chatRuntime.getStatus()).isEqualTo("UP");
        assertThat(chatRuntime.getDetails())
                .containsEntry("deepSeekTimeoutSeconds", 60L)
                .containsEntry("chatRateLimitPerMinute", 30)
                .containsEntry("streamTimeoutSeconds", 120L)
                .containsEntry("streamCorePoolSize", 4)
                .containsEntry("streamMaxPoolSize", 8)
                .containsEntry("streamQueueCapacity", 100)
                .doesNotContainKeys("apiKey", "adminPassword", "userPassword");
    }

    @Test
    void readinessNormalizesInvalidRuntimeConfigurationForDisplay() throws Exception {
        Files.createDirectories(tempDir);
        ReflectionTestUtils.setField(healthService, "apiKey", "sk-test");
        ReflectionTestUtils.setField(healthService, "deepSeekTimeoutSeconds", 0L);
        ReflectionTestUtils.setField(healthService, "chatRateLimitPerMinute", -1);
        ReflectionTestUtils.setField(healthService, "chatStreamTimeoutSeconds", 1L);
        ReflectionTestUtils.setField(healthService, "chatStreamCorePoolSize", 0);
        ReflectionTestUtils.setField(healthService, "chatStreamMaxPoolSize", 0);
        ReflectionTestUtils.setField(healthService, "chatStreamQueueCapacity", -1);

        HealthCheckItem chatRuntime = find(healthService.readiness(), "chat-runtime");

        assertThat(chatRuntime.getDetails())
                .containsEntry("deepSeekTimeoutSeconds", 5L)
                .containsEntry("chatRateLimitPerMinute", 0)
                .containsEntry("streamTimeoutSeconds", 30L)
                .containsEntry("streamCorePoolSize", 1)
                .containsEntry("streamMaxPoolSize", 1)
                .containsEntry("streamQueueCapacity", 0);
    }

    private HealthCheckItem find(HealthCheckResponse response, String name) {
        return response.getChecks()
                .stream()
                .filter(check -> name.equals(check.getName()))
                .findFirst()
                .orElseThrow();
    }
}
