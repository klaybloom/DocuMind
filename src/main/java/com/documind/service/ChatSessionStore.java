package com.documind.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

/**
 * 活跃会话的热存储。Redis 不保存事实数据，失效后由 ChatHistoryService 从 MySQL 恢复。
 */
public interface ChatSessionStore {

    MessageWindowChatMemory load(String knowledgeBase, String sessionId);

    void save(String knowledgeBase, String sessionId, MessageWindowChatMemory memory);

    void replace(String knowledgeBase, String sessionId, List<ChatMessage> messages);

    int clear(String sessionId);

    default int count() {
        return -1;
    }
}
