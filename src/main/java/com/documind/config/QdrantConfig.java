package com.documind.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Qdrant 连接、健康检查和 collection 初始化。
 */
@Configuration
public class QdrantConfig {

    @Value("${app.vector-store.qdrant.host}")
    private String host;

    @Value("${app.vector-store.qdrant.grpc-port}")
    private int grpcPort;

    @Value("${app.vector-store.qdrant.health-url}")
    private String healthUrl;

    @Value("${app.vector-store.qdrant.collection}")
    private String collection;

    @Value("${app.vector-store.qdrant.api-key:}")
    private String apiKey;

    @Value("${app.vector-store.qdrant.use-tls:false}")
    private boolean useTls;

    @Value("${app.vector-store.qdrant.timeout-seconds:5}")
    private long timeoutSeconds;

    @Value("${app.vector-store.qdrant.dimension:384}")
    private int configuredDimension;

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient() {
        Duration timeout = timeout();
        verifyHttpHealth(timeout);

        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, grpcPort, useTls)
                .withTimeout(timeout);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.withApiKey(apiKey);
        }

        QdrantClient client = new QdrantClient(builder.build());
        try {
            client.healthCheckAsync(timeout).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return client;
        } catch (Exception e) {
            client.close();
            throw new IllegalStateException("Qdrant gRPC 不可用: " + host + ":" + grpcPort, e);
        }
    }

    @Bean
    public QdrantCollectionState qdrantCollectionState(QdrantClient client, EmbeddingModel embeddingModel) {
        Duration timeout = timeout();
        try {
            boolean exists = client.collectionExistsAsync(collection, timeout)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exists) {
                client.createCollectionAsync(
                                collection,
                                VectorParams.newBuilder()
                                        .setSize(vectorDimension(embeddingModel))
                                        .setDistance(Distance.Cosine)
                                        .build(),
                                timeout)
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                verifyCollectionConfiguration(client, vectorDimension(embeddingModel), timeout);
            }
            return new QdrantCollectionState(collection, !exists);
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化 Qdrant collection: " + collection, e);
        }
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(QdrantClient client, QdrantCollectionState collectionState) {
        return new QdrantEmbeddingStore(client, collectionState.collection(), "text");
    }

    private void verifyHttpHealth(Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP status=" + response.statusCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Qdrant HTTP 健康检查失败: " + healthUrl, e);
        }
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    private int vectorDimension(EmbeddingModel embeddingModel) {
        int modelDimension = embeddingModel.dimension();
        return modelDimension > 0 ? modelDimension : Math.max(1, configuredDimension);
    }

    private void verifyCollectionConfiguration(QdrantClient client, int expectedDimension, Duration timeout) throws Exception {
        CollectionInfo info = client.getCollectionInfoAsync(collection, timeout)
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!info.hasConfig()
                || !info.getConfig().hasParams()
                || !info.getConfig().getParams().hasVectorsConfig()
                || !info.getConfig().getParams().getVectorsConfig().hasParams()) {
            throw new IllegalStateException("Qdrant collection 未使用单向量配置: " + collection);
        }

        VectorParams vectorParams = info.getConfig().getParams().getVectorsConfig().getParams();
        if (vectorParams.getSize() != expectedDimension || vectorParams.getDistance() != Distance.Cosine) {
            throw new IllegalStateException("Qdrant collection 配置不匹配: " + collection
                    + "，期望维度=" + expectedDimension + "、距离=COSINE"
                    + "，实际维度=" + vectorParams.getSize() + "、距离=" + vectorParams.getDistance());
        }
    }

    public record QdrantCollectionState(String collection, boolean created) {
    }
}
