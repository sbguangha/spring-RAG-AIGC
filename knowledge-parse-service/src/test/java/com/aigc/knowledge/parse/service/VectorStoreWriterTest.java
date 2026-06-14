package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.Chunk;
import com.aigc.knowledge.parse.exception.DocumentParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * VectorStoreWriter 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreWriterTest {

    @Mock
    private VectorStore milvusVectorStore;

    @Mock
    private VectorStore elasticsearchVectorStore;

    private VectorStoreWriter vectorStoreWriter;

    @BeforeEach
    void setUp() {
        vectorStoreWriter = new VectorStoreWriter(milvusVectorStore, elasticsearchVectorStore,
                "knowledge_chunks_v4", "aigc_v4");
    }

    @Test
    @DisplayName("正常 Chunk 应双写到 Milvus 和 ES")
    void writeChunks_shouldWriteToBothStores() {
        List<Chunk> chunks = Arrays.asList(
                Chunk.builder().index(0).content("内容一").summary("摘要一").build(),
                Chunk.builder().index(1).content("内容二").summary("摘要二").build()
        );

        vectorStoreWriter.writeChunks(chunks, "test.txt");

        verify(milvusVectorStore, times(1)).add(anyList());
        verify(elasticsearchVectorStore, times(1)).add(anyList());
    }

    @Test
    @DisplayName("空列表或空白内容不应调用 VectorStore")
    void writeChunks_withEmptyList_shouldNotCallStores() {
        vectorStoreWriter.writeChunks(Collections.emptyList(), "empty.txt");
        vectorStoreWriter.writeChunks(Collections.singletonList(
                Chunk.builder().index(0).content("   ").build()), "blank.txt");

        verify(milvusVectorStore, never()).add(anyList());
        verify(elasticsearchVectorStore, never()).add(anyList());
    }

    @Test
    @DisplayName("Milvus 写入失败时不应影响 ES 写入")
    void writeChunks_withMilvusFailure_shouldStillWriteToEs() {
        List<Chunk> chunks = Collections.singletonList(
                Chunk.builder().index(0).content("内容").build());

        doThrow(new RuntimeException("Milvus 异常")).when(milvusVectorStore).add(anyList());

        vectorStoreWriter.writeChunks(chunks, "test.txt");

        verify(elasticsearchVectorStore, times(1)).add(anyList());
    }

    @Test
    @DisplayName("两个向量库均写入失败时应抛出异常")
    void writeChunks_withAllStoresFailure_shouldThrow() {
        List<Chunk> chunks = Collections.singletonList(
                Chunk.builder().index(0).content("内容").build());

        doThrow(new RuntimeException("Milvus 异常")).when(milvusVectorStore).add(anyList());
        doThrow(new RuntimeException("ES 异常")).when(elasticsearchVectorStore).add(anyList());

        assertThrows(DocumentParseException.class, () -> vectorStoreWriter.writeChunks(chunks, "test.txt"));
    }

    @Test
    @DisplayName("Document 元数据应包含 source 和 chunkIndex")
    void writeChunks_shouldCarryMetadata() {
        List<Chunk> chunks = Collections.singletonList(
                Chunk.builder().index(42).content("测试内容").summary("摘要").build());

        vectorStoreWriter.writeChunks(chunks, "doc.txt");

        verify(milvusVectorStore).add(argThat(docs -> {
            if (docs == null || docs.isEmpty()) return false;
            Document doc = docs.get(0);
            return "doc.txt".equals(doc.getMetadata().get("source"))
                    && Integer.valueOf(42).equals(doc.getMetadata().get("chunkIndex"))
                    && "摘要".equals(doc.getMetadata().get("summary"));
        }));
    }
}
