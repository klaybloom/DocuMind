package com.demo.ragchat.controller;

import com.demo.ragchat.dto.RagAnswer;
import com.demo.ragchat.service.AuditService;
import com.demo.ragchat.service.DocumentService;
import com.demo.ragchat.service.KnowledgeBaseAccessService;
import com.demo.ragchat.service.RateLimitService;
import com.demo.ragchat.service.RagService;
import com.demo.ragchat.exception.GlobalExceptionHandler;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerHttpTest {

    private MockMvc mockMvc;
    private FakeRagService ragService;
    private RecordingAuditService auditService;
    private FakeRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        DocumentService documentService = new FakeDocumentService();
        ragService = new FakeRagService(documentService);
        auditService = new RecordingAuditService();
        rateLimitService = new FakeRateLimitService();
        KnowledgeBaseAccessService accessService = new KnowledgeBaseAccessService(documentService);
        ReflectionTestUtils.setField(accessService, "userKnowledgeBases", "HR");
        ChatController controller = new ChatController(
                ragService,
                auditService,
                accessService,
                rateLimitService,
                Runnable::run
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void chatReturnsAnswerAndRecordsMetadataOnlyAuditEvent() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "合同模板在哪里？",
                                  "sessionId": "session-1",
                                  "knowledgeBase": "HR"
                                }
                                """)
                        .principal(named("reader"))
                        .with(request -> {
                            request.setUserPrincipal(user("reader", "ROLE_USER"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("测试回答"))
                .andExpect(jsonPath("$.fromDocuments").value(false));

        assertThat(ragService.askCalls).isEqualTo(1);
        assertThat(ragService.question).isEqualTo("合同模板在哪里？");
        assertThat(ragService.sessionId).isEqualTo("reader:session-1");
        assertThat(ragService.knowledgeBase).isEqualTo("HR");
        assertThat(auditService.recordCalls).isEqualTo(1);
        assertThat(auditService.actor).isEqualTo("reader");
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_CHAT_REQUEST);
        assertThat(auditService.details)
                .containsEntry("messageLength", "合同模板在哪里？".length())
                .containsEntry("sessionId", "session-1")
                .doesNotContainKey("message")
                .doesNotContainKey("question");
    }

    @Test
    void chatRejectsUnauthorizedKnowledgeBaseWithoutCallingModelOrAudit() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "合同模板在哪里？",
                                  "sessionId": "session-1",
                                  "knowledgeBase": "Legal"
                                }
                                """)
                        .principal(named("reader"))
                        .with(request -> {
                            request.setUserPrincipal(user("reader", "ROLE_USER"));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("当前账号没有访问该知识库的权限"));

        assertThat(ragService.askCalls).isZero();
        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void chatRejectsRateLimitedRequestWithoutCallingModel() throws Exception {
        rateLimitService.decision = RateLimitService.RateLimitDecision.rejected(30, 12);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "合同模板在哪里？",
                                  "sessionId": "session-1",
                                  "knowledgeBase": "HR"
                                }
                                """)
                        .principal(named("reader"))
                        .with(request -> {
                            request.setUserPrincipal(user("reader", "ROLE_USER"));
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("请求过于频繁，请 12 秒后再试。当前限制为每分钟 30 次。"));

        assertThat(ragService.askCalls).isZero();
        assertThat(auditService.recordCalls).isEqualTo(1);
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_RATE_LIMITED_CHAT_REQUEST);
        assertThat(auditService.details)
                .containsEntry("streaming", false)
                .containsEntry("messageLength", "合同模板在哪里？".length())
                .containsEntry("sessionId", "session-1")
                .containsEntry("limit", 30)
                .containsEntry("retryAfterSeconds", 12L)
                .doesNotContainKey("message")
                .doesNotContainKey("question");
        assertThat(rateLimitService.actor).isEqualTo("reader");
    }

    @Test
    void streamChatRejectsRateLimitedRequestWithoutCallingModel() throws Exception {
        rateLimitService.decision = RateLimitService.RateLimitDecision.rejected(30, 12);

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "合同模板在哪里？",
                                  "sessionId": "session-1",
                                  "knowledgeBase": "HR"
                                }
                                """)
                        .principal(named("reader"))
                        .with(request -> {
                            request.setUserPrincipal(user("reader", "ROLE_USER"));
                            return request;
                        }))
                .andExpect(status().isOk());

        assertThat(ragService.streamCalls).isZero();
        assertThat(auditService.recordCalls).isEqualTo(1);
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_RATE_LIMITED_CHAT_REQUEST);
        assertThat(auditService.details)
                .containsEntry("streaming", true)
                .containsEntry("messageLength", "合同模板在哪里？".length())
                .containsEntry("sessionId", "session-1")
                .containsEntry("limit", 30)
                .containsEntry("retryAfterSeconds", 12L)
                .doesNotContainKey("message")
                .doesNotContainKey("question");
    }

    @Test
    void invalidChatRequestReturnsValidationMessageWithoutCallingModelOrAudit() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "",
                                  "sessionId": "../bad session",
                                  "knowledgeBase": "HR"
                                }
                                """)
                        .principal(named("reader"))
                        .with(request -> {
                            request.setUserPrincipal(user("reader", "ROLE_USER"));
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(allOf(
                        containsString("消息不能为空"),
                        containsString("会话ID只能包含字母、数字、点、下划线、冒号和连字符")
                )));

        assertThat(ragService.askCalls).isZero();
        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void clearSessionRejectsInvalidSessionIdWithoutClearingMemoryOrAudit() throws Exception {
        mockMvc.perform(delete("/api/chat/sessions/bad session")
                        .principal(named("reader")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("会话ID无效"));

        assertThat(ragService.clearCalls).isZero();
        assertThat(auditService.recordCalls).isZero();
    }

    @Test
    void clearSessionClearsMemoryAndRecordsAuditEvent() throws Exception {
        ragService.clearResult = 2;

        mockMvc.perform(delete("/api/chat/sessions/session-1")
                        .principal(named("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedMemories").value(2));

        assertThat(ragService.clearCalls).isEqualTo(1);
        assertThat(ragService.clearedSessionId).isEqualTo("reader:session-1");
        assertThat(auditService.recordCalls).isEqualTo(1);
        assertThat(auditService.action).isEqualTo(AuditService.ACTION_CLEAR_CHAT_SESSION);
        assertThat(auditService.details)
                .containsEntry("sessionId", "session-1")
                .containsEntry("removedMemories", 2);
    }

    @Test
    void scopedSessionIdSanitizesActorNameBeforeCallingRagService() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "合同模板在哪里？",
                                  "sessionId": "session-1",
                                  "knowledgeBase": "HR"
                                }
                                """)
                        .principal(named("reader@example.com"))
                        .with(request -> {
                            request.setUserPrincipal(user("reader@example.com", "ROLE_USER"));
                            return request;
                        }))
                .andExpect(status().isOk());

        assertThat(ragService.sessionId).isEqualTo("reader_example.com:session-1");
        assertThat(auditService.actor).isEqualTo("reader@example.com");
        assertThat(auditService.details).containsEntry("sessionId", "session-1");
    }

    @Test
    void streamTimeoutUsesConfiguredSecondsWithMinimumValue() {
        DocumentService documentService = new FakeDocumentService();
        ChatController controller = new ChatController(
                new FakeRagService(documentService),
                new RecordingAuditService(),
                new KnowledgeBaseAccessService(documentService),
                new FakeRateLimitService(),
                Runnable::run
        );
        ReflectionTestUtils.setField(controller, "streamTimeoutSeconds", 10L);
        assertThat(controller.streamTimeoutMillis()).isEqualTo(30_000L);

        ReflectionTestUtils.setField(controller, "streamTimeoutSeconds", 180L);
        assertThat(controller.streamTimeoutMillis()).isEqualTo(180_000L);
    }

    private Principal named(String name) {
        return () -> name;
    }

    private Authentication user(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "password",
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    static class FakeDocumentService extends DocumentService {

        @Override
        public String normalizeKnowledgeBase(String knowledgeBase) {
            return knowledgeBase == null || knowledgeBase.trim().isEmpty() ? DEFAULT_KNOWLEDGE_BASE : knowledgeBase.trim();
        }
    }

    static class FakeRagService extends RagService {

        private int askCalls;
        private String question;
        private String sessionId;
        private String knowledgeBase;
        private int streamCalls;
        private int clearCalls;
        private String clearedSessionId;
        private int clearResult;

        FakeRagService(DocumentService documentService) {
            super(null, null, null, new InMemoryEmbeddingStore<TextSegment>(), documentService);
        }

        @Override
        public RagAnswer ask(String question, String sessionId, String knowledgeBase) {
            askCalls++;
            this.question = question;
            this.sessionId = sessionId;
            this.knowledgeBase = knowledgeBase;
            return new RagAnswer("测试回答", List.of(), false);
        }

        @Override
        public void askStream(String question,
                              String sessionId,
                              String knowledgeBase,
                              java.util.function.Consumer<String> onNext,
                              Runnable onComplete,
                              java.util.function.Consumer<Throwable> onError) {
            streamCalls++;
            onNext.accept("测试回答");
            onComplete.run();
        }

        @Override
        public int clearSessionMemory(String sessionId) {
            clearCalls++;
            clearedSessionId = sessionId;
            return clearResult;
        }
    }

    static class RecordingAuditService extends AuditService {

        private int recordCalls;
        private String actor;
        private String action;
        private Map<String, Object> details;

        @Override
        public synchronized void record(String actor,
                                        String action,
                                        String knowledgeBase,
                                        String fileName,
                                        Map<String, Object> details) {
            recordCalls++;
            this.actor = actor;
            this.action = action;
            this.details = details;
        }
    }

    static class FakeRateLimitService extends RateLimitService {

        private RateLimitDecision decision = RateLimitDecision.allowed(30);
        private String actor;

        @Override
        public RateLimitDecision check(String actor) {
            this.actor = actor;
            return decision;
        }
    }
}
