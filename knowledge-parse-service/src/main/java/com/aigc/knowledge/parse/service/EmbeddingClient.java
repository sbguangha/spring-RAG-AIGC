package com.aigc.knowledge.parse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Embedding API 批量调用客户端。
 * <p>
 * 将大量文本切片按固定批次（batch）切分，顺序或并发调用 EmbeddingModel，
 * 避免一次性发送过多文本触发大模型 API 限流。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final EmbeddingModel embeddingModel;

    public EmbeddingClient(@Qualifier("bailianEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 批量获取文本切片的向量嵌入。
     *
     * @param texts    清洗后的文本列表
     * @param batchSize 每批请求的文本数量，建议根据 API 限流配置
     * @return 与输入列表顺序一致的 float[] 嵌入列表
     */
    public List<float[]> embedTexts(List<String> texts, int batchSize) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用 Stream + Lambda 将列表按 batchSize 分批
        List<List<String>> batches = IntStream.rangeClosed(0, (texts.size() - 1) / batchSize)
                .boxed()
                .map(idx -> {
                    int start = idx * batchSize;
                    int end = Math.min(start + batchSize, texts.size());
                    return texts.subList(start, end);
                })
                .collect(Collectors.toList());

        log.info("开始批量 Embedding，共 {} 条文本，分为 {} 批，每批 {} 条",
                texts.size(), batches.size(), batchSize);

        return batches.stream()
                .flatMap(batch -> embedBatch(batch).stream())
                .collect(Collectors.toList());
    }

    /**
     * 使用并发流（parallelStream）批量请求 Embedding，适用于本地/无严格限流场景。
     * <p>
     * 注意：调用方需根据实际 API 限流评估并发度，可通过自定义 ForkJoinPool 进一步控制。
     *
     * @param texts     清洗后的文本列表
     * @param batchSize 每批数量
     * @return 嵌入列表
     */
    public List<float[]> embedTextsParallel(List<String> texts, int batchSize) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<String>> batches = IntStream.rangeClosed(0, (texts.size() - 1) / batchSize)
                .boxed()
                .map(idx -> {
                    int start = idx * batchSize;
                    int end = Math.min(start + batchSize, texts.size());
                    return texts.subList(start, end);
                })
                .collect(Collectors.toList());

        return batches.parallelStream()
                .flatMap(batch -> embedBatch(batch).stream())
                .collect(Collectors.toList());
    }

    /**
     * 调用 EmbeddingModel 处理单批文本，使用 Optional 安全处理空响应。
     */
    private List<float[]> embedBatch(List<String> batch) {
        try {
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));
            List<float[]> embeddings = Optional.ofNullable(response)
                    .map(EmbeddingResponse::getResults)
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(result -> result != null ? result.getOutput() : null)
                    .map(this::safeCopyEmbedding)
                    .collect(Collectors.toList());

            // 防御：若响应缺失或结果数量与批次不一致，返回与批次等长的空数组，避免下游错位
            if (embeddings.size() != batch.size()) {
                return batch.stream()
                        .map(text -> new float[0])
                        .collect(Collectors.toList());
            }
            return embeddings;
        } catch (Exception e) {
            log.error("Embedding 批量请求失败，本批 {} 条", batch.size(), e);
            // 失败时返回与批次等长的空数组，避免整体流程中断
            return batch.stream()
                    .map(text -> new float[0])
                    .collect(Collectors.toList());
        }
    }

    /**
     * 安全复制 Embedding 向量，避免返回潜在不可变的内部数组引用。
     */
    private float[] safeCopyEmbedding(float[] embedding) {
        return Optional.ofNullable(embedding)
                .filter(arr -> arr.length > 0)
                .map(arr -> Arrays.copyOf(arr, arr.length))
                .orElse(new float[0]);
    }
}
