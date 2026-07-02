package com.documind.model;

import jakarta.persistence.*;
import java.util.Map;

/**
 * 返回给前端的审计事件视图。
 */
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String timestamp;

    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    private String knowledgeBase;

    private String fileName;

    @Convert(converter = MapToStringConverter.class)
    @Column(columnDefinition = "CLOB")
    private Map<String, Object> details;

    protected AuditEventEntity() {}

    public AuditEventEntity(String id, String timestamp, String actor, String action,
                            String knowledgeBase, String fileName, Map<String, Object> details) {
        this.id = id;
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.knowledgeBase = knowledgeBase;
        this.fileName = fileName;
        this.details = details;
    }

    public String getId() { return id; }

    public String getTimestamp() { return timestamp; }

    public String getActor() { return actor; }

    public String getAction() { return action; }

    public String getKnowledgeBase() { return knowledgeBase; }

    public String getFileName() { return fileName; }

    public Map<String, Object> getDetails() { return details; }
}
