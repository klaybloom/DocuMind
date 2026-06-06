package com.demo.ragchat.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void userDetailsServiceCreatesAdminAndOptionalUserRoles() {
        SecurityConfig config = securityConfig("admin", "admin-pass-123", "reader", "reader-pass-123");
        PasswordEncoder passwordEncoder = config.passwordEncoder();

        UserDetailsService users = config.userDetailsService(passwordEncoder);
        UserDetails admin = users.loadUserByUsername("admin");
        UserDetails reader = users.loadUserByUsername("reader");

        assertThat(passwordEncoder.matches("admin-pass-123", admin.getPassword())).isTrue();
        assertThat(admin.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_USER");
        assertThat(reader.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    void userDetailsServiceRejectsMissingAdminPassword() {
        SecurityConfig config = securityConfig("admin", "", "", "");

        assertThatThrownBy(() -> config.userDetailsService(config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_ADMIN_USERNAME");
    }

    @Test
    void userDetailsServiceRejectsPartialRegularUserConfig() {
        SecurityConfig config = securityConfig("admin", "admin-pass-123", "reader", "");

        assertThatThrownBy(() -> config.userDetailsService(config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_USER_USERNAME");
    }

    @Test
    void userDetailsServiceRejectsWeakAdminPassword() {
        SecurityConfig config = securityConfig("admin", "short", "", "");

        assertThatThrownBy(() -> config.userDetailsService(config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_ADMIN_PASSWORD")
                .hasMessageContaining("12");
    }

    @Test
    void userDetailsServiceRejectsWeakRegularUserPassword() {
        SecurityConfig config = securityConfig("admin", "admin-pass-123", "reader", "short");

        assertThatThrownBy(() -> config.userDetailsService(config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_USER_PASSWORD")
                .hasMessageContaining("12");
    }

    @Test
    void userDetailsServiceRejectsDuplicateUsernames() {
        SecurityConfig config = securityConfig("admin", "admin-pass-123", "ADMIN", "reader-pass-123");

        assertThatThrownBy(() -> config.userDetailsService(config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能和 DOCUMIND_ADMIN_USERNAME 相同");
    }

    private SecurityConfig securityConfig(String adminUsername,
                                          String adminPassword,
                                          String userUsername,
                                          String userPassword) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", adminUsername);
        ReflectionTestUtils.setField(config, "adminPassword", adminPassword);
        ReflectionTestUtils.setField(config, "userUsername", userUsername);
        ReflectionTestUtils.setField(config, "userPassword", userPassword);
        ReflectionTestUtils.setField(config, "minPasswordLength", 12);
        return config;
    }
}
