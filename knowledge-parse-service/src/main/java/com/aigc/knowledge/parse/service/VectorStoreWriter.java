package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.Chunk;
import com.aigc.knowledge.parse.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
public class VectorStoreWriter {

    private final VectorStore milvusVectorStore;

    private final VectorStore elasticsearchVectorStore;

    private final String milvusCollectionName;

    private final String elasticsearchIndexName;

    public VectorStoreWriter(@Qualifier("milvusVectorStore") VectorStore milvusVectorStore,
                             @Qualifier("elasticsearchVectorStore") VectorStore elasticsearchVectorStore,
                             @Value("${vector.milvus.collection-name:knowledge_chunks_v4}") String milvusCollectionName,
                             @Value("${vector.elasticsearch.index-prefix:aigc_v4}") String elasticsearchIndexPrefix) {
        this.milvusVectorStore = milvusVectorStore;
        this.elasticsearchVectorStore = elasticsearchVectorStore;
        this.milvusCollectionName = milvusCollectionName;
        this.elasticsearchIndexName = elasticsearchIndexPrefix + "_knowledge_chunks";
    }

    /**
     * 单次 Embedding 请求的最大文档数。
     * <p>
     * 百炼 text-embedding-v4 接口要求 batch size 不能超过 10，
     * 超过会返回 {@code InvalidParameter: batch size is invalid, it should not be larger than 10}。
     */
    private static final int EMBEDDING_BATCH_SIZE = 10;

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

        log.info("开始向向量库写入, fileName={}, chunks={}, milvusCollection={}, esIndex={}",
                fileName, documents.size(), milvusCollectionName, elasticsearchIndexName);

        boolean milvusSuccess = writeBatchesToMilvus(documents, fileName);
        boolean elasticsearchSuccess = writeBatchesToElasticsearch(documents, fileName);
        if (!milvusSuccess && !elasticsearchSuccess) {
            throw new DocumentParseException(String.format(
                    "向量库写入失败：Milvus=%s, ES=%s, fileName=%s, chunks=%d, milvusCollection=%s, esIndex=%s",
                    milvusSuccess, elasticsearchSuccess, fileName, documents.size(), milvusCollectionName, elasticsearchIndexName));
        }
        log.info("向量库写入完成, fileName={}, milvusSuccess={}, esSuccess={}",
                fileName, milvusSuccess, elasticsearchSuccess);
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

    private boolean writeBatchesToMilvus(List<Document> documents, String fileName) {
        if (documents.isEmpty()) {
            return true;
        }
        int total = documents.size();
        int successCount = 0;
        for (int i = 0; i < total; i += EMBEDDING_BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, total));
            if (writeToMilvus(batch, fileName)) {
                successCount += batch.size();
            }
        }
        log.info("Milvus 分批写入完成, fileName={}, success={}/{}"
                + (successCount < total ? ", 部分批次失败已降级" : ""),
                fileName, successCount, total);
        return successCount > 0;
    }

    private boolean writeBatchesToElasticsearch(List<Document> documents, String fileName) {
        if (documents.isEmpty()) {
            return true;
        }
        int total = documents.size();
        int successCount = 0;
        for (int i = 0; i < total; i += EMBEDDING_BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, total));
            if (writeToElasticsearch(batch, fileName)) {
                successCount += batch.size();
            }
        }
        log.info("ES 分批写入完成, fileName={}, success={}/{}"
                + (successCount < total ? ", 部分批次失败已降级" : ""),
                fileName, successCount, total);
        return successCount > 0;
    }

    private boolean writeToMilvus(List<Document> documents, String fileName) {
        try {
            milvusVectorStore.add(documents);
            if (log.isDebugEnabled()) {
                log.debug("写入 Milvus 成功, fileName={}, count={}", fileName, documents.size());
            }
            return true;
        } catch (Exception e) {
            log.error("写入 Milvus 失败, fileName={}, batchSize={}", fileName, documents.size(), e);
            return false;
        }
    }

    private boolean writeToElasticsearch(List<Document> documents, String fileName) {
        try {
            elasticsearchVectorStore.add(documents);
            if (log.isDebugEnabled()) {
                log.debug("写入 ES 成功, fileName={}, count={}", fileName, documents.size());
            }
            return true;
        } catch (Exception e) {
            log.error("写入 ES 失败, fileName={}, batchSize={}", fileName, documents.size(), e);
            return false;
        }
    }
}
