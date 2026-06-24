package com.documind.controller;

import com.documind.dto.HealthCheckResponse;
import com.documind.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<HealthCheckResponse> liveness() {
        return ResponseEntity.ok(healthService.liveness());
    }

    @GetMapping("/readiness")
    public ResponseEntity<HealthCheckResponse> readiness() {
        HealthCheckResponse response = healthService.readiness();
        if ("DOWN".equals(response.getStatus())) {
            return ResponseEntity.status(503).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
