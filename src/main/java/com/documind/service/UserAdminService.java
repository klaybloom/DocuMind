package com.documind.service;

import com.documind.dto.CreateUserRequest;
import com.documind.dto.ResetPasswordRequest;
import com.documind.dto.UpdateUserRequest;
import com.documind.dto.UserAccountResponse;
import com.documind.model.UserAccount;
import com.documind.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserAdminService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String ALL_KNOWLEDGE_BASES = "*";

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentService documentService;

    @Value("${app.security.min-password-length:12}")
    private int minPasswordLength;

    public UserAdminService(UserAccountRepository repository,
                            PasswordEncoder passwordEncoder,
                            DocumentService documentService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.documentService = documentService;
    }

    @Transactional(readOnly = true)
    public List<UserAccountResponse> listUsers() {
        return repository.findAll()
                .stream()
                .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserAccountResponse createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (repository.existsByUsername(username)) {
            throw new UserAdminException(HttpStatus.CONFLICT, "用户名已存在");
        }
        validatePassword(request.getPassword());

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setRole(normalizeRole(request.getRole()));
        account.setEnabled(request.getEnabled() == null || request.getEnabled());
        account.setKnowledgeBases(knowledgeBasesForRole(account.getRole(), request.getKnowledgeBases()));
        return toResponse(repository.save(account));
    }

    @Transactional
    public UserAccountResponse updateUser(Long id, UpdateUserRequest request) {
        UserAccount account = findUser(id);
        String newRole = normalizeRole(request.getRole() == null ? account.getRole() : request.getRole());
        boolean newEnabled = request.getEnabled() == null ? account.isEnabled() : request.getEnabled();
        preventRemovingLastEnabledAdmin(account, newRole, newEnabled);

        account.setRole(newRole);
        account.setEnabled(newEnabled);
        account.setKnowledgeBases(knowledgeBasesForRole(newRole, request.getKnowledgeBases(), account.getKnowledgeBases()));
        return toResponse(repository.save(account));
    }

    @Transactional
    public UserAccountResponse resetPassword(Long id, ResetPasswordRequest request) {
        UserAccount account = findUser(id);
        validatePassword(request.getPassword());
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        return toResponse(repository.save(account));
    }

    private UserAccount findUser(Long id) {
        if (id == null) {
            throw new UserAdminException(HttpStatus.BAD_REQUEST, "用户ID不能为空");
        }
        return repository.findById(id)
                .orElseThrow(() -> new UserAdminException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim();
        if (username.isEmpty()) {
            throw new UserAdminException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        return username;
    }

    private String normalizeRole(String value) {
        if (ROLE_ADMIN.equals(value)) {
            return ROLE_ADMIN;
        }
        return ROLE_USER;
    }

    private String knowledgeBasesForRole(String role, List<String> requested) {
        return knowledgeBasesForRole(role, requested, null);
    }

    private String knowledgeBasesForRole(String role, List<String> requested, String existing) {
        if (ROLE_ADMIN.equals(role)) {
            return null;
        }
        if (requested == null && existing != null && !existing.trim().isEmpty()) {
            return String.join(",", normalizeKnowledgeBases(Arrays.asList(existing.split(","))));
        }
        Set<String> normalized = normalizeKnowledgeBases(requested);
        return String.join(",", normalized);
    }

    private Set<String> normalizeKnowledgeBases(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return new LinkedHashSet<>(List.of(DocumentService.DEFAULT_KNOWLEDGE_BASE));
        }
        LinkedHashSet<String> values = requested.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .map(value -> ALL_KNOWLEDGE_BASES.equals(value)
                        ? ALL_KNOWLEDGE_BASES
                        : documentService.normalizeKnowledgeBase(value))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        if (values.contains(ALL_KNOWLEDGE_BASES)) {
            return new LinkedHashSet<>(List.of(ALL_KNOWLEDGE_BASES));
        }
        if (values.isEmpty()) {
            values.add(DocumentService.DEFAULT_KNOWLEDGE_BASE);
        }
        return values;
    }

    private List<String> parseKnowledgeBases(UserAccount account) {
        if (ROLE_ADMIN.equals(account.getRole())) {
            return List.of(ALL_KNOWLEDGE_BASES);
        }
        String value = account.getKnowledgeBases();
        if (value == null || value.trim().isEmpty()) {
            return List.of(DocumentService.DEFAULT_KNOWLEDGE_BASE);
        }
        List<String> values = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        return values.isEmpty() ? List.of(DocumentService.DEFAULT_KNOWLEDGE_BASE) : values;
    }

    private boolean hasAllKnowledgeBases(UserAccount account, List<String> knowledgeBases) {
        return ROLE_ADMIN.equals(account.getRole()) || knowledgeBases.contains(ALL_KNOWLEDGE_BASES);
    }

    private void validatePassword(String value) {
        int requiredLength = Math.max(8, minPasswordLength);
        if (value == null || value.trim().length() < requiredLength) {
            throw new UserAdminException(HttpStatus.BAD_REQUEST, "密码长度不能少于 " + requiredLength + " 字符");
        }
    }

    private void preventRemovingLastEnabledAdmin(UserAccount account, String newRole, boolean newEnabled) {
        boolean currentlyEnabledAdmin = account.isEnabled() && ROLE_ADMIN.equals(account.getRole());
        boolean remainsEnabledAdmin = newEnabled && ROLE_ADMIN.equals(newRole);
        if (!currentlyEnabledAdmin || remainsEnabledAdmin) {
            return;
        }
        long enabledAdmins = repository.findAll()
                .stream()
                .filter(candidate -> candidate.isEnabled() && ROLE_ADMIN.equals(candidate.getRole()))
                .count();
        if (enabledAdmins <= 1) {
            throw new UserAdminException(HttpStatus.CONFLICT, "至少需要保留一个启用的管理员");
        }
    }

    private UserAccountResponse toResponse(UserAccount account) {
        List<String> knowledgeBases = parseKnowledgeBases(account);
        return new UserAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.isEnabled(),
                hasAllKnowledgeBases(account, knowledgeBases),
                knowledgeBases
        );
    }

    public static class UserAdminException extends RuntimeException {
        private final HttpStatus status;

        public UserAdminException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }
}
