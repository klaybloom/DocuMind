package com.documind.service;

import com.documind.model.UserAccount;
import com.documind.repository.KnowledgeBaseOwnerRepository;
import com.documind.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseAccessServiceTest {

    private KnowledgeBaseAccessService accessService;
    private UserAccountRepository repository;
    private KnowledgeBaseOwnerRepository ownerRepository;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        ownerRepository = mock(KnowledgeBaseOwnerRepository.class);
        accessService = new KnowledgeBaseAccessService(new DocumentService(), repository, ownerRepository);
    }

    @Test
    void adminCanAccessEveryKnowledgeBase() {
        Authentication admin = auth("admin", "ROLE_ADMIN", "ROLE_USER");

        assertThat(accessService.canAccess(admin, "HR")).isTrue();
        assertThat(accessService.filterAccessible(admin, List.of("default", "HR", "Legal")))
                .containsExactly("default", "HR", "Legal");
    }

    @Test
    void userDefaultsToDefaultKnowledgeBaseOnly() {
        when(repository.findByUsername("reader")).thenReturn(Optional.of(account("reader", "USER", true, null)));
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "default")).isTrue();
        assertThat(accessService.canAccess(user, "HR")).isFalse();
        assertThat(accessService.filterAccessible(user, List.of("default", "HR")))
                .containsExactly("default");
    }

    @Test
    void userCanAccessConfiguredKnowledgeBases() {
        when(repository.findByUsername("reader")).thenReturn(Optional.of(account("reader", "USER", true, "default, HR, Legal")));
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "HR")).isTrue();
        assertThat(accessService.canAccess(user, "Legal")).isTrue();
        assertThat(accessService.canAccess(user, "Finance")).isFalse();
    }

    @Test
    void ownerCanAccessOwnedKnowledgeBaseWithoutUserGrant() {
        when(repository.findByUsername("owner")).thenReturn(Optional.of(account("owner", "USER", true, "default")));
        when(ownerRepository.existsByKnowledgeBaseAndUsername("HR", "owner")).thenReturn(true);
        Authentication user = auth("owner", "ROLE_USER");

        assertThat(accessService.canAccess(user, "HR")).isTrue();
        assertThat(accessService.filterAccessible(user, List.of("default", "HR", "Legal")))
                .containsExactly("default", "HR");
    }

    @Test
    void userCanAccessAllKnowledgeBasesWithWildcard() {
        when(repository.findByUsername("reader")).thenReturn(Optional.of(account("reader", "USER", true, "*")));
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "HR")).isTrue();
        assertThat(accessService.canAccess(user, "Finance")).isTrue();
    }

    @Test
    void disabledUserCannotAccessKnowledgeBase() {
        when(repository.findByUsername("reader")).thenReturn(Optional.of(account("reader", "USER", false, "HR")));
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "HR")).isFalse();
    }

    @Test
    void missingUserCannotAccessKnowledgeBase() {
        when(repository.findByUsername("reader")).thenReturn(Optional.empty());
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "HR")).isFalse();
    }

    @Test
    void unauthenticatedUserCannotAccessKnowledgeBase() {
        assertThat(accessService.canAccess(null, "default")).isFalse();
    }

    private UserAccount account(String username, String role, boolean enabled, String knowledgeBases) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setRole(role);
        account.setEnabled(enabled);
        account.setKnowledgeBases(knowledgeBases);
        account.setPassword("{noop}password");
        return account;
    }

    private Authentication auth(String username, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "password",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
