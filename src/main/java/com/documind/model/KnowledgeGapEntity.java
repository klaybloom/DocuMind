package com.documind.model;

import jakarta.persistence.*;

/**
 * 知识缺口数据库实体，记录未能从文档中回答的问题。
 */
@Entity
@Table(name = "knowledge_gaps")
public class KnowledgeGapEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String knowledgeBase;

    @Column(nullable = false, length = 2000)
    private String question;

    private String sessionId;

    private String createdAt;

    private int occurrences;

    private String lastAskedAt;

    protected KnowledgeGapEntity() {}

    public KnowledgeGapEntity(String id, String knowledgeBase, String question,
                              String sessionId, String createdAt, int occurrences, String lastAskedAt) {
        this.id = id;
        this.knowledgeBase = knowledgeBase;
        this.question = question;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.occurrences = occurrences;
        this.lastAskedAt = lastAskedAt;
    }

    public String getId() { return id; }

    public String getKnowledgeBase() { return knowledgeBase; }
    public void setKnowledgeBase(String knowledgeBase) { this.knowledgeBase = knowledgeBase; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }

    public String getLastAskedAt() { return lastAskedAt; }
    public void setLastAskedAt(String lastAskedAt) { this.lastAskedAt = lastAskedAt; }
}
