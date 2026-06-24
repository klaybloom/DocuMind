package com.documind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 5000, message = "消息长度不能超过5000字符")
    private String message;

    @Size(max = 100, message = "会话ID长度不能超过100字符")
    @Pattern(regexp = "^$|[A-Za-z0-9._:-]+$", message = "会话ID只能包含字母、数字、点、下划线、冒号和连字符")
    private String sessionId;

    @Size(max = 60, message = "知识库名称长度不能超过60字符")
    @Pattern(regexp = "^$|[\\p{L}\\p{N}._-]+$", message = "知识库名称只能包含文字、数字、点、下划线和连字符")
    private String knowledgeBase;

    public ChatRequest() {
    }

    public ChatRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }
}
