package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个健康检查项，描述检查状态和提示信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckItem {

    private String name;
    private String status;
    private String message;
    @Setter(lombok.AccessLevel.NONE)
    private Map<String, Object> details = Collections.emptyMap();

    public static HealthCheckItem up(String name, String message, Map<String, Object> details) {
        return new HealthCheckItem(name, "UP", message, details);
    }

    public static HealthCheckItem down(String name, String message, Map<String, Object> details) {
        return new HealthCheckItem(name, "DOWN", message, details);
    }

    public static HealthCheckItem degraded(String name, String message, Map<String, Object> details) {
        return new HealthCheckItem(name, "DEGRADED", message, details);
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
    }
}
