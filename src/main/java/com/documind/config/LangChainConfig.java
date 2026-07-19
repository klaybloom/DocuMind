package com.documind.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 相关 Bean 配置，包含 DeepSeek 聊天模型和本地 embedding 模型。
 */
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
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(requireConfigured(apiKey, "DEEPSEEK_API_KEY"))
                .baseUrl(requireConfigured(baseUrl, "DEEPSEEK_BASE_URL"))
                .modelName(requireConfigured(modelName, "DEEPSEEK_MODEL"))
                .timeout(modelTimeout())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(requireConfigured(apiKey, "DEEPSEEK_API_KEY"))
                .baseUrl(requireConfigured(baseUrl, "DEEPSEEK_BASE_URL"))
                .modelName(requireConfigured(modelName, "DEEPSEEK_MODEL"))
                .timeout(modelTimeout())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        // 使用本地嵌入模型以提高效率和降低成本
        return new AllMiniLmL6V2EmbeddingModel();
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
