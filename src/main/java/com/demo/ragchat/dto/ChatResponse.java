package com.demo.ragchat.dto;

public class ChatResponse {

    private String response;
    private String error;

    public ChatResponse() {
    }

    public ChatResponse(String response) {
        this.response = response;
    }

    public static ChatResponse success(String response) {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setResponse(response);
        return chatResponse;
    }

    public static ChatResponse error(String error) {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setError(error);
        return chatResponse;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
