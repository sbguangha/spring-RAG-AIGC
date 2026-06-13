package com.aigc.knowledge.parse.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EmbeddingClient 单元测试。
 * <p>
 * 验证分批处理、并发流调用、空响应处理及 API 异常时的降级行为。
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingClientTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingClient embeddingClient;

    @BeforeEach
    void setUp() {
        embeddingClient = new EmbeddingClient(embeddingModel);
    }

    @Test
    @DisplayName("空文本列表应返回空结果")
    void embedTexts_withEmptyList_shouldReturnEmpty() {
        assertTrue(embeddingClient.embedTexts(null, 8).isEmpty());
        assertTrue(embeddingClient.embedTexts(Collections.emptyList(), 8).isEmpty());
    }

    @Test
    @DisplayName("应按 batchSize 分批调用 EmbeddingModel")
    void embedTexts_shouldBatchBySize() {
        List<String> texts = Arrays.asList("a", "b", "c", "d", "e");

        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(mockResponse(2))
                .thenReturn(mockResponse(2))
                .thenReturn(mockResponse(1));

        List<float[]> embeddings = embeddingClient.embedTexts(texts, 2);

        assertEquals(5, embeddings.size());
        verify(embeddingModel, times(3)).call(any(EmbeddingRequest.class));
    }

    @Test
    @DisplayName("并发流模式应完成全部分批调用")
    void embedTextsParallel_shouldProcessAllBatches() {
        List<String> texts = Arrays.asList("1", "2", "3", "4");

        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(mockResponse(2))
                .thenReturn(mockResponse(2));

        List<float[]> embeddings = embeddingClient.embedTextsParallel(texts, 2);

        assertEquals(4, embeddings.size());
        verify(embeddingModel, atLeast(2)).call(any(EmbeddingRequest.class));
    }

    @Test
    @DisplayName("EmbeddingModel 返回 null 时应降级为空向量数组")
    void embedTexts_withNullResponse_shouldReturnEmptyArrays() {
        List<String> texts = Arrays.asList("x", "y");
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(null);

        List<float[]> embeddings = embeddingClient.embedTexts(texts, 10);

        assertEquals(2, embeddings.size());
        assertEquals(0, embeddings.get(0).length);
        assertEquals(0, embeddings.get(1).length);
    }

    @Test
    @DisplayName("单批调用异常时不应中断整体流程")
    void embedTexts_withBatchException_shouldFallback() {
        List<String> texts = Arrays.asList("a", "b");

        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenThrow(new RuntimeException("API 限流"));

        List<float[]> embeddings = embeddingClient.embedTexts(texts, 10);

        assertEquals(2, embeddings.size());
        assertEquals(0, embeddings.get(0).length);
    }

    private EmbeddingResponse mockResponse(int resultCount) {
        List<Embedding> results = java.util.stream.IntStream.range(0, resultCount)
                .mapToObj(i -> new Embedding(new float[]{0.1f, 0.2f, 0.3f}, i))
                .toList();
        return new EmbeddingResponse(results);
    }
}
