package com.documind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseAccessServiceTest {

    private KnowledgeBaseAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new KnowledgeBaseAccessService(new DocumentService());
        ReflectionTestUtils.setField(accessService, "userKnowledgeBases", "default");
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
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "default")).isTrue();
        assertThat(accessService.canAccess(user, "HR")).isFalse();
        assertThat(accessService.filterAccessible(user, List.of("default", "HR")))
                .containsExactly("default");
    }

    @Test
    void userCanAccessConfiguredKnowledgeBases() {
        ReflectionTestUtils.setField(accessService, "userKnowledgeBases", "default, HR, Legal");
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "HR")).isTrue();
        assertThat(accessService.canAccess(user, "Legal")).isTrue();
        assertThat(accessService.canAccess(user, "Finance")).isFalse();
    }

    @Test
    void userCanAccessAllKnowledgeBasesWithWildcard() {
        ReflectionTestUtils.setField(accessService, "userKnowledgeBases", "*");
        Authentication user = auth("reader", "ROLE_USER");

        assertThat(accessService.canAccess(user, "HR")).isTrue();
        assertThat(accessService.canAccess(user, "Finance")).isTrue();
    }

    @Test
    void unauthenticatedUserCannotAccessKnowledgeBase() {
        assertThat(accessService.canAccess(null, "default")).isFalse();
    }

    private Authentication auth(String username, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "password",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
