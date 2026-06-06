package com.demo.ragchat.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LangChainConfigTest {

    @Test
    void modelTimeoutUsesConfiguredSeconds() {
        LangChainConfig config = new LangChainConfig();
        ReflectionTestUtils.setField(config, "timeoutSeconds", 90L);

        assertThat(config.modelTimeout()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void modelTimeoutHasMinimumValue() {
        LangChainConfig config = new LangChainConfig();
        ReflectionTestUtils.setField(config, "timeoutSeconds", 0L);

        assertThat(config.modelTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
