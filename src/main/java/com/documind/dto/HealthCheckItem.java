package com.documind.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class HealthCheckItem {

    private String name;
    private String status;
    private String message;
    private Map<String, Object> details = Collections.emptyMap();

    public HealthCheckItem() {
    }

    public HealthCheckItem(String name, String status, String message, Map<String, Object> details) {
        this.name = name;
        this.status = status;
        this.message = message;
        setDetails(details);
    }

    public static HealthCheckItem up(String name, String message, Map<String, Object> details) {
        return new HealthCheckItem(name, "UP", message, details);
    }

    public static HealthCheckItem down(String name, String message, Map<String, Object> details) {
        return new HealthCheckItem(name, "DOWN", message, details);
    }

    public static HealthCheckItem degraded(String name, String message, Map<String, Object> details) {
        return new HealthCheckItem(name, "DEGRADED", message, details);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
    }
}
