package com.demo.ragchat.service;

import com.demo.ragchat.dto.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest {

    @TempDir
    Path tempDir;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService();
        ReflectionTestUtils.setField(auditService, "documentsPath", tempDir.toString());
        ReflectionTestUtils.setField(auditService, "maxEvents", 10_000);
    }

    @Test
    void recordWritesAuditEventWithoutFullQuestionOrMessage() throws Exception {
        auditService.record(
                "admin",
                AuditService.ACTION_CHAT_REQUEST,
                "HR",
                null,
                Map.of(
                        "question", "完整问题不应进入审计文件",
                        "message", "完整消息不应进入审计文件",
                        "messageLength", 12,
                        "sessionId", "session-1"
                )
        );

        Path auditPath = tempDir.resolve(".documind-audit.log");
        assertThat(auditPath).exists();
        String raw = Files.readString(auditPath, StandardCharsets.UTF_8);
        assertThat(raw)
                .contains(AuditService.ACTION_CHAT_REQUEST)
                .contains("messageLength")
                .contains("session-1")
                .doesNotContain("完整问题不应进入审计文件")
                .doesNotContain("完整消息不应进入审计文件");

        List<AuditEvent> events = auditService.listRecent(10);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getActor()).isEqualTo("admin");
        assertThat(events.get(0).getKnowledgeBase()).isEqualTo("HR");
        assertThat(events.get(0).getDetails()).containsEntry("messageLength", 12);
    }

    @Test
    void recordRateLimitedChatEventWithoutFullQuestionOrMessage() throws Exception {
        auditService.record(
                "reader",
                AuditService.ACTION_RATE_LIMITED_CHAT_REQUEST,
                "HR",
                null,
                Map.of(
                        "question", "完整问题不应进入审计文件",
                        "message", "完整消息不应进入审计文件",
                        "messageLength", 12,
                        "streaming", true,
                        "limit", 30,
                        "retryAfterSeconds", 12
                )
        );

        Path auditPath = tempDir.resolve(".documind-audit.log");
        String raw = Files.readString(auditPath, StandardCharsets.UTF_8);
        assertThat(raw)
                .contains(AuditService.ACTION_RATE_LIMITED_CHAT_REQUEST)
                .contains("retryAfterSeconds")
                .doesNotContain("完整问题不应进入审计文件")
                .doesNotContain("完整消息不应进入审计文件");

        AuditEvent event = auditService.listRecent(10).get(0);
        assertThat(event.getActor()).isEqualTo("reader");
        assertThat(event.getDetails())
                .containsEntry("streaming", true)
                .containsEntry("limit", 30)
                .containsEntry("retryAfterSeconds", 12);
    }

    @Test
    void listRecentReturnsNewestEventsFirstAndRespectsLimit() {
        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, "HR", "one.txt", Map.of());
        auditService.record("admin", AuditService.ACTION_DELETE_DOCUMENT, "HR", "two.txt", Map.of());

        List<AuditEvent> events = auditService.listRecent(1);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo(AuditService.ACTION_DELETE_DOCUMENT);
        assertThat(events.get(0).getFileName()).isEqualTo("two.txt");
    }

    @Test
    void recordCleansDetailKeysAndStringValues() {
        auditService.record(
                "admin",
                AuditService.ACTION_UPLOAD_DOCUMENT,
                "HR",
                "policy.txt",
                Map.of(
                        "owner", " Alice ",
                        "bad key", "ignored",
                        "x".repeat(61), "ignored",
                        "note", "n".repeat(250)
                )
        );

        AuditEvent event = auditService.listRecent(1).get(0);

        assertThat(event.getDetails())
                .containsEntry("owner", "Alice")
                .doesNotContainKeys("bad key", "x".repeat(61));
        assertThat(event.getDetails().get("note").toString()).hasSize(200);
    }

    @Test
    void recordTrimsAuditLogToConfiguredMaxEvents() throws Exception {
        ReflectionTestUtils.setField(auditService, "maxEvents", 2);

        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, "HR", "one.txt", Map.of());
        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, "HR", "two.txt", Map.of());
        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, "HR", "three.txt", Map.of());

        Path auditPath = tempDir.resolve(".documind-audit.log");
        List<String> lines = Files.readAllLines(auditPath, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(2);
        assertThat(String.join("\n", lines))
                .doesNotContain("one.txt")
                .contains("two.txt")
                .contains("three.txt");

        List<AuditEvent> events = auditService.listRecent(10);
        assertThat(events)
                .extracting(AuditEvent::getFileName)
                .containsExactly("three.txt", "two.txt");
    }
}
