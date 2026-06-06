package com.demo.ragchat.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuditEvent {

    private String id;
    private String timestamp;
    private String actor;
    private String action;
    private String knowledgeBase;
    private String fileName;
    private Map<String, Object> details = Collections.emptyMap();

    public AuditEvent() {
    }

    public AuditEvent(String id,
                      String timestamp,
                      String actor,
                      String action,
                      String knowledgeBase,
                      String fileName,
                      Map<String, Object> details) {
        this.id = id;
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.knowledgeBase = knowledgeBase;
        this.fileName = fileName;
        setDetails(details);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
    }
}
