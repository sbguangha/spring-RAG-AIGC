package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.Chunk;
import com.aigc.knowledge.parse.exception.DocumentParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 向量库存写入器。
 * <p>
 * 将解析后的 Chunk 列表转换为 Spring AI Document，并双写到 Milvus 与 Elasticsearch。
 * 写入失败时单独降级，不影响另一侧存储。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreWriter {

    @Qualifier("milvusVectorStore")
    private final VectorStore milvusVectorStore;

    @Qualifier("elasticsearchVectorStore")
    private final VectorStore elasticsearchVectorStore;

    /**
     * 将 Chunk 列表写入向量库。
     *
     * @param chunks   解析后的切片列表
     * @param fileName 来源文件名
     */
    public void writeChunks(List<Chunk> chunks, String fileName) {
        List<Chunk> validChunks = Optional.ofNullable(chunks)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank())
                .collect(Collectors.toList());

        if (validChunks.isEmpty()) {
            log.warn("没有可写入向量库的切片, fileName={}", fileName);
            return;
        }

        List<Document> documents = validChunks.stream()
                .map(chunk -> toDocument(chunk, fileName))
                .collect(Collectors.toList());

        boolean milvusSuccess = writeToMilvus(documents, fileName);
        boolean elasticsearchSuccess = writeToElasticsearch(documents, fileName);
        if (!milvusSuccess && !elasticsearchSuccess) {
            throw new DocumentParseException("向量库写入失败：Milvus 和 Elasticsearch 均未写入成功");
        }
    }

    private Document toDocument(Chunk chunk, String fileName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", fileName);
        metadata.put("chunkIndex", chunk.getIndex());
        metadata.put("startOffset", chunk.getStartOffset());
        metadata.put("endOffset", chunk.getEndOffset());
        Optional.ofNullable(chunk.getSummary())
                .filter(summary -> !summary.isBlank())
                .ifPresent(summary -> metadata.put("summary", summary));
        Optional.ofNullable(chunk.getPageNumber())
                .ifPresent(page -> metadata.put("pageNumber", page));
        Optional.ofNullable(chunk.getMetadata())
                .ifPresent(existing -> existing.forEach(metadata::putIfAbsent));

        return new Document(chunk.getContent(), metadata);
    }

    private boolean writeToMilvus(List<Document> documents, String fileName) {
        try {
            milvusVectorStore.add(documents);
            log.info("写入 Milvus 成功, fileName={}, count={}", fileName, documents.size());
            return true;
        } catch (Exception e) {
            log.error("写入 Milvus 失败, fileName={}", fileName, e);
            return false;
        }
    }

    private boolean writeToElasticsearch(List<Document> documents, String fileName) {
        try {
            elasticsearchVectorStore.add(documents);
            log.info("写入 ES 成功, fileName={}, count={}", fileName, documents.size());
            return true;
        } catch (Exception e) {
            log.error("写入 ES 失败, fileName={}", fileName, e);
            return false;
        }
    }
}
