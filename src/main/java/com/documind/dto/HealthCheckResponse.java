package com.documind.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HealthCheckResponse {

    private String status;
    private String timestamp;
    private List<HealthCheckItem> checks = Collections.emptyList();

    public HealthCheckResponse() {
    }

    public HealthCheckResponse(String status, String timestamp, List<HealthCheckItem> checks) {
        this.status = status;
        this.timestamp = timestamp;
        setChecks(checks);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public List<HealthCheckItem> getChecks() {
        return checks;
    }

    public void setChecks(List<HealthCheckItem> checks) {
        this.checks = checks == null ? Collections.emptyList() : new ArrayList<>(checks);
    }
}
