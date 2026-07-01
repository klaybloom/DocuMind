package com.documind.controller;

import com.documind.service.KnowledgeBaseManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final KnowledgeBaseManagementService knowledgeBaseManagementService;

    public AuthController(KnowledgeBaseManagementService knowledgeBaseManagementService) {
        this.knowledgeBaseManagementService = knowledgeBaseManagementService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", auth.getName());
        body.put("roles", roles);
        List<String> managedKnowledgeBases = knowledgeBaseManagementService.manageableKnowledgeBases(auth);
        body.put("managedKnowledgeBases", managedKnowledgeBases);
        body.put("canManageKnowledgeBases", roles.contains("ADMIN") || !managedKnowledgeBases.isEmpty());
        return ResponseEntity.ok(body);
    }
}
