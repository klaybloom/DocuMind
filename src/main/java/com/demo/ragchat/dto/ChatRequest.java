package com.demo.ragchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 5000, message = "消息长度不能超过5000字符")
    private String message;

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
}
