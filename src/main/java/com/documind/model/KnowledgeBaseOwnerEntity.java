package com.documind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "knowledge_base_owners", uniqueConstraints = {
        @UniqueConstraint(name = "uk_knowledge_base_owner", columnNames = {"knowledge_base", "username"})
})
public class KnowledgeBaseOwnerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_base", nullable = false, length = 60)
    private String knowledgeBase;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 40)
    private String createdAt;

    public KnowledgeBaseOwnerEntity() {
    }

    public KnowledgeBaseOwnerEntity(String knowledgeBase, String username) {
        this.knowledgeBase = knowledgeBase;
        this.username = username;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now().toString();
        }
    }

    public Long getId() {
        return id;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
