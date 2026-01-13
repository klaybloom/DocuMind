package com.demo.ragchat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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

    @Value("${app.chroma.url}")
    private String chromaUrl;

    @Value("${app.chroma.collection-name}")
    private String collectionName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        // 使用本地嵌入模型以提高效率和降低成本
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // 由于 ChromaDB Docker 兼容性问题（Numpy 2.0 / API 405），回退到内存存储。
        // 这确保了演示可以开箱即用。
        return new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
    }
}
