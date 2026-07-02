package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 系统 readiness 汇总结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckResponse {

    private String status;
    private String timestamp;
    @Setter(lombok.AccessLevel.NONE)
    private List<HealthCheckItem> checks = Collections.emptyList();

    public void setChecks(List<HealthCheckItem> checks) {
        this.checks = checks == null ? Collections.emptyList() : new ArrayList<>(checks);
    }
}
