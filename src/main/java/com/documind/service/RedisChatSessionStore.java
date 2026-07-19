package com.documind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Redis 中仅保存最近消息窗口；key 的 TTL 过期不会影响 MySQL 中的完整会话记录。
 */
@Service
public class RedisChatSessionStore implements ChatSessionStore {

    private static final String KEY_PREFIX = "documind:chat:memory:";
    private static final String SESSION_INDEX_PREFIX = "documind:chat:session-keys:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.chat.session.ttl-minutes:30}")
    private long ttlMinutes;

    @Value("${app.chat.session.max-messages:10}")
    private int maxMessages;

    public RedisChatSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public MessageWindowChatMemory load(String knowledgeBase, String sessionId) {
        String payload = redisTemplate.opsForValue().get(memoryKey(knowledgeBase, sessionId));
        MessageWindowChatMemory memory = newMemory();
        if (payload == null || payload.isBlank()) {
            return memory;
        }
        try {
            memory.set(toMessages(objectMapper.readValue(payload, new TypeReference<List<StoredMessage>>() { })));
            return memory;
        } catch (Exception ex) {
            // 缓存内容异常只删除热数据，后续会从 MySQL 重建会话窗口。
            redisTemplate.delete(memoryKey(knowledgeBase, sessionId));
            return memory;
        }
    }

    @Override
    public void save(String knowledgeBase, String sessionId, MessageWindowChatMemory memory) {
        try {
            String key = memoryKey(knowledgeBase, sessionId);
            Duration ttl = ttl();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(toStored(memory.messages())), ttl);
            String indexKey = sessionIndexKey(sessionId);
            redisTemplate.opsForSet().add(indexKey, key);
            redisTemplate.expire(indexKey, ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("Redis 会话缓存不可用", ex);
        }
    }

    @Override
    public void replace(String knowledgeBase, String sessionId, List<ChatMessage> messages) {
        MessageWindowChatMemory memory = newMemory();
        memory.set(messages);
        save(knowledgeBase, sessionId, memory);
    }

    @Override
    public int clear(String sessionId) {
        String indexKey = sessionIndexKey(sessionId);
        var keys = redisTemplate.opsForSet().members(indexKey);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        redisTemplate.delete(keys);
        redisTemplate.delete(indexKey);
        return keys.size();
    }

    private MessageWindowChatMemory newMemory() {
        return MessageWindowChatMemory.withMaxMessages(Math.max(2, maxMessages));
    }

    private List<StoredMessage> toStored(List<ChatMessage> messages) {
        List<StoredMessage> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage user) {
                result.add(new StoredMessage("USER", user.singleText()));
            } else if (message instanceof AiMessage ai) {
                result.add(new StoredMessage("ASSISTANT", ai.text()));
            } else if (message instanceof SystemMessage system) {
                result.add(new StoredMessage("SYSTEM", system.text()));
            }
        }
        return result;
    }

    private List<ChatMessage> toMessages(List<StoredMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        for (StoredMessage message : messages) {
            if ("USER".equals(message.role())) {
                result.add(UserMessage.from(message.content()));
            } else if ("ASSISTANT".equals(message.role())) {
                result.add(AiMessage.from(message.content()));
            } else if ("SYSTEM".equals(message.role())) {
                result.add(SystemMessage.from(message.content()));
            }
        }
        return result;
    }

    private Duration ttl() {
        return Duration.ofMinutes(Math.max(1, ttlMinutes));
    }

    private String memoryKey(String knowledgeBase, String sessionId) {
        return KEY_PREFIX + keyPart(knowledgeBase) + ":" + keyPart(sessionId);
    }

    private String sessionIndexKey(String sessionId) {
        return SESSION_INDEX_PREFIX + keyPart(sessionId);
    }

    private String keyPart(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private record StoredMessage(String role, String content) {
    }
}
