package com.documind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "knowledge_bases", uniqueConstraints = {
        @UniqueConstraint(name = "uk_knowledge_base_name", columnNames = "name")
})
/**
 * 知识库数据库实体，描述知识库名称、说明和创建者。
 */
public class KnowledgeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, length = 40)
    private String createdAt;

    @Column(nullable = false, length = 40)
    private String updatedAt;

    public KnowledgeBaseEntity() {
    }

    public KnowledgeBaseEntity(String name, String createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        String now = Instant.now().toString();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now().toString();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
