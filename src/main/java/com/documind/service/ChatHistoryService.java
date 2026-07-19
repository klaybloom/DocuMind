package com.documind.service;

import com.documind.model.UserAccount;
import com.documind.repository.UserAccountRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MySQL 是会话原文的唯一事实来源。Redis 清空或过期后，这个服务负责恢复最近窗口。
 */
@Service
public class ChatHistoryService {

    private final JdbcTemplate jdbcTemplate;
    private final UserAccountRepository userAccountRepository;

    public ChatHistoryService(JdbcTemplate jdbcTemplate, UserAccountRepository userAccountRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public void appendExchange(String scopedSessionId, String knowledgeBase, String question, String answer) {
        Optional<UserAccount> user = userForScopedSession(scopedSessionId);
        if (user.isEmpty()) {
            return;
        }
        String conversationId = findOrCreateConversation(user.get().getId(), scopedSessionId, knowledgeBase);
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("INSERT INTO chat_messages (conversation_id, message_role, content, created_at) VALUES (?, ?, ?, ?)",
                conversationId, "USER", question, now);
        jdbcTemplate.update("INSERT INTO chat_messages (conversation_id, message_role, content, created_at) VALUES (?, ?, ?, ?)",
                conversationId, "ASSISTANT", answer, now);
        jdbcTemplate.update("UPDATE chat_conversations SET updated_at = ? WHERE id = ?", now, conversationId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> latestMessages(String scopedSessionId, String knowledgeBase, int maxMessages) {
        Optional<UserAccount> user = userForScopedSession(scopedSessionId);
        if (user.isEmpty()) {
            return List.of();
        }
        List<StoredChatMessage> reversed = jdbcTemplate.query(
                "SELECT m.message_role, m.content FROM chat_messages m "
                        + "JOIN chat_conversations c ON c.id = m.conversation_id "
                        + "WHERE c.user_id = ? AND c.session_id = ? AND c.knowledge_base = ? "
                        + "ORDER BY m.created_at DESC, m.id DESC LIMIT ?",
                (rs, rowNum) -> new StoredChatMessage(rs.getString(1), rs.getString(2)),
                user.get().getId(), scopedSessionId, knowledgeBase, Math.max(2, maxMessages));
        List<ChatMessage> result = new ArrayList<>();
        for (int index = reversed.size() - 1; index >= 0; index--) {
            StoredChatMessage message = reversed.get(index);
            if ("USER".equals(message.role())) {
                result.add(UserMessage.from(message.content()));
            } else if ("ASSISTANT".equals(message.role())) {
                result.add(AiMessage.from(message.content()));
            }
        }
        return result;
    }

    private String findOrCreateConversation(Long userId, String scopedSessionId, String knowledgeBase) {
        List<String> found = jdbcTemplate.query(
                "SELECT id FROM chat_conversations WHERE user_id = ? AND session_id = ? AND knowledge_base = ?",
                (rs, rowNum) -> rs.getString(1), userId, scopedSessionId, knowledgeBase);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        String id = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("INSERT INTO chat_conversations (id, user_id, session_id, knowledge_base, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, userId, scopedSessionId, knowledgeBase, "ACTIVE", now, now);
        return id;
    }

    private Optional<UserAccount> userForScopedSession(String scopedSessionId) {
        if (scopedSessionId == null) {
            return Optional.empty();
        }
        int separator = scopedSessionId.indexOf(':');
        String username = separator < 0 ? scopedSessionId : scopedSessionId.substring(0, separator);
        return userAccountRepository.findByUsername(username);
    }

    private record StoredChatMessage(String role, String content) {
    }
}
