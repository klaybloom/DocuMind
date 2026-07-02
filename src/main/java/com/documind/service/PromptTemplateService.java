package com.documind.service;

import com.documind.dto.SourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Prompt 模板服务，集中生成文档问答和通用回答提示词。
 */
@Service
public class PromptTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateService.class);

    @Value("${app.rag.prompts.system-document:classpath:prompts/system-document.md}")
    private String systemDocumentPath;

    @Value("${app.rag.prompts.system-general:classpath:prompts/system-general.md}")
    private String systemGeneralPath;

    @Value("${app.rag.prompts.user-with-sources:classpath:prompts/user-with-sources.md}")
    private String userWithSourcesPath;

    private String systemDocumentPrompt;
    private String systemGeneralPrompt;
    private String userWithSourcesTemplate;

    @jakarta.annotation.PostConstruct
    public void init() {
        systemDocumentPrompt = loadTemplate(systemDocumentPath, "system-document");
        systemGeneralPrompt = loadTemplate(systemGeneralPath, "system-general");
        userWithSourcesTemplate = loadTemplate(userWithSourcesPath, "user-with-sources");
        logger.info("Prompt templates loaded successfully");
    }

    public String getDocumentSystemPrompt() {
        return systemDocumentPrompt;
    }

    public String getGeneralSystemPrompt() {
        return systemGeneralPrompt;
    }

    public String buildUserPrompt(String question, List<SourceReference> sources) {
        if (sources.isEmpty()) {
            return "用户问题：\n" + question;
        }

        StringBuilder sourcesBuilder = new StringBuilder();
        for (SourceReference source : sources) {
            sourcesBuilder.append("[")
                    .append(source.getIndex())
                    .append("] 知识库：")
                    .append(source.getKnowledgeBase())
                    .append("，文件：")
                    .append(source.getFileName());
            if (source.getPage() != null && !source.getPage().trim().isEmpty()) {
                sourcesBuilder.append("，页码：").append(source.getPage());
            }
            sourcesBuilder.append("，片段：")
                    .append(source.getChunkId())
                    .append("\n")
                    .append(source.getText())
                    .append("\n\n");
        }

        return userWithSourcesTemplate
                .replace("{{question}}", question)
                .replace("{{sources}}", sourcesBuilder.toString().trim());
    }

    private String loadTemplate(String path, String name) {
        try {
            String resourcePath = path;
            if (resourcePath.startsWith("classpath:")) {
                resourcePath = resourcePath.substring("classpath:".length());
            }
            InputStream stream = new ClassPathResource(resourcePath).getInputStream();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            logger.debug("Loaded prompt template '{}': {} chars", name, content.length());
            return content;
        } catch (IOException e) {
            logger.error("Failed to load prompt template: {}", name, e);
            throw new RuntimeException("无法加载提示词模板: " + name, e);
        }
    }
}
