package com.documind.controller;

import com.documind.model.UserAccount;
import com.documind.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerHttpTest {

    private static final String ADMIN_PASSWORD = "test-profile-admin-password";

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    StreamingChatModel streamingChatModel;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void nonAdminCannotListUsers() throws Exception {
        String username = unique("reader");
        saveUser(username, "reader-password", "USER", "HR", true);

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(httpBasic(username, "reader-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void plainUserCannotLoadUserOptionsForDropdowns() throws Exception {
        String username = unique("reader");
        saveUser(username, "reader-password", "USER", "default", true);

        mockMvc.perform(get("/api/v1/admin/users/options")
                        .with(httpBasic(username, "reader-password")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("当前账号没有后台管理权限")));
    }

    @Test
    void knowledgeBaseOwnerCanLoadUserOptionsForDropdowns() throws Exception {
        String username = unique("owner");
        String kb = unique("OptionsKB");
        saveUser(username, "owner-password", "USER", "default", true);

        mockMvc.perform(post("/api/v1/admin/knowledge-bases")
                        .with(httpBasic("admin", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "owners": ["%s"]
                                }
                                """.formatted(kb, username)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/v1/admin/users/options")
                        .with(httpBasic(username, "owner-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("password");
    }

    @Test
    void adminListsUsersWithoutPasswordHashes() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/users")
                        .with(httpBasic("admin", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("password");
    }

    @Test
    void adminCreatesUpdatesAndResetsUser() throws Exception {
        String username = unique("analyst");
        String createBody = """
                {
                  "username": "%s",
                  "password": "analyst-password",
                  "role": "USER",
                  "enabled": true,
                  "knowledgeBases": ["HR"]
                }
                """.formatted(username);

        String createdJson = mockMvc.perform(post("/api/v1/admin/users")
                        .with(httpBasic("admin", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.knowledgeBases[0]").value("HR"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdJson);
        long id = created.get("id").asLong();

        mockMvc.perform(put("/api/v1/admin/users/{id}", id)
                        .with(httpBasic("admin", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "USER",
                                  "enabled": false,
                                  "knowledgeBases": ["Legal"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.knowledgeBases[0]").value("Legal"));

        mockMvc.perform(put("/api/v1/admin/users/{id}/password", id)
                        .with(httpBasic("admin", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "new-analyst-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        UserAccount account = userAccountRepository.findByUsername(username).orElseThrow();
        assertThat(passwordEncoder.matches("new-analyst-password", account.getPassword())).isTrue();
    }

    @Test
    void adminCreateRejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(httpBasic("admin", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "weak-user",
                                  "password": "short",
                                  "role": "USER",
                                  "enabled": true,
                                  "knowledgeBases": ["default"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("密码长度不能少于 8 字符")));
    }

    @Test
    void adminCannotDisableTheLastEnabledAdmin() throws Exception {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();

        mockMvc.perform(put("/api/v1/admin/users/{id}", admin.getId())
                        .with(httpBasic("admin", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "USER",
                                  "enabled": false,
                                  "knowledgeBases": ["default"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("至少需要保留一个启用的管理员")));
    }

    private void saveUser(String username, String password, String role, String knowledgeBases, boolean enabled) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setKnowledgeBases(knowledgeBases);
        user.setEnabled(enabled);
        userAccountRepository.save(user);
    }

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
