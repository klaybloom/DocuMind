package com.documind.service;

import com.documind.model.UserAccount;
import com.documind.repository.UserAccountRepository;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class KnowledgeBaseManagementServiceTest {

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    StreamingChatModel streamingChatModel;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Autowired
    KnowledgeBaseManagementService managementService;

    @Autowired
    KnowledgeBaseAccessService accessService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void existingKnowledgeBasesDefaultToAdminOwner() {
        managementService.syncKnownKnowledgeBases();

        assertThat(managementService.ownersOf(DocumentService.DEFAULT_KNOWLEDGE_BASE))
                .contains("admin");
    }

    @Test
    void ownerCanAccessOwnedKnowledgeBaseWithoutMemberGrant() {
        String owner = unique("owner");
        String kb = unique("HR");
        saveUser(owner, "USER", "default", true);

        managementService.createKnowledgeBase(kb, List.of(owner), adminAuth());

        assertThat(accessService.canAccess(userAuth(owner), kb)).isTrue();
    }

    @Test
    void ownerCanAddOwnerAndSelfTransfer() {
        String first = unique("first-owner");
        String second = unique("second-owner");
        String third = unique("third-owner");
        String kb = unique("Legal");
        saveUser(first, "USER", "default", true);
        saveUser(second, "USER", "default", true);
        saveUser(third, "USER", "default", true);
        managementService.createKnowledgeBase(kb, List.of(first), adminAuth());

        managementService.addOwners(kb, List.of(second), userAuth(first));
        assertThat(managementService.ownersOf(kb)).contains(first, second);

        managementService.selfTransfer(kb, List.of(third), userAuth(first));
        assertThat(managementService.ownersOf(kb)).contains(second, third).doesNotContain(first);
        assertThat(accessService.canAccess(userAuth(third), kb)).isTrue();
    }

    @Test
    void preventsKnowledgeBaseWithoutOwners() {
        String owner = unique("single-owner");
        String kb = unique("Finance");
        saveUser(owner, "USER", "default", true);
        managementService.createKnowledgeBase(kb, List.of(owner), adminAuth());

        assertThatThrownBy(() -> managementService.setOwners(kb, List.of(), adminAuth()))
                .isInstanceOf(KnowledgeBaseManagementService.KnowledgeBaseManagementException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ownerCanDistributeAccessToUsers() {
        String owner = unique("distributor");
        String reader = unique("reader");
        String kb = unique("Support");
        saveUser(owner, "USER", "default", true);
        saveUser(reader, "USER", "default", true);
        managementService.createKnowledgeBase(kb, List.of(owner), adminAuth());

        managementService.setMembers(kb, List.of(reader), userAuth(owner));

        UserAccount account = userAccountRepository.findByUsername(reader).orElseThrow();
        assertThat(account.getKnowledgeBases()).contains(kb);
        assertThat(accessService.canAccess(userAuth(reader), kb)).isTrue();
    }

    private void saveUser(String username, String role, String knowledgeBases, boolean enabled) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password-123"));
        user.setRole(role);
        user.setKnowledgeBases(knowledgeBases);
        user.setEnabled(enabled);
        userAccountRepository.save(user);
    }

    private Authentication adminAuth() {
        return auth("admin", "ROLE_ADMIN");
    }

    private Authentication userAuth(String username) {
        return auth(username, "ROLE_USER");
    }

    private Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "password",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
