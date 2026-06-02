package com.demo.ragchat.controller;

import com.demo.ragchat.dto.ChatRequest;
import com.demo.ragchat.dto.ChatResponse;
import com.demo.ragchat.service.RagService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            String response = ragService.ask(request.getMessage());
            return ResponseEntity.ok(ChatResponse.success(response));
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.error("处理请求时发生错误，请稍后重试"));
        }
    }
}
