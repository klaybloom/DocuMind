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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKnowledgeBaseControllerHttpTest {

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
    void adminCreatesKnowledgeBaseAndOwnerListsOnlyOwnedKnowledgeBases() throws Exception {
        String owner = unique("owner");
        String otherOwner = unique("other-owner");
        String kb = unique("HR");
        String otherKb = unique("Legal");
        saveUser(owner, "owner-password", "USER", "default", true);
        saveUser(otherOwner, "other-password", "USER", "default", true);

        createKnowledgeBase(kb, owner);
        createKnowledgeBase(otherKb, otherOwner);

        String body = mockMvc.perform(get("/api/v1/admin/knowledge-bases")
                        .with(httpBasic(owner, "owner-password")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode list = objectMapper.readTree(body);
        assertThat(list).anyMatch(node -> kb.equals(node.get("knowledgeBase").asText()));
        assertThat(list).noneMatch(node -> otherKb.equals(node.get("knowledgeBase").asText()));
    }

    @Test
    void ownerCanDistributeMembersButNonOwnerCannot() throws Exception {
        String owner = unique("owner");
        String reader = unique("reader");
        String outsider = unique("outsider");
        String kb = unique("Support");
        saveUser(owner, "owner-password", "USER", "default", true);
        saveUser(reader, "reader-password", "USER", "default", true);
        saveUser(outsider, "outsider-password", "USER", "default", true);
        createKnowledgeBase(kb, owner);

        mockMvc.perform(put("/api/v1/admin/knowledge-bases/{kb}/members", kb)
                        .with(httpBasic(owner, "owner-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "members": ["%s"] }
                                """.formatted(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0]").value(reader));

        assertThat(userAccountRepository.findByUsername(reader).orElseThrow().getKnowledgeBases())
                .contains(kb);

        mockMvc.perform(put("/api/v1/admin/knowledge-bases/{kb}/members", kb)
                        .with(httpBasic(outsider, "outsider-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "members": ["%s"] }
                                """.formatted(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanSelfTransferKnowledgeBase() throws Exception {
        String owner = unique("transfer-owner");
        String newOwner = unique("new-owner");
        String kb = unique("Finance");
        saveUser(owner, "owner-password", "USER", "default", true);
        saveUser(newOwner, "new-owner-password", "USER", "default", true);
        createKnowledgeBase(kb, owner);

        mockMvc.perform(put("/api/v1/admin/knowledge-bases/{kb}/owners/self-transfer", kb)
                        .with(httpBasic(owner, "owner-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "owners": ["%s"] }
                                """.formatted(newOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owners[0]").value(newOwner));

        mockMvc.perform(get("/api/v1/admin/knowledge-bases")
                        .with(httpBasic(owner, "owner-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.knowledgeBase=='%s')]".formatted(kb)).isEmpty());
    }

    @Test
    void ownerCannotCreateKnowledgeBase() throws Exception {
        String owner = unique("creator");
        saveUser(owner, "owner-password", "USER", "default", true);

        mockMvc.perform(post("/api/v1/admin/knowledge-bases")
                        .with(httpBasic(owner, "owner-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "owners": ["%s"] }
                                """.formatted(unique("NewKB"), owner)))
                .andExpect(status().isForbidden());
    }

    private void createKnowledgeBase(String name, String owner) throws Exception {
        mockMvc.perform(post("/api/v1/admin/knowledge-bases")
                        .with(httpBasic("admin", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "owners": ["%s"] }
                                """.formatted(name, owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.knowledgeBase").value(name));
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
