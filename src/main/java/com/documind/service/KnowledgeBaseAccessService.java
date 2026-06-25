package com.documind.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseAccessService {

    private final DocumentService documentService;

    @Value("${app.security.user-knowledge-bases:default}")
    private String userKnowledgeBases;

    public KnowledgeBaseAccessService(DocumentService documentService) {
        this.documentService = documentService;
    }

    public boolean canAccess(Authentication authentication, String knowledgeBase) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return true;
        }
        if (!hasRole(authentication, "ROLE_USER")) {
            return false;
        }

        Set<String> allowed = allowedKnowledgeBases();
        return allowed.contains("*") || allowed.contains(documentService.normalizeKnowledgeBase(knowledgeBase));
    }

    public boolean canAccessAll(Authentication authentication, List<String> knowledgeBases) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return canAccess(authentication, DocumentService.DEFAULT_KNOWLEDGE_BASE);
        }
        return knowledgeBases.stream().allMatch(knowledgeBase -> canAccess(authentication, knowledgeBase));
    }

    public List<String> filterAccessible(Authentication authentication, List<String> knowledgeBases) {
        if (authentication == null || knowledgeBases == null) {
            return List.of();
        }
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return knowledgeBases;
        }
        return knowledgeBases.stream()
                .filter(knowledgeBase -> canAccess(authentication, knowledgeBase))
                .collect(Collectors.toList());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private Set<String> allowedKnowledgeBases() {
        if (userKnowledgeBases == null || userKnowledgeBases.trim().isEmpty()) {
            return Set.of(DocumentService.DEFAULT_KNOWLEDGE_BASE);
        }
        return Arrays.stream(userKnowledgeBases.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> "*".equals(value) ? "*" : documentService.normalizeKnowledgeBase(value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
