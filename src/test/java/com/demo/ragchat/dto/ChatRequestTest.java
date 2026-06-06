package com.demo.ragchat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void allowsFrontendSessionIdAndChineseKnowledgeBaseName() {
        ChatRequest request = new ChatRequest("查询报销制度");
        request.setSessionId("conv-1770000000000");
        request.setKnowledgeBase("人事_制度-2026");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsInvalidSessionIdAndKnowledgeBaseCharacters() {
        ChatRequest request = new ChatRequest("查询报销制度");
        request.setSessionId("../session id");
        request.setKnowledgeBase("../HR");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sessionId", "knowledgeBase");
    }

    @Test
    void rejectsOverlongMessage() {
        ChatRequest request = new ChatRequest("x".repeat(5001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("message");
    }
}
