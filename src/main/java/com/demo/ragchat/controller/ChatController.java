package com.demo.ragchat.controller;

import com.demo.ragchat.dto.ChatRequest;
import com.demo.ragchat.dto.ChatResponse;
import com.demo.ragchat.service.RagService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        try {
            logger.debug("Received chat request: {}", request.getMessage());
            String response = ragService.ask(request.getMessage(), request.getSessionId());
            return ResponseEntity.ok(ChatResponse.success(response));
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.error("处理请求时发生错误，请稍后重试"));
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        CompletableFuture.runAsync(() -> ragService.askStream(
                request.getMessage(),
                request.getSessionId(),
                token -> sendEmitterEvent(emitter, "token", token),
                () -> {
                    sendEmitterEvent(emitter, "done", "");
                    emitter.complete();
                },
                error -> {
                    logger.error("Error processing streaming chat request", error);
                    sendEmitterEvent(emitter, "error", "处理请求时发生错误，请稍后重试");
                    emitter.complete();
                }
        ));

        return emitter;
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
}
