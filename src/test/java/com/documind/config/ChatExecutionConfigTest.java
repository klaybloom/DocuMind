package com.documind.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class ChatExecutionConfigTest {

    @Test
    void chatStreamExecutorNormalizesInvalidPoolSettings() {
        ChatExecutionConfig config = new ChatExecutionConfig();
        ReflectionTestUtils.setField(config, "corePoolSize", 0);
        ReflectionTestUtils.setField(config, "maxPoolSize", 0);
        ReflectionTestUtils.setField(config, "queueCapacity", -1);

        Executor executor = config.chatStreamExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(1);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(1);
        assertThat(taskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        taskExecutor.shutdown();
    }
}
