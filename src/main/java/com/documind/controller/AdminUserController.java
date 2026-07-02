package com.documind.controller;

import com.documind.dto.CreateUserRequest;
import com.documind.dto.ResetPasswordRequest;
import com.documind.dto.UpdateUserRequest;
import com.documind.dto.UserAccountResponse;
import com.documind.dto.UserOptionResponse;
import com.documind.service.AuditService;
import com.documind.service.KnowledgeBaseManagementService;
import com.documind.service.UserAdminService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员用户接口，提供账号创建、更新、禁用和重置密码能力。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final KnowledgeBaseManagementService knowledgeBaseManagementService;
    private final AuditService auditService;

    public AdminUserController(UserAdminService userAdminService,
                               KnowledgeBaseManagementService knowledgeBaseManagementService,
                               AuditService auditService) {
        this.userAdminService = userAdminService;
        this.knowledgeBaseManagementService = knowledgeBaseManagementService;
        this.auditService = auditService;
    }

    @GetMapping("/options")
    public ResponseEntity<?> listUserOptions(Authentication authentication) {
        if (!knowledgeBaseManagementService.hasManagedKnowledgeBases(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "当前账号没有后台管理权限"));
        }
        return ResponseEntity.ok(knowledgeBaseManagementService.listUserOptions());
    }

    @GetMapping
    public List<UserAccountResponse> listUsers() {
        return userAdminService.listUsers();
    }

    @PostMapping
    public ResponseEntity<UserAccountResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                          Principal principal) {
        UserAccountResponse user = userAdminService.createUser(request);
        auditService.record(actor(principal), AuditService.ACTION_CREATE_USER,
                null, null, userDetails(user));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/{id}")
    public UserAccountResponse updateUser(@PathVariable Long id,
                                          @Valid @RequestBody UpdateUserRequest request,
                                          Principal principal) {
        UserAccountResponse user = userAdminService.updateUser(id, request);
        auditService.record(actor(principal), AuditService.ACTION_UPDATE_USER,
                null, null, userDetails(user));
        return user;
    }

    @PutMapping("/{id}/password")
    public UserAccountResponse resetPassword(@PathVariable Long id,
                                             @Valid @RequestBody ResetPasswordRequest request,
                                             Principal principal) {
        UserAccountResponse user = userAdminService.resetPassword(id, request);
        auditService.record(actor(principal), AuditService.ACTION_RESET_USER_PASSWORD,
                null, null, Map.of("targetUsername", user.getUsername()));
        return user;
    }

    @ExceptionHandler(UserAdminService.UserAdminException.class)
    public ResponseEntity<Map<String, String>> handleUserAdminException(UserAdminService.UserAdminException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
    }

    private Map<String, Object> userDetails(UserAccountResponse user) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetUsername", user.getUsername());
        details.put("targetRole", user.getRole());
        details.put("enabled", user.isEnabled());
        details.put("allKnowledgeBases", user.isAllKnowledgeBases());
        details.put("knowledgeBases", String.join(",", user.getKnowledgeBases()));
        return details;
    }

    private String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
