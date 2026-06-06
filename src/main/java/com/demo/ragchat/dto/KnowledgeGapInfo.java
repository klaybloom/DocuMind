package com.demo.ragchat.dto;

public class KnowledgeGapInfo {

    private String id;
    private String knowledgeBase;
    private String question;
    private String sessionId;
    private String createdAt;
    private int occurrences;
    private String lastAskedAt;

    public KnowledgeGapInfo() {
    }

    public KnowledgeGapInfo(String id,
                            String knowledgeBase,
                            String question,
                            String sessionId,
                            String createdAt,
                            int occurrences,
                            String lastAskedAt) {
        this.id = id;
        this.knowledgeBase = knowledgeBase;
        this.question = question;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.occurrences = occurrences;
        this.lastAskedAt = lastAskedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(int occurrences) {
        this.occurrences = occurrences;
    }

    public String getLastAskedAt() {
        return lastAskedAt;
    }

    public void setLastAskedAt(String lastAskedAt) {
        this.lastAskedAt = lastAskedAt;
    }
}
