package com.documind.dto;

public class FaqDraftResponse {

    private String knowledgeBase;
    private String generatedAt;
    private int questionCount;
    private String markdown;

    public FaqDraftResponse() {
    }

    public FaqDraftResponse(String knowledgeBase, String generatedAt, int questionCount, String markdown) {
        this.knowledgeBase = knowledgeBase;
        this.generatedAt = generatedAt;
        this.questionCount = questionCount;
        this.markdown = markdown;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }
}
