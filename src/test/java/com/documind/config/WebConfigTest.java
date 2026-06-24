package com.documind.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    void parseAllowedOriginsTrimsWhitespaceAndSkipsEmptyValues() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(
                config,
                "allowedOrigins",
                "https://kb.example.com, https://admin.example.com, ,http://localhost:8080"
        );

        assertThat(config.parseAllowedOrigins())
                .containsExactly(
                        "https://kb.example.com",
                        "https://admin.example.com",
                        "http://localhost:8080"
                );
    }
}
