package com.aigc.orchestration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SemanticDeduplicator 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SemanticDeduplicatorTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private SemanticDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new SemanticDeduplicator(embeddingModel);
    }

    @Test
    @DisplayName("空列表或单文档应原样返回")
    void deduplicate_withEmptyOrSingle_shouldReturnAsIs() {
        assertTrue(deduplicator.deduplicate(null).isEmpty());
        assertTrue(deduplicator.deduplicate(Collections.emptyList()).isEmpty());

        Document single = new Document("唯一文档", Map.of("score", 0.9));
        List<Document> result = deduplicator.deduplicate(Collections.singletonList(single));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("语义相似的文档应被移除，语义不同的文档应保留")
    void deduplicate_shouldRemoveSemanticDuplicates() {
        Document docA = new Document("Spring Boot 是一个用于构建微服务的 Java 框架", Map.of("score", 0.95));
        Document docB = new Document("Spring Boot 是 Java 微服务开发框架", Map.of("score", 0.90));
        Document docC = new Document("Redis 是一个内存数据库", Map.of("score", 0.85));

        // A 与 B 向量相同（相似度 1.0），A 与 C 正交（相似度 0.0）
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(response(new float[]{1.0f, 0.0f}))
                .thenReturn(response(new float[]{1.0f, 0.0f}))
                .thenReturn(response(new float[]{0.0f, 1.0f}));

        List<Document> result = deduplicator.deduplicate(Arrays.asList(docA, docB, docC));

        verify(embeddingModel, times(3)).call(any(EmbeddingRequest.class));
        assertEquals(2, result.size());
        assertTrue(result.contains(docA));
        assertFalse(result.contains(docB));
        assertTrue(result.contains(docC));
    }

    @Test
    @DisplayName("Embedding 失败时应保留文档且不抛异常")
    void deduplicate_withEmbeddingFailure_shouldFallback() {
        Document docA = new Document("文档 A", Map.of("score", 0.9));
        Document docB = new Document("文档 B", Map.of("score", 0.8));

        doThrow(new RuntimeException("API 异常")).when(embeddingModel).call(any(EmbeddingRequest.class));

        List<Document> result = deduplicator.deduplicate(Arrays.asList(docA, docB));

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("distance 应被正确归一化为分数")
    void extractNormalizedScore_withDistance_shouldInvert() {
        Document doc = new Document("测试", Map.of("distance", 0.5));
        // 通过公开结果间接验证：distance=0.5 -> 分数 1/(1+0.5)=0.667，排序高于 distance=1.0 -> 0.5
        Document doc2 = new Document("测试2", Map.of("distance", 1.0));

        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(response(new float[]{1.0f, 0.0f}))
                .thenReturn(response(new float[]{0.0f, 1.0f}));

        List<Document> result = deduplicator.deduplicate(Arrays.asList(doc2, doc));

        verify(embeddingModel, times(2)).call(any(EmbeddingRequest.class));
        assertEquals(2, result.size());
        // doc 分数更高，应排在第一位
        assertEquals(doc, result.get(0));
    }

    private EmbeddingResponse response(float[] embedding) {
        return new EmbeddingResponse(Collections.singletonList(new Embedding(embedding, 0)));
    }
}
