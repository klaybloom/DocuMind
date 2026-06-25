package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    private String id;
    private String timestamp;
    private String actor;
    private String action;
    private String knowledgeBase;
    private String fileName;
    @Setter(lombok.AccessLevel.NONE)
    private Map<String, Object> details = Collections.emptyMap();

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
    }
}
