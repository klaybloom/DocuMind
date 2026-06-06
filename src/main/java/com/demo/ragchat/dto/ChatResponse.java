package com.demo.ragchat.dto;

import java.util.Collections;
import java.util.List;

public class ChatResponse {

    private String response;
    private String error;
    private List<SourceReference> sources = Collections.emptyList();
    private boolean fromDocuments;

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

    public static ChatResponse success(RagAnswer answer) {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setResponse(answer.getAnswer());
        chatResponse.setSources(answer.getSources());
        chatResponse.setFromDocuments(answer.isFromDocuments());
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

    public List<SourceReference> getSources() {
        return sources;
    }

    public void setSources(List<SourceReference> sources) {
        this.sources = sources == null ? Collections.emptyList() : sources;
    }

    public boolean isFromDocuments() {
        return fromDocuments;
    }

    public void setFromDocuments(boolean fromDocuments) {
        this.fromDocuments = fromDocuments;
    }
}
