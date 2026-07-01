package com.documind.config;

import com.documind.model.UserAccount;
import com.documind.repository.UserAccountRepository;
import com.documind.service.UserAccountInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityConfigTest {

    @Test
    void initializerCreatesAdminAndOptionalUserAccounts() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        when(repo.findByUsername(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserAccountInitializer initializer = new UserAccountInitializer(repo, encoder);
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-pass-123");
        ReflectionTestUtils.setField(initializer, "userUsername", "reader");
        ReflectionTestUtils.setField(initializer, "userPassword", "reader-pass-123");
        ReflectionTestUtils.setField(initializer, "userKnowledgeBases", "default");
        ReflectionTestUtils.setField(initializer, "minPasswordLength", 12);
        initializer.init();

        verify(repo, times(2)).save(any(UserAccount.class));
    }

    @Test
    void initializerDoesNotOverwriteExistingRegularUserKnowledgeBases() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserAccount existingUser = new UserAccount();
        existingUser.setUsername("reader");
        existingUser.setPassword(encoder.encode("old-reader-pass"));
        existingUser.setRole("USER");
        existingUser.setEnabled(true);
        existingUser.setKnowledgeBases("HR");
        when(repo.findByUsername("admin")).thenReturn(Optional.empty());
        when(repo.findByUsername("reader")).thenReturn(Optional.of(existingUser));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserAccountInitializer initializer = new UserAccountInitializer(repo, encoder);
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-pass-123");
        ReflectionTestUtils.setField(initializer, "userUsername", "reader");
        ReflectionTestUtils.setField(initializer, "userPassword", "reader-pass-123");
        ReflectionTestUtils.setField(initializer, "userKnowledgeBases", "default");
        ReflectionTestUtils.setField(initializer, "minPasswordLength", 12);
        initializer.init();

        assertThat(existingUser.getKnowledgeBases()).isEqualTo("HR");
    }

    @Test
    void initializerRejectsMissingAdminPassword() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        UserAccountInitializer initializer = new UserAccountInitializer(repo,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");
        ReflectionTestUtils.setField(initializer, "minPasswordLength", 12);

        assertThatThrownBy(initializer::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_ADMIN_PASSWORD");
    }

    @Test
    void initializerRejectsPartialRegularUserConfig() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        UserAccountInitializer initializer = new UserAccountInitializer(repo,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-pass-123");
        ReflectionTestUtils.setField(initializer, "userUsername", "reader");
        ReflectionTestUtils.setField(initializer, "userPassword", "");
        ReflectionTestUtils.setField(initializer, "minPasswordLength", 12);

        assertThatThrownBy(initializer::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_USER_USERNAME");
    }

    @Test
    void initializerRejectsWeakAdminPassword() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        UserAccountInitializer initializer = new UserAccountInitializer(repo,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "short");
        ReflectionTestUtils.setField(initializer, "minPasswordLength", 12);

        assertThatThrownBy(initializer::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_ADMIN_PASSWORD")
                .hasMessageContaining("12");
    }

    @Test
    void initializerRejectsWeakRegularUserPassword() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        UserAccountInitializer initializer = new UserAccountInitializer(repo,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-pass-123");
        ReflectionTestUtils.setField(initializer, "userUsername", "reader");
        ReflectionTestUtils.setField(initializer, "userPassword", "short");
        ReflectionTestUtils.setField(initializer, "minPasswordLength", 12);

        assertThatThrownBy(initializer::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_USER_PASSWORD")
                .hasMessageContaining("12");
    }

    @Test
    void initializerRejectsDuplicateUsernames() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        UserAccountInitializer initializer = new UserAccountInitializer(repo,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-pass-123");
        ReflectionTestUtils.setField(initializer, "userUsername", "ADMIN");
        ReflectionTestUtils.setField(initializer, "userPassword", "reader-pass-123");
        ReflectionTestUtils.setField(initializer, "minPasswordLength", 12);

        assertThatThrownBy(initializer::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能和 DOCUMIND_ADMIN_USERNAME 相同");
    }
}
