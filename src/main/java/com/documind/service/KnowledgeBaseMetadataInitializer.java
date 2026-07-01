package com.documind.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class KnowledgeBaseMetadataInitializer implements ApplicationRunner {

    private final KnowledgeBaseManagementService knowledgeBaseManagementService;

    public KnowledgeBaseMetadataInitializer(KnowledgeBaseManagementService knowledgeBaseManagementService) {
        this.knowledgeBaseManagementService = knowledgeBaseManagementService;
    }

    @Override
    public void run(ApplicationArguments args) {
        knowledgeBaseManagementService.syncKnownKnowledgeBases();
    }
}
