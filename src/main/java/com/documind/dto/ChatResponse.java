package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String response;
    private String error;
    @Setter(lombok.AccessLevel.NONE)
    private List<SourceReference> sources = Collections.emptyList();
    private boolean fromDocuments;

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

    public void setSources(List<SourceReference> sources) {
        this.sources = sources == null ? Collections.emptyList() : sources;
    }
}
