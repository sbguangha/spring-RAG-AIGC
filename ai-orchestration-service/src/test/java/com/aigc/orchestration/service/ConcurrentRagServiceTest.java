package com.aigc.orchestration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * ConcurrentRagService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentRagServiceTest {

    @Mock
    private VectorStore milvusVectorStore;

    @Mock
    private VectorStore elasticsearchVectorStore;

    @Mock
    private ChatModel chatModel;

    @Mock
    private SemanticDeduplicator semanticDeduplicator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private Executor executor;

    private ConcurrentRagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new ConcurrentRagService(milvusVectorStore, elasticsearchVectorStore,
                chatModel, semanticDeduplicator, redisTemplate, executor);
    }

    @Test
    @DisplayName("score 越大越相似应直接使用")
    void extractVectorScore_withScore_shouldUseDirectly() throws Exception {
        Document doc = new Document("test", Map.of("score", 0.85));
        double score = invokeExtractVectorScore(doc);
        assertEquals(0.85, score, 0.001);
    }

    @Test
    @DisplayName("distance 越小越相似应被反转为分数")
    void extractVectorScore_withDistance_shouldInvert() throws Exception {
        Document doc = new Document("test", Map.of("distance", 1.0));
        double score = invokeExtractVectorScore(doc);
        assertEquals(0.5, score, 0.001);
    }

    @Test
    @DisplayName("空 metadata 应返回 0.0")
    void extractVectorScore_withEmptyMetadata_shouldReturnZero() throws Exception {
        Document doc = new Document("test", Collections.emptyMap());
        double score = invokeExtractVectorScore(doc);
        assertEquals(0.0, score, 0.001);
    }

    @Test
    @DisplayName("processDocuments 应调用语义去重器")
    void processDocuments_shouldCallSemanticDeduplicator() throws Exception {
        Document doc1 = new Document("内容 A", Map.of("score", 0.9));
        Document doc2 = new Document("内容 B", Map.of("score", 0.8));
        List<Document> input = Arrays.asList(doc1, doc2);

        when(semanticDeduplicator.deduplicate(anyList())).thenReturn(input);

        List<Document> result = invokeProcessDocuments(input, "问题");

        verify(semanticDeduplicator, times(1)).deduplicate(anyList());
        assertNotNull(result);
    }

    @Test
    @DisplayName("processDocuments 对空输入应返回空列表")
    void processDocuments_withEmptyInput_shouldReturnEmpty() throws Exception {
        List<Document> result = invokeProcessDocuments(Collections.emptyList(), "问题");
        assertTrue(result.isEmpty());
    }

    private double invokeExtractVectorScore(Document doc) throws Exception {
        Method method = ConcurrentRagService.class.getDeclaredMethod("extractVectorScore", Document.class);
        method.setAccessible(true);
        return (double) method.invoke(ragService, doc);
    }

    @SuppressWarnings("unchecked")
    private List<Document> invokeProcessDocuments(List<Document> docs, String query) throws Exception {
        Method method = ConcurrentRagService.class.getDeclaredMethod("processDocuments", List.class, String.class);
        method.setAccessible(true);
        return (List<Document>) method.invoke(ragService, docs, query);
    }
}
