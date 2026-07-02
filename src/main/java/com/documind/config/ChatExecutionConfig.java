package com.documind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 问答流式响应的线程池配置，隔离 SSE 输出与普通 Web 请求。
 */
@Configuration
public class ChatExecutionConfig {

    @Value("${app.chat.stream.core-pool-size:4}")
    private int corePoolSize;

    @Value("${app.chat.stream.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${app.chat.stream.queue-capacity:100}")
    private int queueCapacity;

    @Bean("chatStreamExecutor")
    public Executor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizedCorePoolSize());
        executor.setMaxPoolSize(normalizedMaxPoolSize());
        executor.setQueueCapacity(normalizedQueueCapacity());
        executor.setThreadNamePrefix("documind-chat-stream-");
        executor.initialize();
        return executor;
    }

    private int normalizedCorePoolSize() {
        return Math.max(1, corePoolSize);
    }

    private int normalizedMaxPoolSize() {
        return Math.max(normalizedCorePoolSize(), maxPoolSize);
    }

    private int normalizedQueueCapacity() {
        return Math.max(0, queueCapacity);
    }
}
