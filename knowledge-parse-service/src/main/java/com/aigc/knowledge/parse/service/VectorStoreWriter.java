package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.Chunk;
import com.aigc.knowledge.parse.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    private final boolean strictWriteMode;

    public VectorStoreWriter(@Qualifier("milvusVectorStore") VectorStore milvusVectorStore,
                             @Qualifier("elasticsearchVectorStore") VectorStore elasticsearchVectorStore,
                             @Value("${vector.milvus.collection-name:knowledge_chunks_v4}") String milvusCollectionName,
                             @Value("${vector.elasticsearch.index-prefix:aigc_v4}") String elasticsearchIndexPrefix,
                             @Value("${vector.write.strict-mode:true}") boolean strictWriteMode) {
        this.milvusVectorStore = milvusVectorStore;
        this.elasticsearchVectorStore = elasticsearchVectorStore;
        this.milvusCollectionName = milvusCollectionName;
        this.elasticsearchIndexName = elasticsearchIndexPrefix + "_knowledge_chunks";
        this.strictWriteMode = strictWriteMode;
    }

    /**
     * 单次 Embedding 请求的最大文档数。
     * <p>
     * 百炼 text-embedding-v4 接口要求 batch size 不能超过 10，
     * 超过会返回 {@code InvalidParameter: batch size is invalid, it should not be larger than 10}。
     */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    /**
     * 向量库批量写入结果。
     */
    @lombok.Value
    private static class WriteResult {
        int successCount;
        int failCount;
        List<String> errors;

        boolean isSuccess() {
            return failCount == 0 && successCount > 0;
        }

        boolean hasAnySuccess() {
            return successCount > 0;
        }
    }

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

        log.info("开始向向量库写入, fileName={}, chunks={}, milvusCollection={}, esIndex={}, strictMode={}",
                fileName, documents.size(), milvusCollectionName, elasticsearchIndexName, strictWriteMode);

        WriteResult milvusResult = writeBatchesToMilvus(documents, fileName);
        WriteResult esResult = writeBatchesToElasticsearch(documents, fileName);

        // 严格模式：要求 Milvus 和 ES 全部成功；非严格模式：至少有一个成功
        boolean allSuccess = milvusResult.isSuccess() && esResult.isSuccess();
        boolean anySuccess = milvusResult.hasAnySuccess() || esResult.hasAnySuccess();

        if (strictWriteMode) {
            if (!allSuccess) {
                throw new DocumentParseException(String.format(
                        "向量库写入失败（严格模式要求双写均成功）：Milvus=%s, ES=%s, fileName=%s, chunks=%d, milvusCollection=%s, esIndex=%s",
                        milvusResult, esResult, fileName, documents.size(), milvusCollectionName, elasticsearchIndexName));
            }
        } else if (!anySuccess) {
            throw new DocumentParseException(String.format(
                    "向量库写入失败（双写均不可用）：Milvus=%s, ES=%s, fileName=%s, chunks=%d, milvusCollection=%s, esIndex=%s",
                    milvusResult, esResult, fileName, documents.size(), milvusCollectionName, elasticsearchIndexName));
        }

        log.info("向量库写入完成, fileName={}, strictMode={}, milvus={}, es={}",
                fileName, strictWriteMode, milvusResult, esResult);
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

    private WriteResult writeBatchesToMilvus(List<Document> documents, String fileName) {
        if (documents.isEmpty()) {
            return new WriteResult(0, 0, Collections.emptyList());
        }
        int total = documents.size();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < total; i += EMBEDDING_BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, total));
            try {
                milvusVectorStore.add(batch);
                successCount += batch.size();
                if (log.isDebugEnabled()) {
                    log.debug("写入 Milvus 成功, fileName={}, batchIndex={}, batchSize={}", fileName, i, batch.size());
                }
            } catch (Exception e) {
                failCount += batch.size();
                String error = String.format("Milvus batch [%d,%d) 失败: %s", i, Math.min(i + EMBEDDING_BATCH_SIZE, total), e.getMessage());
                errors.add(error);
                log.error("写入 Milvus 失败, fileName={}, batchIndex={}, batchSize={}", fileName, i, batch.size(), e);
            }
        }

        WriteResult result = new WriteResult(successCount, failCount, errors);
        log.info("Milvus 分批写入完成, fileName={}, result={}, milvusCollection={}",
                fileName, result, milvusCollectionName);
        return result;
    }

    private WriteResult writeBatchesToElasticsearch(List<Document> documents, String fileName) {
        if (documents.isEmpty()) {
            return new WriteResult(0, 0, Collections.emptyList());
        }
        int total = documents.size();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < total; i += EMBEDDING_BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, total));
            try {
                elasticsearchVectorStore.add(batch);
                successCount += batch.size();
                if (log.isDebugEnabled()) {
                    log.debug("写入 ES 成功, fileName={}, batchIndex={}, batchSize={}", fileName, i, batch.size());
                }
            } catch (Exception e) {
                failCount += batch.size();
                String error = String.format("ES batch [%d,%d) 失败: %s", i, Math.min(i + EMBEDDING_BATCH_SIZE, total), e.getMessage());
                errors.add(error);
                log.error("写入 ES 失败, fileName={}, batchIndex={}, batchSize={}", fileName, i, batch.size(), e);
            }
        }

        WriteResult result = new WriteResult(successCount, failCount, errors);
        log.info("ES 分批写入完成, fileName={}, result={}, esIndex={}",
                fileName, result, elasticsearchIndexName);
        return result;
    }
}
