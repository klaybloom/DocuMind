package com.documind;

import com.documind.model.DocumentFileEntity;
import com.documind.model.UserAccount;
import com.documind.repository.AuditEventRepository;
import com.documind.repository.DocumentFileRepository;
import com.documind.repository.KnowledgeBaseOwnerRepository;
import com.documind.repository.KnowledgeBaseRepository;
import com.documind.repository.UserAccountRepository;
import com.documind.service.AuditService;
import com.documind.service.DocumentService;
import com.documind.service.KnowledgeBaseAccessService;
import com.documind.service.KnowledgeBaseManagementService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("postgres")
@TestPropertySource(properties = {
        "app.documents-path=${java.io.tmpdir}/documind-postgres-it",
        "app.audit.max-events=2",
        "app.security.admin-password=postgres-integration-admin-password"
})
class PostgresIntegrationIT {

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    StreamingChatModel streamingChatModel;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Autowired
    DocumentService documentService;

    @Autowired
    AuditService auditService;

    @Autowired
    KnowledgeBaseManagementService knowledgeBaseManagementService;

    @Autowired
    KnowledgeBaseAccessService knowledgeBaseAccessService;

    @Autowired
    DocumentFileRepository documentFileRepository;

    @Autowired
    AuditEventRepository auditEventRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    KnowledgeBaseOwnerRepository knowledgeBaseOwnerRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanMutableData() {
        auditEventRepository.deleteAll();
        documentFileRepository.deleteAll();
        knowledgeBaseOwnerRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();
    }

    @Test
    void postgresProfileUsesFlywaySchemaForCoreDatabasePaths() {
        assertThat(userAccountRepository.existsByUsername("admin")).isTrue();

        String kb = unique("HR");
        String owner = unique("owner");
        saveUser(owner, "USER", "default");

        knowledgeBaseManagementService.createKnowledgeBase(kb, List.of(owner), adminAuth());
        assertThat(knowledgeBaseRepository.findByName(kb)).isPresent();
        assertThat(knowledgeBaseOwnerRepository.existsByKnowledgeBaseAndUsername(kb, owner)).isTrue();
        assertThat(knowledgeBaseAccessService.canAccess(userAuth(owner), kb)).isTrue();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "PostgreSQL profile document".getBytes(StandardCharsets.UTF_8));
        documentService.storeFile(file, kb, owner, "admin");
        DocumentFileEntity stored = documentFileRepository.findByKnowledgeBaseAndFileName(kb, "policy.txt").orElseThrow();
        assertThat(stored.getFileHash()).hasSize(64);

        documentService.recordKnowledgeGap(kb, "PostgreSQL gap question?", "pg-session-1");
        documentService.recordKnowledgeGap(kb, "PostgreSQL gap question?", "pg-session-2");
        assertThat(documentService.listKnowledgeGaps(kb))
                .anySatisfy(gap -> {
                    assertThat(gap.getQuestion()).isEqualTo("PostgreSQL gap question?");
                    assertThat(gap.getOccurrences()).isEqualTo(2);
                });

        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, kb, "one.txt", Map.of("index", 1));
        auditService.record("admin", AuditService.ACTION_UPLOAD_DOCUMENT, kb, "two.txt", Map.of("index", 2));
        auditService.record("admin", AuditService.ACTION_DELETE_DOCUMENT, kb, "three.txt", Map.of("index", 3));
        assertThat(auditEventRepository.count()).isEqualTo(2);
        assertThat(auditService.listRecent(10)).hasSize(2);
    }

    private void saveUser(String username, String role, String knowledgeBases) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password-123"));
        user.setRole(role);
        user.setKnowledgeBases(knowledgeBases);
        user.setEnabled(true);
        userAccountRepository.save(user);
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return auth("admin", "ROLE_ADMIN");
    }

    private UsernamePasswordAuthenticationToken userAuth(String username) {
        return auth(username, "ROLE_USER");
    }

    private UsernamePasswordAuthenticationToken auth(String username, String authority) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "password",
                List.of(new SimpleGrantedAuthority(authority)));
    }

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
