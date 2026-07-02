package com.documind.service;

import com.documind.model.UserAccount;
import com.documind.repository.KnowledgeBaseOwnerRepository;
import com.documind.repository.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库访问控制服务，判断用户可访问哪些知识库。
 */
@Service
public class KnowledgeBaseAccessService {

    private final DocumentService documentService;
    private final UserAccountRepository userAccountRepository;
    private final KnowledgeBaseOwnerRepository ownerRepository;

    public KnowledgeBaseAccessService(DocumentService documentService,
                                      UserAccountRepository userAccountRepository,
                                      KnowledgeBaseOwnerRepository ownerRepository) {
        this.documentService = documentService;
        this.userAccountRepository = userAccountRepository;
        this.ownerRepository = ownerRepository;
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

        UserAccount account = userAccountRepository.findByUsername(authentication.getName()).orElse(null);
        if (account == null || !account.isEnabled() || !"USER".equals(account.getRole())) {
            return false;
        }
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        Set<String> allowed = allowedKnowledgeBases(account);
        return allowed.contains("*")
                || allowed.contains(kb)
                || ownerRepository.existsByKnowledgeBaseAndUsername(kb, account.getUsername());
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

    private Set<String> allowedKnowledgeBases(UserAccount account) {
        String knowledgeBases = account.getKnowledgeBases();
        if (knowledgeBases == null || knowledgeBases.trim().isEmpty()) {
            return Set.of(DocumentService.DEFAULT_KNOWLEDGE_BASE);
        }
        return Arrays.stream(knowledgeBases.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> "*".equals(value) ? "*" : documentService.normalizeKnowledgeBase(value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
