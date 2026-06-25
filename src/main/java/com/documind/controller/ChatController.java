package com.documind.controller;

import com.documind.dto.ChatRequest;
import com.documind.dto.ChatResponse;
import com.documind.dto.RagAnswer;
import com.documind.dto.RetrievalDebugInfo;
import com.documind.dto.SourceReference;
import com.documind.service.AuditService;
import com.documind.service.KnowledgeBaseAccessService;
import com.documind.service.RateLimitService;
import com.documind.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_SESSION_ID_LENGTH = 100;
    private static final String SESSION_ID_PATTERN = "[A-Za-z0-9._:-]+";
    private final RagService ragService;
    private final AuditService auditService;
    private final KnowledgeBaseAccessService knowledgeBaseAccessService;
    private final RateLimitService rateLimitService;
    private final Executor chatStreamExecutor;
    private final ObjectMapper objectMapper;

    @Value("${app.chat.stream.timeout-seconds:120}")
    private long streamTimeoutSeconds;

    public ChatController(RagService ragService,
                          AuditService auditService,
                          KnowledgeBaseAccessService knowledgeBaseAccessService,
                          RateLimitService rateLimitService,
                          @Qualifier("chatStreamExecutor") Executor chatStreamExecutor,
                          ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.auditService = auditService;
        this.knowledgeBaseAccessService = knowledgeBaseAccessService;
        this.rateLimitService = rateLimitService;
        this.chatStreamExecutor = chatStreamExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                             @RequestParam(value = "debug", required = false, defaultValue = "false") boolean debug,
                                             Principal principal,
                                             Authentication authentication) {
        try {
            List<String> knowledgeBases = requestedKnowledgeBases(request);
            String knowledgeBaseSelection = String.join(",", knowledgeBases);
            logger.debug("Received chat request, messageLength={}, knowledgeBase={}",
                    safeLength(request.getMessage()), knowledgeBaseSelection);
            if (!knowledgeBaseAccessService.canAccessAll(authentication, knowledgeBases)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ChatResponse.error("当前账号没有访问该知识库的权限"));
            }
            RateLimitService.RateLimitDecision rateLimit = rateLimitService.check(actor(principal));
            if (!rateLimit.allowed()) {
                recordRateLimitedChat(principal, request, rateLimit, false);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.retryAfterSeconds()))
                        .body(ChatResponse.error(rateLimitMessage(rateLimit)));
            }
            auditService.record(
                    actor(principal),
                    AuditService.ACTION_CHAT_REQUEST,
                    knowledgeBaseSelection,
                    null,
                    chatAuditDetails(request, false)
            );
            RagAnswer answer = ragService.ask(
                    request.getMessage(),
                    scopedSessionId(principal, request.getSessionId()),
                    knowledgeBaseSelection,
                    debug
            );
            return ResponseEntity.ok(ChatResponse.success(answer));
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.error("处理请求时发生错误，请稍后重试"));
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request,
                                 @RequestParam(value = "debug", required = false, defaultValue = "false") boolean debug,
                                 Principal principal,
                                 Authentication authentication) {
        SseEmitter emitter = new SseEmitter(streamTimeoutMillis());
        List<String> knowledgeBases = requestedKnowledgeBases(request);
        String knowledgeBaseSelection = String.join(",", knowledgeBases);
        if (!knowledgeBaseAccessService.canAccessAll(authentication, knowledgeBases)) {
            sendEmitterEvent(emitter, "error", "当前账号没有访问该知识库的权限");
            emitter.complete();
            return emitter;
        }
        RateLimitService.RateLimitDecision rateLimit = rateLimitService.check(actor(principal));
        if (!rateLimit.allowed()) {
            recordRateLimitedChat(principal, request, rateLimit, true);
            sendEmitterEvent(emitter, "error", rateLimitMessage(rateLimit));
            emitter.complete();
            return emitter;
        }
        auditService.record(
                actor(principal),
                AuditService.ACTION_CHAT_REQUEST,
                knowledgeBaseSelection,
                null,
                chatAuditDetails(request, true)
        );

        try {
            CompletableFuture.runAsync(() -> ragService.askStream(
                    request.getMessage(),
                    scopedSessionId(principal, request.getSessionId()),
                    knowledgeBaseSelection,
                    token -> sendEmitterEvent(emitter, "token", token),
                    sources -> sendSourcesEvent(emitter, sources),
                    debugInfo -> sendDebugEvent(emitter, debugInfo),
                    () -> {
                        sendEmitterEvent(emitter, "done", "");
                        emitter.complete();
                    },
                    error -> {
                        logger.error("Error processing streaming chat request", error);
                        sendEmitterEvent(emitter, "error", "处理请求时发生错误，请稍后重试");
                        emitter.complete();
                    },
                    debug
            ), chatStreamExecutor);
        } catch (RejectedExecutionException e) {
            logger.warn("Streaming chat executor rejected request", e);
            sendEmitterEvent(emitter, "error", "当前问答请求较多，请稍后再试");
            emitter.complete();
        }

        return emitter;
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId,
                                                            Principal principal) {
        if (!isValidSessionId(sessionId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "会话ID无效"));
        }
        int removed = ragService.clearSessionMemory(scopedSessionId(principal, sessionId));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sessionId", sessionId);
        details.put("removedMemories", removed);
        auditService.record(
                actor(principal),
                AuditService.ACTION_CLEAR_CHAT_SESSION,
                null,
                null,
                details
        );
        return ResponseEntity.ok(Map.of(
                "message", "会话记忆已清理",
                "removedMemories", removed
        ));
    }

    private void sendEmitterEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data == null ? "" : data));
        } catch (IOException e) {
            logger.warn("Failed to send SSE event: {}", eventName, e);
            emitter.completeWithError(e);
        }
    }

    private void sendSourcesEvent(SseEmitter emitter, List<SourceReference> sources) {
        try {
            String json = objectMapper.writeValueAsString(sources);
            sendEmitterEvent(emitter, "sources", json);
        } catch (IOException e) {
            logger.warn("Failed to serialize sources for SSE event", e);
        }
    }

    private void sendDebugEvent(SseEmitter emitter, RetrievalDebugInfo debugInfo) {
        try {
            String json = objectMapper.writeValueAsString(debugInfo);
            sendEmitterEvent(emitter, "debug", json);
        } catch (IOException e) {
            logger.warn("Failed to serialize debug info for SSE event", e);
        }
    }

    private Map<String, Object> chatAuditDetails(ChatRequest request, boolean streaming) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("streaming", streaming);
        details.put("messageLength", safeLength(request.getMessage()));
        details.put("knowledgeBases", requestedKnowledgeBases(request));
        if (request.getSessionId() != null && !request.getSessionId().trim().isEmpty()) {
            details.put("sessionId", request.getSessionId());
        }
        return details;
    }

    private List<String> requestedKnowledgeBases(ChatRequest request) {
        List<String> selected = new ArrayList<>();
        if (request.getKnowledgeBases() != null) {
            request.getKnowledgeBases().stream()
                    .filter(value -> value != null && !value.trim().isEmpty())
                    .map(String::trim)
                    .distinct()
                    .forEach(selected::add);
        }
        if (selected.isEmpty() && request.getKnowledgeBase() != null && !request.getKnowledgeBase().trim().isEmpty()) {
            selected.add(request.getKnowledgeBase().trim());
        }
        if (selected.isEmpty()) {
            selected.add("default");
        }
        return selected;
    }

    private void recordRateLimitedChat(Principal principal,
                                       ChatRequest request,
                                       RateLimitService.RateLimitDecision rateLimit,
                                       boolean streaming) {
        Map<String, Object> details = chatAuditDetails(request, streaming);
        details.put("limit", rateLimit.limit());
        details.put("retryAfterSeconds", rateLimit.retryAfterSeconds());
        auditService.record(
                actor(principal),
                AuditService.ACTION_RATE_LIMITED_CHAT_REQUEST,
                String.join(",", requestedKnowledgeBases(request)),
                null,
                details
        );
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private String rateLimitMessage(RateLimitService.RateLimitDecision decision) {
        return "请求过于频繁，请 " + decision.retryAfterSeconds()
                + " 秒后再试。当前限制为每分钟 " + decision.limit() + " 次。";
    }

    long streamTimeoutMillis() {
        return Math.max(30, streamTimeoutSeconds) * 1000L;
    }

    private boolean isValidSessionId(String sessionId) {
        return sessionId != null
                && !sessionId.trim().isEmpty()
                && sessionId.length() <= MAX_SESSION_ID_LENGTH
                && sessionId.matches(SESSION_ID_PATTERN);
    }

    private String scopedSessionId(Principal principal, String sessionId) {
        String actor = actor(principal).replaceAll("[^A-Za-z0-9._-]", "_");
        String id = sessionId == null || sessionId.trim().isEmpty() ? "default" : sessionId.trim();
        return actor + ":" + id;
    }
}
