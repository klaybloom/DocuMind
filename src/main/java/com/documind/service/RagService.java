package com.documind.service;

import com.documind.dto.RagAnswer;
import com.documind.dto.DocumentFileInfo;
import com.documind.dto.RetrievalDebugInfo;
import com.documind.dto.SourceReference;
import com.documind.exception.InvalidFileException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * RAG 核心服务，负责文档解析、切分、embedding、检索、问答和索引持久化。
 */
@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    private static final ObjectMapper CACHE_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final double DEFAULT_MIN_SCORE = 0.65;
    private static final double DEFAULT_MIN_KEYWORD_HIT_RATIO = 0.25;
    private static final int DEFAULT_RETRIEVAL_POOL_SIZE = 50;
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;
    private static final String CHUNK_ID = "chunk_id";
    private static final String KNOWLEDGE_BASE = "knowledge_base";
    private static final String PAGE = "page";
    private static final String STORE_FILE = ".documind-vectors.json";
    private static final String CACHE_FILE = ".documind-index-cache.json";
    private static final int DEFAULT_MAX_SESSIONS = 1000;
    private static final long DEFAULT_SESSION_TTL_MINUTES = 60;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final EmbeddingModel embeddingModel;
    private final DocumentService documentService;
    private final PromptTemplateService promptTemplateService;

    @Value("${app.documents-path}")
    private String documentsPath;

    @Value("${app.rag.max-results:" + DEFAULT_MAX_RESULTS + "}")
    private int maxResults = DEFAULT_MAX_RESULTS;

    @Value("${app.rag.min-score:" + DEFAULT_MIN_SCORE + "}")
    private double minScore = DEFAULT_MIN_SCORE;

    @Value("${app.rag.keyword-min-hit-ratio:" + DEFAULT_MIN_KEYWORD_HIT_RATIO + "}")
    private double keywordMinHitRatio = DEFAULT_MIN_KEYWORD_HIT_RATIO;

    @Value("${app.rag.retrieval-pool-size:" + DEFAULT_RETRIEVAL_POOL_SIZE + "}")
    private int retrievalPoolSize = DEFAULT_RETRIEVAL_POOL_SIZE;

    @Value("${app.rag.chunk-size:" + DEFAULT_CHUNK_SIZE + "}")
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    @Value("${app.rag.chunk-overlap:" + DEFAULT_CHUNK_OVERLAP + "}")
    private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;

    @Value("${app.chat.session.max:" + DEFAULT_MAX_SESSIONS + "}")
    private int maxSessions = DEFAULT_MAX_SESSIONS;

    @Value("${app.chat.session.ttl-minutes:" + DEFAULT_SESSION_TTL_MINUTES + "}")
    private long sessionTtlMinutes = DEFAULT_SESSION_TTL_MINUTES;

    private EmbeddingStore<TextSegment> embeddingStore;
    private volatile List<TextSegment> indexedSegments = Collections.emptyList();
    private volatile Map<String, IndexedDocument> indexedDocumentCache = Collections.emptyMap();
    private boolean indexReady;
    private boolean storeLoadedFromDisk;

    private Cache<String, MessageWindowChatMemory> sessionMemories = buildSessionCache(
            DEFAULT_MAX_SESSIONS, DEFAULT_SESSION_TTL_MINUTES);

    public RagService(ChatModel chatModel,
                      StreamingChatModel streamingChatModel,
                      EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      DocumentService documentService,
                      PromptTemplateService promptTemplateService) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentService = documentService;
        this.promptTemplateService = promptTemplateService;
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing RAG Service");
        this.sessionMemories = buildSessionCache(
                Math.max(100, maxSessions), Math.max(5, sessionTtlMinutes));
        boolean loaded = tryLoadStoreFromFile();
        if (loaded) {
            logger.info("Loaded persisted vector store, running incremental refresh");
        } else {
            logger.info("No persisted vector store found, running full index build");
        }
        refreshIndex();
    }

    public synchronized void refreshIndex() {
        try {
            logger.info("Starting index refresh");
            this.embeddingStore = new InMemoryEmbeddingStore<>();
            this.storeLoadedFromDisk = false;
            List<TextSegment> refreshedSegments = new ArrayList<>();
            Map<String, IndexedDocument> refreshedCache = new LinkedHashMap<>();
            Map<String, IndexedDocument> previousCache = indexedDocumentCache;

            List<DocumentFileInfo> files = documentService.listDocumentFiles();
            logger.info("Found {} files to index", files.size());

            DocumentSplitter splitter = DocumentSplitters.recursive(validChunkSize(), validChunkOverlap());
            for (DocumentFileInfo fileInfo : files) {
                String documentKey = documentKey(fileInfo);
                String fingerprint = fingerprint(fileInfo);
                IndexedDocument cachedDocument = previousCache.get(documentKey);

                if (hasReusableIndex(cachedDocument)
                        && cachedDocument.fingerprint().equals(fingerprint)
                        && DocumentService.STATUS_INDEXED.equals(fileInfo.getIndexStatus())) {
                    embeddingStore.addAll(cachedDocument.embeddings(), cachedDocument.segments());
                    refreshedSegments.addAll(cachedDocument.segments());
                    refreshedCache.put(documentKey, cachedDocument);
                    logger.info("Reused cached index for file: {}/{}", fileInfo.getKnowledgeBase(), fileInfo.getFileName());
                    continue;
                }

                try {
                    documentService.markIndexing(fileInfo);
                    Document document = loadDocument(fileInfo);
                    document.metadata().put(Document.FILE_NAME, fileInfo.getFileName());
                    document.metadata().put(KNOWLEDGE_BASE, fileInfo.getKnowledgeBase());

                    List<TextSegment> segments = enrichSegments(fileInfo, splitter.split(document));
                    if (segments.isEmpty()) {
                        logger.warn("No text segments generated for file: {}/{}", fileInfo.getKnowledgeBase(), fileInfo.getFileName());
                        documentService.markIndexFailed(fileInfo, "未提取到可索引文本");
                        continue;
                    }

                    List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                    embeddingStore.addAll(embeddings, segments);
                    refreshedSegments.addAll(segments);
                    refreshedCache.put(documentKey, new IndexedDocument(fingerprint, segments, embeddings));
                    documentService.markIndexed(fileInfo, segments.size());
                    logger.info("Successfully indexed file: {}/{}, chunks: {}", fileInfo.getKnowledgeBase(), fileInfo.getFileName(), segments.size());
                } catch (Exception e) {
                    documentService.markIndexFailed(fileInfo, e.getMessage());
                    logger.error("Failed to index file: {}/{}", fileInfo.getKnowledgeBase(), fileInfo.getFileName(), e);
                }
            }

            indexedSegments = Collections.unmodifiableList(refreshedSegments);
            indexedDocumentCache = Collections.unmodifiableMap(refreshedCache);
            indexReady = true;
            saveStoreToFile();
            logger.info("Index refresh completed successfully");
        } catch (Exception e) {
            logger.error("Error during index refresh", e);
            throw new RuntimeException("索引刷新失败", e);
        }
    }

    /**
     * 重新索引单个文档：移除旧片段，重新解析、切分并生成 embedding。
     */
    public synchronized void reindexDocument(String filename, String knowledgeBase) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        String sanitizedFilename = java.nio.file.Paths.get(filename).getFileName().toString();
        String chunkPrefix = kb + "/" + sanitizedFilename + "#";

        // 先确认文件仍存在于目标知识库。
        DocumentFileInfo fileInfo = documentService.listDocumentFiles(kb).stream()
                .filter(f -> sanitizedFilename.equals(f.getFileName()))
                .findFirst()
                .orElseThrow(() -> new InvalidFileException("文件不存在: " + sanitizedFilename));

        documentService.markIndexing(fileInfo);

        try {
            // 重新解析并切分文档。
            DocumentSplitter splitter = DocumentSplitters.recursive(validChunkSize(), validChunkOverlap());
            Document document = loadDocument(fileInfo);
            document.metadata().put(Document.FILE_NAME, fileInfo.getFileName());
            document.metadata().put(KNOWLEDGE_BASE, fileInfo.getKnowledgeBase());

            List<TextSegment> newSegments = enrichSegments(fileInfo, splitter.split(document));
            if (newSegments.isEmpty()) {
                documentService.markIndexFailed(fileInfo, "未提取到可索引文本");
                throw new RuntimeException("未提取到可索引文本");
            }

            List<Embedding> newEmbeddings = embeddingModel.embedAll(newSegments).content();

            // 移除该文件的旧片段，再加入新片段。
            List<TextSegment> remainingSegments = indexedSegments.stream()
                    .filter(s -> !firstNonBlank(s.metadata().getString(CHUNK_ID), "").startsWith(chunkPrefix))
                    .toList();

            List<TextSegment> allSegments = new ArrayList<>(remainingSegments);
            allSegments.addAll(newSegments);

            rebuildStoreFromSegments(allSegments);

            // 更新单文件索引缓存，避免下次全量刷新重复解析。
            String documentKey = documentKey(fileInfo);
            String fingerprint = fingerprint(fileInfo);
            Map<String, IndexedDocument> newCache = new LinkedHashMap<>(indexedDocumentCache);
            newCache.put(documentKey, new IndexedDocument(fingerprint, newSegments, newEmbeddings));
            indexedDocumentCache = Collections.unmodifiableMap(newCache);

            documentService.markIndexed(fileInfo, newSegments.size());
            saveStoreToFile();
            logger.info("Reindexed document: {}/{}, chunks: {}", kb, sanitizedFilename, newSegments.size());
        } catch (Exception e) {
            documentService.markIndexFailed(fileInfo, e.getMessage());
            logger.error("Failed to reindex document: {}/{}", kb, sanitizedFilename, e);
            throw new RuntimeException("重新索引失败: " + sanitizedFilename, e);
        }
    }

    /**
     * 从向量索引中移除某个文档的片段，通常在删除文件后调用。
     */
    public synchronized void removeDocument(String filename, String knowledgeBase) {
        String kb = documentService.normalizeKnowledgeBase(knowledgeBase);
        String sanitizedFilename = java.nio.file.Paths.get(filename).getFileName().toString();
        String chunkPrefix = kb + "/" + sanitizedFilename + "#";

        List<TextSegment> remainingSegments = indexedSegments.stream()
                .filter(s -> !firstNonBlank(s.metadata().getString(CHUNK_ID), "").startsWith(chunkPrefix))
                .toList();

        rebuildStoreFromSegments(remainingSegments);

        // 同步移除单文件索引缓存。
        Map<String, IndexedDocument> newCache = new LinkedHashMap<>(indexedDocumentCache);
        newCache.entrySet().removeIf(e -> e.getKey().equals(kb + "/" + sanitizedFilename));
        indexedDocumentCache = Collections.unmodifiableMap(newCache);

        saveStoreToFile();
        logger.info("Removed document from index: {}/{}, remaining chunks: {}", kb, sanitizedFilename, remainingSegments.size());
    }

    /**
     * 根据给定片段重建内存向量索引。
     */
    private void rebuildStoreFromSegments(List<TextSegment> segments) {
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        if (!segments.isEmpty()) {
            // TextSegment 自身不带 embedding，需要重新生成向量。
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
        }
        indexedSegments = Collections.unmodifiableList(new ArrayList<>(segments));
        indexReady = true;
    }

    public RagAnswer ask(String question) {
        return ask(question, null, DocumentService.DEFAULT_KNOWLEDGE_BASE);
    }

    public RagAnswer ask(String question, String sessionId) {
        return ask(question, sessionId, DocumentService.DEFAULT_KNOWLEDGE_BASE);
    }

    public RagAnswer ask(String question, String sessionId, String knowledgeBase) {
        return ask(question, sessionId, knowledgeBase, false);
    }

    public RagAnswer ask(String question, String sessionId, String knowledgeBase, boolean debug) {
        try {
            if (!indexReady) {
                logger.warn("Index is not initialized");
                return new RagAnswer("助手尚未初始化，请稍后再试。", Collections.emptyList(), false);
            }

            List<String> selectedKnowledgeBases = normalizeKnowledgeBases(knowledgeBase);
            String kb = knowledgeBaseKey(selectedKnowledgeBases);
            RetrievalResult result = retrieveSources(question, selectedKnowledgeBases, kb, debug);
            List<SourceReference> sources = result.sources();
            if (sources.isEmpty()) {
                selectedKnowledgeBases.forEach(selected -> documentService.recordKnowledgeGap(selected, question, sessionId));
            }
            MessageWindowChatMemory memory = getOrCreateMemory(sessionId, kb);
            List<ChatMessage> messages = buildMessages(memory, question, sources);
            String response = chatModel.chat(messages).aiMessage().text();

            memory.add(UserMessage.from(question));
            memory.add(AiMessage.from(response));

            String answer = sources.isEmpty() ? response + formatOwnerSuggestion(selectedKnowledgeBases) : response + formatSources(sources);
            RagAnswer ragAnswer = new RagAnswer(answer, sources, !sources.isEmpty());
            ragAnswer.setDebugInfo(result.debugInfo());
            return ragAnswer;
        } catch (Exception e) {
            logger.error("Error processing question", e);
            throw new RuntimeException("处理问题时发生错误", e);
        }
    }

    public boolean isIndexReady() {
        return indexReady;
    }

    public int indexedSegmentCount() {
        return indexedSegments.size();
    }

    public void askStream(String question,
                          String sessionId,
                          String knowledgeBase,
                          Consumer<String> onNext,
                          Consumer<List<SourceReference>> onSources,
                          Runnable onComplete,
                          Consumer<Throwable> onError) {
        askStream(question, sessionId, knowledgeBase, onNext, onSources, null, onComplete, onError, false);
    }

    public void askStream(String question,
                          String sessionId,
                          String knowledgeBase,
                          Consumer<String> onNext,
                          Consumer<List<SourceReference>> onSources,
                          Consumer<RetrievalDebugInfo> onDebug,
                          Runnable onComplete,
                          Consumer<Throwable> onError,
                          boolean debug) {
        try {
            if (!indexReady) {
                logger.warn("Index is not initialized");
                onNext.accept("助手尚未初始化，请稍后再试。");
                onComplete.run();
                return;
            }

            List<String> selectedKnowledgeBases = normalizeKnowledgeBases(knowledgeBase);
            String kb = knowledgeBaseKey(selectedKnowledgeBases);
            RetrievalResult result = retrieveSources(question, selectedKnowledgeBases, kb, debug);
            List<SourceReference> sources = result.sources();
            if (sources.isEmpty()) {
                selectedKnowledgeBases.forEach(selected -> documentService.recordKnowledgeGap(selected, question, sessionId));
            }
            MessageWindowChatMemory memory = getOrCreateMemory(sessionId, kb);
            List<ChatMessage> messages = buildMessages(memory, question, sources);
            StringBuilder generated = new StringBuilder();

            streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    generated.append(token);
                    onNext.accept(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    if (generated.length() == 0) {
                        handleEmptyStreamingResponse(question, selectedKnowledgeBases, sources, result.debugInfo(),
                                messages, memory, onNext, onSources, onDebug, onComplete, onError);
                        return;
                    }
                    if (!sources.isEmpty()) {
                        onSources.accept(sources);
                    } else {
                        onNext.accept(formatOwnerSuggestion(selectedKnowledgeBases));
                    }
                    if (onDebug != null && result.debugInfo() != null) {
                        onDebug.accept(result.debugInfo());
                    }
                    memory.add(UserMessage.from(question));
                    memory.add(AiMessage.from(generated.toString()));
                    onComplete.run();
                }

                @Override
                public void onError(Throwable error) {
                    handleStreamingError(error, question, selectedKnowledgeBases, sources, result.debugInfo(),
                            messages, memory, generated, onNext, onSources, onDebug, onComplete, onError);
                }
            });
        } catch (Exception e) {
            logger.error("Error processing streaming question", e);
            onError.accept(e);
        }
    }

    private Document loadDocument(DocumentFileInfo fileInfo) {
        Path path = documentService.resolveDocumentPath(fileInfo);
        String filename = fileInfo.getFileName();
        String lowerName = filename.toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".pdf")) {
            return FileSystemDocumentLoader.loadDocument(path, new ApachePdfBoxDocumentParser());
        }
        if (lowerName.endsWith(".txt")) {
            return FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());
        }
        if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")
                || lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx")
                || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx")) {
            return FileSystemDocumentLoader.loadDocument(path, new ApachePoiDocumentParser());
        }

        throw new IllegalArgumentException("不支持的文件类型: " + filename);
    }

    private List<TextSegment> enrichSegments(DocumentFileInfo fileInfo, List<TextSegment> segments) {
        List<TextSegment> enriched = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            Metadata metadata = segment.metadata() == null ? new Metadata() : segment.metadata().copy();
            metadata.put(Document.FILE_NAME, fileInfo.getFileName());
            metadata.put(KNOWLEDGE_BASE, fileInfo.getKnowledgeBase());
            metadata.put(CHUNK_ID, fileInfo.getKnowledgeBase() + "/" + fileInfo.getFileName() + "#" + (i + 1));
            enriched.add(TextSegment.from(segment.text(), metadata));
        }
        return enriched;
    }

    private record RetrievalResult(List<SourceReference> sources, RetrievalDebugInfo debugInfo) {}

    private RetrievalResult retrieveSources(String question, List<String> knowledgeBases, String knowledgeBaseLabel, boolean debug) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(validRetrievalPoolSize())
                        .minScore(validMinScore())
                        .build()
        ).matches();
        Map<String, RetrievedCandidate> candidates = new LinkedHashMap<>();
        Map<String, String> matchTypes = new LinkedHashMap<>();

        // 记录向量检索命中的候选片段。
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            String segmentKnowledgeBase = firstNonBlank(segment.metadata().getString(KNOWLEDGE_BASE), DocumentService.DEFAULT_KNOWLEDGE_BASE);
            if (!matchesKnowledgeBase(knowledgeBases, segmentKnowledgeBase)) {
                continue;
            }

            String chunkId = firstNonBlank(segment.metadata().getString(CHUNK_ID), String.valueOf(segment.hashCode()));
            matchTypes.put(chunkId, "VECTOR");
            addCandidate(candidates, segment, match.score());
        }

        // 记录关键词补充命中的候选片段。
        for (RetrievedCandidate candidate : keywordCandidates(question, knowledgeBases)) {
            String chunkId = firstNonBlank(candidate.segment().metadata().getString(CHUNK_ID), String.valueOf(candidate.segment().hashCode()));
            if (!matchTypes.containsKey(chunkId)) {
                matchTypes.put(chunkId, "KEYWORD");
            } else {
                matchTypes.put(chunkId, "BOTH");
            }
            addCandidate(candidates, candidate.segment(), candidate.score());
        }

        List<RetrievedCandidate> sortedCandidates = candidates.values()
                .stream()
                .sorted(Comparator.comparing(RetrievedCandidate::score).reversed())
                .toList();

        // 标记最终会带入回答的前 N 个片段。
        int maxResults = validMaxResults();
        Set<String> usedChunkIds = new HashSet<>();
        for (int i = 0; i < Math.min(maxResults, sortedCandidates.size()); i++) {
            String chunkId = firstNonBlank(sortedCandidates.get(i).segment().metadata().getString(CHUNK_ID), "unknown");
            usedChunkIds.add(chunkId);
        }

        List<SourceReference> sources = new ArrayList<>();
        List<RetrievalDebugInfo.CandidateDebug> allDebugCandidates = new ArrayList<>();

        for (RetrievedCandidate candidate : sortedCandidates) {
            TextSegment segment = candidate.segment();
            String segmentKnowledgeBase = firstNonBlank(segment.metadata().getString(KNOWLEDGE_BASE), DocumentService.DEFAULT_KNOWLEDGE_BASE);
            String chunkId = firstNonBlank(segment.metadata().getString(CHUNK_ID), "unknown");
            boolean used = usedChunkIds.contains(chunkId);

            if (used) {
                sources.add(new SourceReference(
                        sources.size() + 1,
                        segmentKnowledgeBase,
                        firstNonBlank(segment.metadata().getString(Document.FILE_NAME), "未知文件"),
                        firstNonBlank(segment.metadata().getString(PAGE), segment.metadata().getString("page_number")),
                        chunkId,
                        segment.text(),
                        candidate.score()
                ));
            }

            if (debug) {
                String matchType = matchTypes.getOrDefault(chunkId, "VECTOR");
                allDebugCandidates.add(new RetrievalDebugInfo.CandidateDebug(
                        chunkId,
                        firstNonBlank(segment.metadata().getString(Document.FILE_NAME), "未知文件"),
                        segmentKnowledgeBase,
                        segment.text(),
                        candidate.score(),
                        matchType,
                        used
                ));
            }
        }

        RetrievalDebugInfo debugInfo = null;
        if (debug) {
            debugInfo = new RetrievalDebugInfo(allDebugCandidates, sources.size(), knowledgeBaseLabel);
        }

        return new RetrievalResult(sources, debugInfo);
    }

    private List<ChatMessage> buildMessages(MessageWindowChatMemory memory,
                                            String question,
                                            List<SourceReference> sources) {
        if (sources.isEmpty()) {
            return buildGeneralMessages(memory, question);
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(documentOnlySystemPrompt()));
        messages.addAll(memory.messages());
        messages.add(UserMessage.from(userPrompt(question, sources)));
        return messages;
    }

    private List<ChatMessage> buildGeneralMessages(MessageWindowChatMemory memory, String question) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(generalSystemPrompt()));
        messages.addAll(memory.messages());
        messages.add(UserMessage.from("用户问题：\n" + question));
        return messages;
    }

    private String generalSystemPrompt() {
        return promptTemplateService.getGeneralSystemPrompt();
    }

    private String documentOnlySystemPrompt() {
        return promptTemplateService.getDocumentSystemPrompt();
    }

    private String userPrompt(String question, List<SourceReference> sources) {
        return promptTemplateService.buildUserPrompt(question, sources);
    }

    private String formatSources(List<SourceReference> sources) {
        StringBuilder builder = new StringBuilder("\n\n参考来源：");
        for (SourceReference source : sources) {
            builder.append("\n[")
                    .append(source.getIndex())
                    .append("] ")
                    .append(source.getKnowledgeBase())
                    .append("/")
                    .append(source.getFileName());
            if (!isBlank(source.getPage())) {
                builder.append("，页码：").append(source.getPage());
            }
            builder.append("，片段：")
                    .append(source.getChunkId())
                    .append("，相似度：")
                    .append(String.format(Locale.ROOT, "%.3f", source.getScore()))
                    .append("\n摘录：")
                    .append(abbreviate(source.getText(), 180));
        }
        return builder.toString();
    }

    private String formatOwnerSuggestion(List<String> knowledgeBases) {
        List<String> owners = knowledgeBases.stream()
                .flatMap(knowledgeBase -> documentService.suggestOwners(knowledgeBase).stream())
                .distinct()
                .toList();
        if (owners.isEmpty()) {
            return "";
        }
        return "\n\n建议联系知识库负责人补充资料：" + String.join("、", owners);
    }

    private void handleEmptyStreamingResponse(String question,
                                              List<String> knowledgeBases,
                                              List<SourceReference> sources,
                                              RetrievalDebugInfo debugInfo,
                                              List<ChatMessage> messages,
                                              MessageWindowChatMemory memory,
                                              Consumer<String> onNext,
                                              Consumer<List<SourceReference>> onSources,
                                              Consumer<RetrievalDebugInfo> onDebug,
                                              Runnable onComplete,
                                              Consumer<Throwable> onError) {
        try {
            logger.warn("Streaming model completed without tokens; using non-streaming response, sourceCount={}", sources.size());
            String response = chatModel.chat(messages).aiMessage().text();
            if (!sources.isEmpty()) {
                onSources.accept(sources);
            }
            if (onDebug != null && debugInfo != null) {
                onDebug.accept(debugInfo);
            }
            String answer = sources.isEmpty() ? response + formatOwnerSuggestion(knowledgeBases) : response;
            onNext.accept(answer);
            memory.add(UserMessage.from(question));
            memory.add(AiMessage.from(response));
            onComplete.run();
        } catch (Exception fallbackError) {
            logger.error("Non-streaming response failed after empty stream", fallbackError);
            onError.accept(fallbackError);
        }
    }

    private void handleStreamingError(Throwable error,
                                      String question,
                                      List<String> knowledgeBases,
                                      List<SourceReference> sources,
                                      RetrievalDebugInfo debugInfo,
                                      List<ChatMessage> messages,
                                      MessageWindowChatMemory memory,
                                      StringBuilder generated,
                                      Consumer<String> onNext,
                                      Consumer<List<SourceReference>> onSources,
                                      Consumer<RetrievalDebugInfo> onDebug,
                                      Runnable onComplete,
                                      Consumer<Throwable> onError) {
        if (generated.length() > 0) {
            if (!sources.isEmpty()) {
                onSources.accept(sources);
            }
            if (onDebug != null && debugInfo != null) {
                onDebug.accept(debugInfo);
            }
            onError.accept(error);
            return;
        }

        try {
            logger.warn("Streaming model failed before returning tokens; using non-streaming response, sourceCount={}, error={}",
                    sources.size(),
                    error.toString());
            String response = chatModel.chat(messages).aiMessage().text();
            if (!sources.isEmpty()) {
                onSources.accept(sources);
            }
            if (onDebug != null && debugInfo != null) {
                onDebug.accept(debugInfo);
            }
            String answer = sources.isEmpty() ? response + formatOwnerSuggestion(knowledgeBases) : response;
            onNext.accept(answer);
            memory.add(UserMessage.from(question));
            memory.add(AiMessage.from(response));
            onComplete.run();
        } catch (Exception fallbackError) {
            logger.error("Non-streaming fallback also failed", fallbackError);
            onError.accept(error);
        }
    }

    private MessageWindowChatMemory getOrCreateMemory(String sessionId, String knowledgeBase) {
        String id = isBlank(sessionId) ? "default" : sessionId;
        return sessionMemories.get(knowledgeBase + ":" + id,
                ignored -> MessageWindowChatMemory.withMaxMessages(10));
    }

    public int clearSessionMemory(String sessionId) {
        if (isBlank(sessionId)) {
            return 0;
        }
        String suffix = ":" + sessionId.trim();
        List<String> toRemove = sessionMemories.asMap().keySet().stream()
                .filter(key -> key.endsWith(suffix))
                .toList();
        toRemove.forEach(sessionMemories::invalidate);
        return toRemove.size();
    }

    int sessionMemoryCount() {
        return (int) sessionMemories.estimatedSize();
    }

    private Cache<String, MessageWindowChatMemory> buildSessionCache(int maxSize, long ttlMinutes) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(ttlMinutes, TimeUnit.MINUTES)
                .build();
    }

    private String documentKey(DocumentFileInfo fileInfo) {
        return fileInfo.getKnowledgeBase() + "/" + fileInfo.getFileName();
    }

    private String fingerprint(DocumentFileInfo fileInfo) {
        return fileInfo.getSizeBytes() + ":" + firstNonBlank(fileInfo.getUploadedAt(), "");
    }

    private List<RetrievedCandidate> keywordCandidates(String question, List<String> knowledgeBases) {
        Set<String> queryTerms = keywordTerms(question);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        List<RetrievedCandidate> candidates = new ArrayList<>();
        for (TextSegment segment : indexedSegments) {
            String segmentKnowledgeBase = firstNonBlank(segment.metadata().getString(KNOWLEDGE_BASE), DocumentService.DEFAULT_KNOWLEDGE_BASE);
            if (!matchesKnowledgeBase(knowledgeBases, segmentKnowledgeBase)) {
                continue;
            }

            Set<String> segmentTerms = keywordTerms(segment.text());
            int hits = 0;
            for (String term : queryTerms) {
                if (segmentTerms.contains(term)) {
                    hits++;
                }
            }
            double hitRatio = hits / (double) queryTerms.size();
            if (isStrongKeywordMatch(queryTerms.size(), hits, hitRatio)) {
                double score = Math.min(0.95, 0.55 + hitRatio * 0.40);
                candidates.add(new RetrievedCandidate(segment, score));
            }
        }
        return candidates;
    }

    private boolean isStrongKeywordMatch(int queryTermCount, int hits, double hitRatio) {
        if (hits <= 0 || hitRatio < validKeywordMinHitRatio()) {
            return false;
        }
        return queryTermCount <= 2 || hits >= 2;
    }

    private int validMaxResults() {
        return Math.max(1, maxResults);
    }

    private double validMinScore() {
        return clamp(minScore, 0.0, 1.0);
    }

    private double validKeywordMinHitRatio() {
        return clamp(keywordMinHitRatio, 0.0, 1.0);
    }

    private int validRetrievalPoolSize() {
        return Math.max(validMaxResults(), retrievalPoolSize);
    }

    private int validChunkSize() {
        return Math.max(100, chunkSize);
    }

    private int validChunkOverlap() {
        return Math.max(0, Math.min(chunkOverlap, validChunkSize() - 1));
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private Set<String> keywordTerms(String text) {
        if (isBlank(text)) {
            return Collections.emptySet();
        }

        Set<String> terms = new HashSet<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        String[] words = normalized.split("[^\\p{L}\\p{N}]+");
        for (String word : words) {
            if (word.length() >= 2) {
                terms.add(word);
                addCjkBigrams(terms, word);
            }
        }
        return terms;
    }

    private void addCjkBigrams(Set<String> terms, String text) {
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                cjk.append(ch);
            } else {
                addBigrams(terms, cjk.toString());
                cjk.setLength(0);
            }
        }
        addBigrams(terms, cjk.toString());
    }

    private void addBigrams(Set<String> terms, String text) {
        if (text.length() < 2) {
            return;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            terms.add(text.substring(i, i + 2));
        }
    }

    private void addCandidate(Map<String, RetrievedCandidate> candidates, TextSegment segment, double score) {
        String chunkId = firstNonBlank(segment.metadata().getString(CHUNK_ID), String.valueOf(segment.hashCode()));
        RetrievedCandidate existing = candidates.get(chunkId);
        if (existing == null || score > existing.score()) {
            candidates.put(chunkId, new RetrievedCandidate(segment, score));
        }
    }

    private boolean matchesKnowledgeBase(List<String> requested, String actual) {
        return requested.contains(actual);
    }

    private List<String> normalizeKnowledgeBases(String knowledgeBaseSelection) {
        if (isBlank(knowledgeBaseSelection)) {
            return List.of(DocumentService.DEFAULT_KNOWLEDGE_BASE);
        }
        List<String> normalized = Arrays.stream(knowledgeBaseSelection.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(documentService::normalizeKnowledgeBase)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of(DocumentService.DEFAULT_KNOWLEDGE_BASE) : normalized;
    }

    private String knowledgeBaseKey(List<String> knowledgeBases) {
        return String.join(",", knowledgeBases);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean tryLoadStoreFromFile() {
        try {
            Path storePath = storeFilePath();
            if (!Files.exists(storePath)) {
                return false;
            }
            long sizeBytes = Files.size(storePath);
            logger.info("Loading persisted vector store from {}, size={} bytes", storePath, sizeBytes);
            InMemoryEmbeddingStore<TextSegment> loaded = InMemoryEmbeddingStore.fromFile(storePath);
            this.embeddingStore = loaded;
            this.storeLoadedFromDisk = true;
            // 文件加载成功即可先标记可用，随后 refreshIndex() 会做增量更新。
            this.indexReady = true;
            logger.info("Persisted vector store loaded successfully");
            // 同时加载文档 fingerprint 缓存，用于判断索引是否可复用。
            loadCacheFromFile();
            return true;
        } catch (Exception e) {
            logger.warn("Failed to load persisted vector store, will rebuild from documents: {}", e.getMessage());
            this.storeLoadedFromDisk = false;
            return false;
        }
    }

    private boolean hasReusableIndex(IndexedDocument document) {
        return document != null
                && document.segments() != null
                && document.embeddings() != null
                && !document.segments().isEmpty()
                && document.segments().size() == document.embeddings().size();
    }

    @SuppressWarnings("unchecked")
    private void loadCacheFromFile() {
        try {
            Path cachePath = cacheFilePath();
            if (!Files.exists(cachePath)) {
                logger.info("No index cache file found, will rebuild fingerprints");
                return;
            }
            Map<String, CacheEntry> raw = CACHE_MAPPER.readValue(
                    cachePath.toFile(),
                    new TypeReference<Map<String, CacheEntry>>() {}
            );
            Map<String, IndexedDocument> restored = new LinkedHashMap<>();
            for (Map.Entry<String, CacheEntry> entry : raw.entrySet()) {
                CacheEntry ce = entry.getValue();
                restored.put(entry.getKey(), new IndexedDocument(ce.fingerprint, List.of(), List.of()));
            }
            this.indexedDocumentCache = Collections.unmodifiableMap(restored);
            logger.info("Index cache loaded: {} document entries", restored.size());
        } catch (Exception e) {
            logger.warn("Failed to load index cache, will rebuild fingerprints: {}", e.getMessage());
        }
    }

    private void saveStoreToFile() {
        try {
            Path storePath = storeFilePath();
            Files.createDirectories(storePath.getParent());
            // 先写临时文件再原子替换，降低写入中断造成文件损坏的概率。
            Path tempPath = storePath.resolveSibling(STORE_FILE + ".tmp");
            if (embeddingStore instanceof InMemoryEmbeddingStore<TextSegment> inMemory) {
                inMemory.serializeToFile(tempPath);
                Files.move(tempPath, storePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                logger.info("Vector store persisted to {}, size={} bytes", storePath, Files.size(storePath));
            }
            saveCacheToFile();
        } catch (Exception e) {
            logger.warn("Failed to persist vector store: {}", e.getMessage());
        }
    }

    private void saveCacheToFile() {
        try {
            Path cachePath = cacheFilePath();
            Map<String, CacheEntry> serializable = new LinkedHashMap<>();
            for (Map.Entry<String, IndexedDocument> entry : indexedDocumentCache.entrySet()) {
                serializable.put(entry.getKey(), new CacheEntry(entry.getValue().fingerprint()));
            }
            CACHE_MAPPER.writerWithDefaultPrettyPrinter().writeValue(cachePath.toFile(), serializable);
            logger.debug("Index cache persisted: {} entries", serializable.size());
        } catch (Exception e) {
            logger.warn("Failed to persist index cache: {}", e.getMessage());
        }
    }

    private Path storeFilePath() {
        return Paths.get(documentsPath).resolve(STORE_FILE);
    }

    private Path cacheFilePath() {
        return Paths.get(documentsPath).resolve(CACHE_FILE);
    }

    private record CacheEntry(String fingerprint) {}

    private record RetrievedCandidate(TextSegment segment, double score) {
    }

    private record IndexedDocument(String fingerprint, List<TextSegment> segments, List<Embedding> embeddings) {
    }
}
