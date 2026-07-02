package com.documind.service;

import com.documind.dto.KnowledgeBaseAdminResponse;
import com.documind.dto.KnowledgeBaseStatus;
import com.documind.dto.UserOptionResponse;
import com.documind.model.KnowledgeBaseEntity;
import com.documind.model.KnowledgeBaseOwnerEntity;
import com.documind.model.UserAccount;
import com.documind.repository.KnowledgeBaseOwnerRepository;
import com.documind.repository.KnowledgeBaseRepository;
import com.documind.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库管理服务，封装创建、成员维护和所有者权限校验。
 */
@Service
public class KnowledgeBaseManagementService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String ALL_KNOWLEDGE_BASES = "*";

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseOwnerRepository ownerRepository;
    private final UserAccountRepository userAccountRepository;
    private final DocumentService documentService;

    @Value("${app.security.admin-username:admin}")
    private String adminUsername;

    public KnowledgeBaseManagementService(KnowledgeBaseRepository knowledgeBaseRepository,
                                          KnowledgeBaseOwnerRepository ownerRepository,
                                          UserAccountRepository userAccountRepository,
                                          DocumentService documentService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.ownerRepository = ownerRepository;
        this.userAccountRepository = userAccountRepository;
        this.documentService = documentService;
    }

    @Transactional
    public void syncKnownKnowledgeBases() {
        syncKnownKnowledgeBases(actorFallback(null));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseAdminResponse> listManageable(Authentication authentication) {
        List<String> manageable = manageableKnowledgeBases(authentication);
        Set<String> manageableSet = new LinkedHashSet<>(manageable);
        return knowledgeBaseRepository.findAllByOrderByNameAsc()
                .stream()
                .filter(kb -> isAdmin(authentication) || manageableSet.contains(kb.getName()))
                .map(kb -> toResponse(kb, authentication))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> manageableKnowledgeBases(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }
        if (isAdmin(authentication)) {
            return knowledgeBaseRepository.findAllByOrderByNameAsc()
                    .stream()
                    .map(KnowledgeBaseEntity::getName)
                    .toList();
        }
        String username = authentication.getName();
        return ownerRepository.findByUsernameOrderByKnowledgeBaseAsc(username)
                .stream()
                .map(KnowledgeBaseOwnerEntity::getKnowledgeBase)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> allKnowledgeBaseNames() {
        return knowledgeBaseRepository.findAllByOrderByNameAsc()
                .stream()
                .map(KnowledgeBaseEntity::getName)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean canManage(Authentication authentication, String knowledgeBase) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        return ownerRepository.existsByKnowledgeBaseAndUsername(kb, authentication.getName());
    }

    @Transactional(readOnly = true)
    public void requireCanManage(Authentication authentication, String knowledgeBase) {
        if (!canManage(authentication, knowledgeBase)) {
            throw new KnowledgeBaseManagementException(HttpStatus.FORBIDDEN, "当前账号没有该知识库管理权限");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasManagedKnowledgeBases(Authentication authentication) {
        return isAdmin(authentication) || !manageableKnowledgeBases(authentication).isEmpty();
    }

    @Transactional
    public KnowledgeBaseAdminResponse createKnowledgeBase(String name, List<String> requestedOwners, Authentication authentication) {
        requireAdmin(authentication);
        String kb = documentService.normalizeKnowledgeBase(name);
        if (knowledgeBaseRepository.existsByName(kb)) {
            throw new KnowledgeBaseManagementException(HttpStatus.CONFLICT, "知识库已存在");
        }
        KnowledgeBaseEntity entity = knowledgeBaseRepository.save(new KnowledgeBaseEntity(kb, actorFallback(authentication)));
        Set<String> owners = normalizeUsernames(requestedOwners);
        if (owners.isEmpty()) {
            owners.add(actorFallback(authentication));
        }
        replaceOwners(kb, owners);
        return toResponse(entity, authentication);
    }

    @Transactional
    public KnowledgeBaseAdminResponse ensureMetadata(String knowledgeBase, Authentication authentication) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        syncOne(kb, actorFallback(authentication));
        return knowledgeBaseRepository.findByName(kb)
                .map(entity -> toResponse(entity, authentication))
                .orElseThrow(() -> new KnowledgeBaseManagementException(HttpStatus.NOT_FOUND, "知识库不存在"));
    }

    @Transactional
    public KnowledgeBaseAdminResponse setOwners(String knowledgeBase, List<String> requestedOwners, Authentication authentication) {
        requireAdmin(authentication);
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        ensureKnowledgeBaseExists(kb);
        Set<String> owners = normalizeUsernames(requestedOwners);
        replaceOwners(kb, owners);
        return responseByName(kb, authentication);
    }

    @Transactional
    public KnowledgeBaseAdminResponse addOwners(String knowledgeBase, List<String> requestedOwners, Authentication authentication) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        requireCanManage(authentication, kb);
        ensureKnowledgeBaseExists(kb);
        Set<String> owners = normalizeUsernames(requestedOwners);
        if (owners.isEmpty()) {
            throw new KnowledgeBaseManagementException(HttpStatus.BAD_REQUEST, "负责人不能为空");
        }
        owners.forEach(username -> addOwnerIfAbsent(kb, username));
        return responseByName(kb, authentication);
    }

    @Transactional
    public KnowledgeBaseAdminResponse selfTransfer(String knowledgeBase, List<String> requestedOwners, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new KnowledgeBaseManagementException(HttpStatus.FORBIDDEN, "当前账号没有该知识库管理权限");
        }
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        String actor = authentication.getName();
        if (!ownerRepository.existsByKnowledgeBaseAndUsername(kb, actor)) {
            throw new KnowledgeBaseManagementException(HttpStatus.FORBIDDEN, "当前账号不是该知识库负责人");
        }
        ensureKnowledgeBaseExists(kb);
        Set<String> owners = normalizeUsernames(requestedOwners);
        owners.remove(actor);
        if (owners.isEmpty()) {
            throw new KnowledgeBaseManagementException(HttpStatus.BAD_REQUEST, "新负责人不能为空");
        }
        owners.forEach(username -> addOwnerIfAbsent(kb, username));
        ownerRepository.deleteByKnowledgeBaseAndUsername(kb, actor);
        if (ownerRepository.countByKnowledgeBase(kb) <= 0) {
            throw new KnowledgeBaseManagementException(HttpStatus.CONFLICT, "知识库至少需要一个负责人");
        }
        return responseByName(kb, authentication);
    }

    @Transactional
    public KnowledgeBaseAdminResponse setMembers(String knowledgeBase, List<String> requestedMembers, Authentication authentication) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        requireCanManage(authentication, kb);
        ensureKnowledgeBaseExists(kb);
        Set<String> members = normalizeUsernames(requestedMembers);
        for (String username : members) {
            requireEnabledUser(username);
        }
        List<UserAccount> accounts = userAccountRepository.findAll();
        for (UserAccount account : accounts) {
            if (!ROLE_USER.equals(account.getRole())) {
                continue;
            }
            Set<String> grants = parseKnowledgeBases(account.getKnowledgeBases());
            if (grants.contains(ALL_KNOWLEDGE_BASES)) {
                continue;
            }
            boolean changed;
            if (members.contains(account.getUsername())) {
                changed = grants.add(kb);
            } else {
                changed = grants.remove(kb);
            }
            if (changed) {
                account.setKnowledgeBases(joinKnowledgeBases(grants));
                userAccountRepository.save(account);
            }
        }
        return responseByName(kb, authentication);
    }

    @Transactional(readOnly = true)
    public List<UserOptionResponse> listUserOptions() {
        return userAccountRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(account -> new UserOptionResponse(account.getUsername(), account.getRole(), account.isEnabled()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isOwner(String username, String knowledgeBase) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        return ownerRepository.existsByKnowledgeBaseAndUsername(kb, username.trim());
    }

    @Transactional(readOnly = true)
    public List<String> ownersOf(String knowledgeBase) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        return ownerRepository.findByKnowledgeBaseOrderByUsernameAsc(kb)
                .stream()
                .map(KnowledgeBaseOwnerEntity::getUsername)
                .toList();
    }

    private void syncKnownKnowledgeBases(String defaultOwner) {
        for (String kb : documentService.listKnowledgeBases()) {
            syncOne(kb, defaultOwner);
        }
    }

    private void syncOne(String knowledgeBase, String defaultOwner) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        knowledgeBaseRepository.findByName(kb)
                .orElseGet(() -> knowledgeBaseRepository.save(new KnowledgeBaseEntity(kb, defaultOwner)));
        if (ownerRepository.countByKnowledgeBase(kb) <= 0) {
            addOwnerIfAbsent(kb, defaultOwner);
        }
    }

    private KnowledgeBaseAdminResponse responseByName(String knowledgeBase, Authentication authentication) {
        return knowledgeBaseRepository.findByName(knowledgeBase)
                .map(entity -> toResponse(entity, authentication))
                .orElseThrow(() -> new KnowledgeBaseManagementException(HttpStatus.NOT_FOUND, "知识库不存在"));
    }

    private KnowledgeBaseAdminResponse toResponse(KnowledgeBaseEntity entity, Authentication authentication) {
        String kb = entity.getName();
        List<String> owners = ownersOf(kb);
        List<String> members = membersOf(kb);
        KnowledgeBaseStatus status = statusOf(kb);
        return new KnowledgeBaseAdminResponse(
                kb,
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                owners,
                members,
                status,
                isAdmin(authentication) || owners.contains(authentication == null ? "" : authentication.getName()),
                owners.contains(authentication == null ? "" : authentication.getName())
        );
    }

    private KnowledgeBaseStatus statusOf(String knowledgeBase) {
        return documentService.listKnowledgeBaseStatuses()
                .stream()
                .filter(status -> Objects.equals(status.getKnowledgeBase(), knowledgeBase))
                .findFirst()
                .orElseGet(() -> new KnowledgeBaseStatus(knowledgeBase, 0, 0, 0, 0, 0, 0, 0));
    }

    private List<String> membersOf(String knowledgeBase) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        return userAccountRepository.findAll()
                .stream()
                .filter(account -> ROLE_USER.equals(account.getRole()))
                .filter(UserAccount::isEnabled)
                .filter(account -> {
                    Set<String> grants = parseKnowledgeBases(account.getKnowledgeBases());
                    return grants.contains(ALL_KNOWLEDGE_BASES) || grants.contains(kb);
                })
                .map(UserAccount::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void replaceOwners(String knowledgeBase, Set<String> owners) {
        if (owners == null || owners.isEmpty()) {
            throw new KnowledgeBaseManagementException(HttpStatus.BAD_REQUEST, "知识库至少需要一个负责人");
        }
        for (String owner : owners) {
            requireEnabledUser(owner);
        }
        ownerRepository.deleteByKnowledgeBaseAndUsernameNotIn(knowledgeBase, owners);
        owners.forEach(username -> addOwnerIfAbsent(knowledgeBase, username));
        if (ownerRepository.countByKnowledgeBase(knowledgeBase) <= 0) {
            throw new KnowledgeBaseManagementException(HttpStatus.CONFLICT, "知识库至少需要一个负责人");
        }
    }

    private void addOwnerIfAbsent(String knowledgeBase, String username) {
        String normalized = normalizeUsername(username);
        requireEnabledUser(normalized);
        if (!ownerRepository.existsByKnowledgeBaseAndUsername(knowledgeBase, normalized)) {
            ownerRepository.save(new KnowledgeBaseOwnerEntity(knowledgeBase, normalized));
        }
    }

    private UserAccount requireEnabledUser(String username) {
        String normalized = normalizeUsername(username);
        UserAccount account = userAccountRepository.findByUsername(normalized)
                .orElseThrow(() -> new KnowledgeBaseManagementException(HttpStatus.BAD_REQUEST, "用户不存在: " + normalized));
        if (!account.isEnabled()) {
            throw new KnowledgeBaseManagementException(HttpStatus.BAD_REQUEST, "用户已停用: " + normalized);
        }
        return account;
    }

    private void ensureKnowledgeBaseExists(String knowledgeBase) {
        if (!knowledgeBaseRepository.existsByName(knowledgeBase)) {
            throw new KnowledgeBaseManagementException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
    }

    private Set<String> normalizeUsernames(List<String> usernames) {
        if (usernames == null) {
            return new LinkedHashSet<>();
        }
        return usernames.stream()
                .map(this::normalizeUsername)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private Set<String> parseKnowledgeBases(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new LinkedHashSet<>(List.of(DocumentService.DEFAULT_KNOWLEDGE_BASE));
        }
        LinkedHashSet<String> grants = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> ALL_KNOWLEDGE_BASES.equals(item) ? item : documentService.normalizeKnowledgeBase(item))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (grants.isEmpty()) {
            grants.add(DocumentService.DEFAULT_KNOWLEDGE_BASE);
        }
        return grants;
    }

    private String joinKnowledgeBases(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return DocumentService.DEFAULT_KNOWLEDGE_BASE;
        }
        if (values.contains(ALL_KNOWLEDGE_BASES)) {
            return ALL_KNOWLEDGE_BASES;
        }
        List<String> normalized = new ArrayList<>(values);
        normalized.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(",", normalized);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private void requireAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new KnowledgeBaseManagementException(HttpStatus.FORBIDDEN, "当前账号没有管理员权限");
        }
    }

    private String actorFallback(Authentication authentication) {
        if (authentication != null && authentication.getName() != null && !authentication.getName().trim().isEmpty()) {
            return authentication.getName().trim();
        }
        return adminUsername == null || adminUsername.trim().isEmpty() ? "admin" : adminUsername.trim();
    }

    public static class KnowledgeBaseManagementException extends RuntimeException {
        private final HttpStatus status;

        public KnowledgeBaseManagementException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }
}
