package com.documind.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

class QdrantEmbeddingStoreIntegrationTest {

    private static final String COLLECTION = "documind-qdrant-integration-test";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private QdrantClient client;
    private QdrantEmbeddingStore store;

    @BeforeEach
    void createCollection() throws Exception {
        client = new QdrantClient(QdrantGrpcClient.newBuilder("127.0.0.1", 6334, false)
                .withTimeout(TIMEOUT)
                .build());
        client.healthCheckAsync(TIMEOUT).get(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (client.collectionExistsAsync(COLLECTION, TIMEOUT).get(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            client.deleteCollectionAsync(COLLECTION, TIMEOUT).get(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        client.createCollectionAsync(COLLECTION,
                        VectorParams.newBuilder().setSize(384).setDistance(Distance.Cosine).build(),
                        TIMEOUT)
                .get(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        var vectorParams = client.getCollectionInfoAsync(COLLECTION, TIMEOUT)
                .get(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .getConfig().getParams().getVectorsConfig().getParams();
        assertThat(vectorParams.getSize()).isEqualTo(384);
        assertThat(vectorParams.getDistance()).isEqualTo(Distance.Cosine);
        store = new QdrantEmbeddingStore(client, COLLECTION, "text");
    }

    @AfterEach
    void deleteCollection() throws Exception {
        if (client != null) {
            if (client.collectionExistsAsync(COLLECTION, TIMEOUT).get(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                client.deleteCollectionAsync(COLLECTION, TIMEOUT).get(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            client.close();
        }
    }

    @Test
    void storesSearchesAndRemovesPointsByDocumentKey() {
        store.addAll(
                List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                List.of(Embedding.from(vector()), Embedding.from(vector())),
                List.of(segment("HR/handbook.txt#1", "HR/handbook.txt", "考勤制度要求九点前到岗。"),
                        segment("Finance/expense.txt#1", "Finance/expense.txt", "报销制度要求提交发票。")));

        assertThat(store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(vector()))
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .matches()).hasSize(2);

        store.removeAll(metadataKey("document_key").isEqualTo("HR/handbook.txt"));

        assertThat(store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(vector()))
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .matches())
                .extracting(match -> match.embedded().metadata().getString("document_key"))
                .containsExactly("Finance/expense.txt");
    }

    private TextSegment segment(String chunkId, String documentKey, String text) {
        Metadata metadata = new Metadata();
        metadata.put("chunk_id", chunkId);
        metadata.put("document_key", documentKey);
        return TextSegment.from(text, metadata);
    }

    private float[] vector() {
        float[] vector = new float[384];
        vector[0] = 1.0f;
        return vector;
    }
}
