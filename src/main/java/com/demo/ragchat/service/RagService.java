package com.demo.ragchat.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentService documentService;

    @Value("${app.documents-path}")
    private String documentsPath;

    private ContentRetriever contentRetriever;

    // Store Assistant instances per session
    private final Map<String, Assistant> sessionAssistants = new ConcurrentHashMap<>();
    private final Map<String, StreamingAssistant> sessionStreamingAssistants = new ConcurrentHashMap<>();

    public RagService(ChatLanguageModel chatLanguageModel,
                      StreamingChatLanguageModel streamingChatLanguageModel,
                      EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      DocumentService documentService) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentService = documentService;
    }

    interface Assistant {
        @SystemMessage({
            "你是一个专业的智能文档助手 (DocuMind)。",
            "请优先根据提供的文档内容回答问题。",
            "如果你发现文档中确实存在相关信息，请务必详细、准确地进行解读。",
            "如果文档中没有相关信息，请明确告知用户文档中未提及，并结合你自身的知识库给出专业建议。"
        })
        String chat(String userMessage);
    }

    interface StreamingAssistant {
        @SystemMessage({
            "你是一个专业的智能文档助手 (DocuMind)。",
            "请优先根据提供的文档内容回答问题。",
            "如果你发现文档中确实存在相关信息，请务必详细、准确地进行解读。",
            "如果文档中没有相关信息，请明确告知用户文档中未提及，并结合你自身的知识库给出专业建议。"
        })
        TokenStream chat(String userMessage);
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing RAG Service");
        refreshIndex();
    }

    public void refreshIndex() {
        try {
            logger.info("Starting index refresh");
            List<String> files = documentService.listFiles();
            logger.info("Found {} files to index", files.size());

            for (String filename : files) {
                try {
                    Path path = Paths.get(documentsPath).resolve(filename);
                    String lowerName = filename.toLowerCase();
                    Document document;

                    if (lowerName.endsWith(".pdf")) {
                        document = FileSystemDocumentLoader.loadDocument(path, new ApachePdfBoxDocumentParser());
                    } else if (lowerName.endsWith(".txt")) {
                        document = FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());
                    } else if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")
                            || lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx")
                            || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx")) {
                        document = FileSystemDocumentLoader.loadDocument(path, new ApachePoiDocumentParser());
                    } else {
                        logger.warn("Skipping unsupported file type: {}", filename);
                        continue;
                    }

                    EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                            .documentSplitter(DocumentSplitters.recursive(500, 50))
                            .embeddingModel(embeddingModel)
                            .embeddingStore(embeddingStore)
                            .build();

                    ingestor.ingest(document);
                    logger.info("Successfully indexed file: {}", filename);
                } catch (Exception e) {
                    logger.error("Failed to index file: {}", filename, e);
                }
            }

            // Setup Content Retriever with a similarity threshold
            this.contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(3)
                    .minScore(0.65)
                    .build();

            logger.info("Index refresh completed successfully");
        } catch (Exception e) {
            logger.error("Error during index refresh", e);
            throw new RuntimeException("索引刷新失败", e);
        }
    }

    public String ask(String question) {
        return ask(question, null);
    }

    public String ask(String question, String sessionId) {
        try {
            if (contentRetriever == null) {
                logger.warn("Content retriever not initialized");
                return "助手尚未初始化，请上传文档后再试。";
            }

            logger.debug("Processing question for session: {}", sessionId);

            // Manually check if we have matching content in our vector store
            List<Content> contents = contentRetriever.retrieve(Query.from(question));

            if (contents == null || contents.isEmpty()) {
                logger.debug("No relevant content found in documents, using general knowledge");
                return chatLanguageModel.generate(question);
            }

            // Get or create Assistant for this session
            Assistant assistant = getOrCreateAssistant(sessionId);
            String response = assistant.chat(question);
            logger.debug("Generated response for session: {}", sessionId);
            return response;
        } catch (Exception e) {
            logger.error("Error processing question", e);
            throw new RuntimeException("处理问题时发生错误", e);
        }
    }

    public void askStream(String question,
                          String sessionId,
                          Consumer<String> onNext,
                          Runnable onComplete,
                          Consumer<Throwable> onError) {
        try {
            if (contentRetriever == null) {
                logger.warn("Content retriever not initialized");
                onNext.accept("助手尚未初始化，请上传文档后再试。");
                onComplete.run();
                return;
            }

            logger.debug("Processing streaming question for session: {}", sessionId);
            List<Content> contents = contentRetriever.retrieve(Query.from(question));

            if (contents == null || contents.isEmpty()) {
                logger.debug("No relevant content found in documents, streaming general knowledge");
                streamingChatLanguageModel.generate(question, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        onNext.accept(token);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        onComplete.run();
                    }

                    @Override
                    public void onError(Throwable error) {
                        onError.accept(error);
                    }
                });
                return;
            }

            StreamingAssistant assistant = getOrCreateStreamingAssistant(sessionId);
            assistant.chat(question)
                    .onNext(onNext)
                    .onComplete(response -> onComplete.run())
                    .onError(onError)
                    .start();
        } catch (Exception e) {
            logger.error("Error processing streaming question", e);
            onError.accept(e);
        }
    }

    private Assistant getOrCreateAssistant(String sessionId) {
        if (sessionId == null) {
            sessionId = "default";
        }

        return sessionAssistants.computeIfAbsent(sessionId, id -> {
            logger.info("Creating new Assistant for session: {}", id);
            return AiServices.builder(Assistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .contentRetriever(contentRetriever)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();
        });
    }

    private StreamingAssistant getOrCreateStreamingAssistant(String sessionId) {
        if (sessionId == null) {
            sessionId = "default";
        }

        return sessionStreamingAssistants.computeIfAbsent(sessionId, id -> {
            logger.info("Creating new streaming Assistant for session: {}", id);
            return AiServices.builder(StreamingAssistant.class)
                    .streamingChatLanguageModel(streamingChatLanguageModel)
                    .contentRetriever(contentRetriever)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();
        });
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionAssistants.remove(sessionId);
            sessionStreamingAssistants.remove(sessionId);
            logger.info("Cleared session: {}", sessionId);
        }
    }
}
