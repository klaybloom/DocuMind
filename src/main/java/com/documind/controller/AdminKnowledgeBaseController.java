package com.documind.controller;

import com.documind.dto.AddKnowledgeBaseOwnersRequest;
import com.documind.dto.CreateKnowledgeBaseRequest;
import com.documind.dto.KnowledgeBaseAdminResponse;
import com.documind.dto.SelfTransferKnowledgeBaseOwnerRequest;
import com.documind.dto.UpdateKnowledgeBaseMembersRequest;
import com.documind.dto.UpdateKnowledgeBaseOwnersRequest;
import com.documind.service.AuditService;
import com.documind.service.KnowledgeBaseManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 管理员知识库接口，负责知识库创建、成员维护和所有者转移。
 */
@RestController
@RequestMapping("/api/v1/admin/knowledge-bases")
public class AdminKnowledgeBaseController {

    private final KnowledgeBaseManagementService knowledgeBaseManagementService;
    private final AuditService auditService;

    public AdminKnowledgeBaseController(KnowledgeBaseManagementService knowledgeBaseManagementService,
                                        AuditService auditService) {
        this.knowledgeBaseManagementService = knowledgeBaseManagementService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<KnowledgeBaseAdminResponse> listKnowledgeBases(Authentication authentication) {
        knowledgeBaseManagementService.syncKnownKnowledgeBases();
        return knowledgeBaseManagementService.listManageable(authentication);
    }

    @PostMapping
    public ResponseEntity<KnowledgeBaseAdminResponse> createKnowledgeBase(@Valid @RequestBody CreateKnowledgeBaseRequest request,
                                                                          Authentication authentication,
                                                                          Principal principal) {
        KnowledgeBaseAdminResponse response = knowledgeBaseManagementService.createKnowledgeBase(
                request.getName(),
                request.getOwners(),
                authentication
        );
        auditService.record(actor(principal), AuditService.ACTION_CREATE_KNOWLEDGE_BASE,
                response.getKnowledgeBase(), null, Map.of("owners", String.join(",", response.getOwners())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{knowledgeBase}/owners")
    public KnowledgeBaseAdminResponse setOwners(@PathVariable String knowledgeBase,
                                                @Valid @RequestBody UpdateKnowledgeBaseOwnersRequest request,
                                                Authentication authentication,
                                                Principal principal) {
        KnowledgeBaseAdminResponse response = knowledgeBaseManagementService.setOwners(
                knowledgeBase,
                request.getOwners(),
                authentication
        );
        auditService.record(actor(principal), AuditService.ACTION_UPDATE_KNOWLEDGE_BASE_OWNERS,
                response.getKnowledgeBase(), null, Map.of("owners", String.join(",", response.getOwners())));
        return response;
    }

    @PostMapping("/{knowledgeBase}/owners")
    public KnowledgeBaseAdminResponse addOwners(@PathVariable String knowledgeBase,
                                                @Valid @RequestBody AddKnowledgeBaseOwnersRequest request,
                                                Authentication authentication,
                                                Principal principal) {
        KnowledgeBaseAdminResponse response = knowledgeBaseManagementService.addOwners(
                knowledgeBase,
                request.getOwners(),
                authentication
        );
        auditService.record(actor(principal), AuditService.ACTION_ADD_KNOWLEDGE_BASE_OWNERS,
                response.getKnowledgeBase(), null, Map.of("owners", String.join(",", request.getOwners())));
        return response;
    }

    @PutMapping("/{knowledgeBase}/owners/self-transfer")
    public KnowledgeBaseAdminResponse selfTransfer(@PathVariable String knowledgeBase,
                                                   @Valid @RequestBody SelfTransferKnowledgeBaseOwnerRequest request,
                                                   Authentication authentication,
                                                   Principal principal) {
        KnowledgeBaseAdminResponse response = knowledgeBaseManagementService.selfTransfer(
                knowledgeBase,
                request.getOwners(),
                authentication
        );
        auditService.record(actor(principal), AuditService.ACTION_TRANSFER_KNOWLEDGE_BASE_OWNER,
                response.getKnowledgeBase(), null, Map.of("newOwners", String.join(",", request.getOwners())));
        return response;
    }

    @PutMapping("/{knowledgeBase}/members")
    public KnowledgeBaseAdminResponse setMembers(@PathVariable String knowledgeBase,
                                                 @Valid @RequestBody UpdateKnowledgeBaseMembersRequest request,
                                                 Authentication authentication,
                                                 Principal principal) {
        KnowledgeBaseAdminResponse response = knowledgeBaseManagementService.setMembers(
                knowledgeBase,
                request.getMembers(),
                authentication
        );
        auditService.record(actor(principal), AuditService.ACTION_UPDATE_KNOWLEDGE_BASE_MEMBERS,
                response.getKnowledgeBase(), null, Map.of("members", String.join(",", response.getMembers())));
        return response;
    }

    @ExceptionHandler(KnowledgeBaseManagementService.KnowledgeBaseManagementException.class)
    public ResponseEntity<Map<String, String>> handleKnowledgeBaseException(
            KnowledgeBaseManagementService.KnowledgeBaseManagementException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
    }

    private String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
