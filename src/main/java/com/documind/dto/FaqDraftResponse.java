package com.documind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FaqDraftResponse {

    private String knowledgeBase;
    private String generatedAt;
    private int questionCount;
    private String markdown;

    public FaqDraftResponse(String knowledgeBase, String generatedAt, int questionCount, String markdown) {
        this.knowledgeBase = knowledgeBase;
        this.generatedAt = generatedAt;
        this.questionCount = questionCount;
        this.markdown = markdown;
    }
}
