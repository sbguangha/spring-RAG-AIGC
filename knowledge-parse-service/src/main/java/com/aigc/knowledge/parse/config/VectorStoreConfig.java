package com.aigc.knowledge.parse.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

/**
 * 向量存储配置类。
 * <p>
 * 手动声明 MilvusVectorStore 与 ElasticsearchVectorStore Bean，
 * 避免 Spring AI starter 自动配置与项目自定义参数不匹配的问题。
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    /**
     * Milvus 服务客户端。
     */
    @Bean
    public MilvusServiceClient milvusServiceClient(
            @Value("${vector.milvus.host:127.0.0.1}") String host,
            @Value("${vector.milvus.port:19530}") int port) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        return new MilvusServiceClient(connectParam);
    }

    /**
     * Milvus 向量存储。
     */
    @Bean
    @Primary
    public VectorStore milvusVectorStore(MilvusServiceClient milvusServiceClient,
                                          EmbeddingModel embeddingModel,
                                          @Value("${vector.milvus.database-name:aigc_knowledge}") String databaseName,
                                          @Value("${vector.milvus.collection-name:knowledge_chunks}") String collectionName,
                                          @Value("${vector.milvus.dimension:1536}") int dimension) {
        log.info("初始化 MilvusVectorStore, database={}, collection={}, dimension={}", databaseName, collectionName, dimension);
        return MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .embeddingDimension(dimension)
                .initializeSchema(true)
                .build();
    }

    /**
     * Elasticsearch 低级 REST 客户端。
     */
    @Bean
    public RestClient elasticsearchRestClient(
            @Value("${vector.elasticsearch.hosts:http://127.0.0.1:9200}") String hosts) {
        String[] hostArray = hosts.split(",");
        RestClientBuilder builder = RestClient.builder(
                java.util.Arrays.stream(hostArray)
                        .map(this::parseHttpHost)
                        .toArray(org.apache.http.HttpHost[]::new)
        );
        return builder.build();
    }

    /**
     * Elasticsearch 向量存储。
     */
    @Bean
    public VectorStore elasticsearchVectorStore(RestClient restClient,
                                                 EmbeddingModel embeddingModel,
                                                 @Value("${vector.elasticsearch.index-prefix:aigc}") String indexPrefix,
                                                 @Value("${vector.elasticsearch.similarity:cosine}") String similarity) {
        String indexName = indexPrefix + "_knowledge_chunks";
        log.info("初始化 ElasticsearchVectorStore, index={}", indexName);

        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(indexName);
        options.setSimilarity(toSimilarityFunction(similarity));

        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .build();
    }

    private org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction toSimilarityFunction(String similarity) {
        return java.util.Arrays.stream(org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction.values())
                .filter(fn -> fn.name().equalsIgnoreCase(similarity))
                .findFirst()
                .orElse(org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction.cosine);
    }

    private org.apache.http.HttpHost parseHttpHost(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        java.net.URI uri = java.net.URI.create(trimmed);
        String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
        String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
        int port = uri.getPort() != -1 ? uri.getPort() : 9200;
        return new org.apache.http.HttpHost(host, port, scheme);
    }
}
