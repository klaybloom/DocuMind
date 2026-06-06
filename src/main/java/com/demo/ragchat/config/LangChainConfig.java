package com.demo.ragchat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainConfig {

    @Value("${app.deepseek.api-key}")
    private String apiKey;

    @Value("${app.deepseek.base-url}")
    private String baseUrl;

    @Value("${app.deepseek.model}")
    private String modelName;

    @Value("${app.deepseek.timeout-seconds:60}")
    private long timeoutSeconds;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(requireConfigured(apiKey, "DEEPSEEK_API_KEY"))
                .baseUrl(requireConfigured(baseUrl, "DEEPSEEK_BASE_URL"))
                .modelName(requireConfigured(modelName, "DEEPSEEK_MODEL"))
                .timeout(modelTimeout())
                .logRequests(false)
                .logResponses(false)
                .tokenizer(new OpenAiTokenizer("gpt-3.5-turbo"))
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(requireConfigured(apiKey, "DEEPSEEK_API_KEY"))
                .baseUrl(requireConfigured(baseUrl, "DEEPSEEK_BASE_URL"))
                .modelName(requireConfigured(modelName, "DEEPSEEK_MODEL"))
                .timeout(modelTimeout())
                .logRequests(false)
                .logResponses(false)
                .tokenizer(new OpenAiTokenizer("gpt-3.5-turbo"))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        // 使用本地嵌入模型以提高效率和降低成本
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
    }

    Duration modelTimeout() {
        return Duration.ofSeconds(Math.max(5, timeoutSeconds));
    }

    private String requireConfigured(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("必须配置 " + name);
        }
        return value;
    }
}
