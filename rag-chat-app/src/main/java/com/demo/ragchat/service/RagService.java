package com.demo.ragchat.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class RagService {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentService documentService;

    @Value("${app.documents-path}")
    private String documentsPath;

    private Assistant ragAssistant;
    private ContentRetriever contentRetriever;

    public RagService(ChatLanguageModel chatLanguageModel,
                      EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      DocumentService documentService) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentService = documentService;
    }

    interface Assistant {
        @SystemMessage({
            "你是一个专业的税务智能助手 (TaxPulse AI)。",
            "请优先根据提供的文档内容回答问题。",
            "如果你发现文档中确实存在相关信息，请务必详细、准确地进行解读。",
            "如果文档中没有相关信息，请明确告知用户文档中未提及，并结合你自身的税务知识库给出专业建议。"
        })
        String chat(String userMessage);
    }

    @PostConstruct
    public void init() {
        refreshIndex();
    }

    public void refreshIndex() {
        List<String> files = documentService.listFiles();
        for (String filename : files) {
            Path path = Paths.get(documentsPath).resolve(filename);
            Document document;
            if (filename.toLowerCase().endsWith(".pdf")) {
                document = FileSystemDocumentLoader.loadDocument(path, new ApachePdfBoxDocumentParser());
            } else if (filename.toLowerCase().endsWith(".txt")) {
                document = FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());
            } else {
                continue;
            }

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 50))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();

            ingestor.ingest(document);
        }

        // Setup Content Retriever with a similarity threshold
        this.contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.65) // Increased threshold to ensure "hit" quality
                .build();

        // Setup Assistant with Content Retriever and Chat Memory
        this.ragAssistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .contentRetriever(contentRetriever)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    public String ask(String question) {
        if (ragAssistant == null) {
            return "助手尚未初始化，请上传税务文档后再试。";
        }

        // 1. Manually check if we have matching content in our vector store
        List<Content> contents = contentRetriever.retrieve(Query.from(question));

        if (contents == null || contents.isEmpty()) {
            // 2. FALLBACK: No hit in documents, use general LLM knowledge
            // We still use the ragAssistant but the prompt is designed to handle it,
            // or we could use the chatLanguageModel directly. 
            // Better to use a separate prompt for "General Knowledge" fallback.
            return chatLanguageModel.generate(question);
        }

        // 3. HIT: Use RAG assistant which has the context injected
        return ragAssistant.chat(question);
    }
}

