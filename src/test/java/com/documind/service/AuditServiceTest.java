package com.documind.service;

import com.documind.dto.AuditEvent;
import com.documind.model.AuditEventEntity;
import com.documind.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditEventRepository repository;
    private AuditService auditService;
    private final List<AuditEventEntity> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        auditService = new AuditService(repository);
        ReflectionTestUtils.setField(auditService, "maxEvents", 10_000);

        // Wire mock to use in-memory list
        when(repository.save(any())).thenAnswer(inv -> {
            AuditEventEntity e = inv.getArgument(0);
            store.add(e);
            return e;
        });
        when(repository.count()).thenAnswer(inv -> (long) store.size());
        doAnswer(inv -> {
            int excess = inv.getArgument(0);
            List<AuditEventEntity> sorted = store.stream()
                    .sorted(Comparator.comparing(AuditEventEntity::getTimestamp))
                    .toList();
            for (int i = 0; i < excess && i < sorted.size(); i++) {
                store.remove(sorted.get(i));
            }
            return null;
        }).when(repository).deleteOldest(anyInt());
        when(repository.findAllByOrderByTimestampDesc(any())).thenAnswer(inv -> {
            int size = inv.<PageRequest>getArgument(0).getPageSize();
            return store.stream()
                    .sorted(Comparator.comparing(AuditEventEntity::getTimestamp).reversed())
                    .limit(size)
                    .toList();
        });
    }

    @Test
    void recordWritesAuditEventWithoutFullQuestionOrMessage() {
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

        assertThat(store).hasSize(1);
        AuditEventEntity entity = store.get(0);
        assertThat(entity.getActor()).isEqualTo("admin");
        assertThat(entity.getAction()).isEqualTo(AuditService.ACTION_CHAT_REQUEST);
        assertThat(entity.getKnowledgeBase()).isEqualTo("HR");
        assertThat(entity.getDetails()).doesNotContainKey("question");
        assertThat(entity.getDetails()).doesNotContainKey("message");

        List<AuditEvent> events = auditService.listRecent(10);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getActor()).isEqualTo("admin");
        assertThat(events.get(0).getKnowledgeBase()).isEqualTo("HR");
        assertThat(events.get(0).getDetails()).containsEntry("messageLength", 12);
    }

    @Test
    void recordRateLimitedChatEventWithoutFullQuestionOrMessage() {
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

        assertThat(store).hasSize(1);
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
    void recordTrimsAuditLogToConfiguredMaxEvents() {
        ReflectionTestUtils.setField(auditService, "maxEvents", 2);

        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, "HR", "one.txt", Map.of());
        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, "HR", "two.txt", Map.of());
        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, "HR", "three.txt", Map.of());

        assertThat(store).hasSize(2);
        List<AuditEvent> events = auditService.listRecent(10);
        assertThat(events).hasSize(2);
        assertThat(events)
                .extracting(AuditEvent::getFileName)
                .containsExactly("three.txt", "two.txt");
    }
}
