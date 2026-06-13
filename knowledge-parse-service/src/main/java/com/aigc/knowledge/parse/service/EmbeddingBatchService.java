package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.DocumentChunk;
import com.aigc.knowledge.parse.dto.EmbeddingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Embedding 批量处理服务。
 *
 * 设计背景：
 *   大模型 Embedding API 通常有 QPS / TPM 限制，如果一次性并发调用所有 Chunk，
 *   容易触发限流导致大量失败。因此采用"分批 + 可控并发"策略：
 *     1. 将 Chunk 列表按固定大小分组（batch）；
 *     2. 每批内部串行或有限并发调用 Embedding API；
 *     3. 批次之间通过 CompletableFuture 组合，整体并发度受 embedding-concurrency 控制。
 *
 * JDK8 特性应用：
 *   - Stream：IntStream.range 分批次、List.stream 转换结果；
 *   - Lambda：CompletableFuture 回调、EmbeddingResponse 映射；
 *   - Optional：空输入防御、空结果兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingBatchService {

    /**
     * 注入 DashScope Embedding 模型。
     */
    @Qualifier("dashScopeEmbeddingModel")
    private final EmbeddingModel embeddingModel;

    /**
     * 向量化线程池：控制并发度，避免触发 API 限流。
     */
    @Qualifier("vectorizeTaskExecutor")
    private final Executor vectorizeExecutor;

    @Value("${parse.embedding-batch-size:16}")
    private int embeddingBatchSize;

    @Value("${parse.embedding-concurrency:4}")
    private int embeddingConcurrency;

    /**
     * 批量生成 Embedding。
     *
     * @param chunks 文档切片列表
     * @return Embedding 结果列表（顺序与输入一致）
     */
    public List<EmbeddingResult> embedChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 按 batch size 分组
        List<List<DocumentChunk>> batches = partition(chunks, embeddingBatchSize);
        log.info("[Embedding] 开始批量向量化, chunks={}, batches={}, concurrency={}",
                chunks.size(), batches.size(), embeddingConcurrency);

        // 2. 控制并发度：使用 parallelStream 并限制并发线程数
        //    通过自定义 ForkJoinPool 或 CompletableFuture + semaphore 都可以，
        //    这里使用 CompletableFuture.allOf + 自定义线程池更可控。
        List<CompletableFuture<List<EmbeddingResult>>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(() -> embedBatch(batch), vectorizeExecutor))
                .collect(Collectors.toList());

        // 3. 等待所有批次完成，并按原始顺序合并结果
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .flatMap(f -> f.join().stream())
                        .collect(Collectors.toList()))
                .join();
    }

    /**
     * 对单批 Chunk 调用 Embedding API。
     *
     * @param batch 当前批次
     * @return 该批次的 Embedding 结果
     */
    private List<EmbeddingResult> embedBatch(List<DocumentChunk> batch) {
        try {
            List<String> texts = batch.stream()
                    .map(DocumentChunk::getCleanedContent)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.toList());

            if (texts.isEmpty()) {
                return Collections.emptyList();
            }

            EmbeddingRequest request = new EmbeddingRequest(texts, null);
            EmbeddingResponse response = embeddingModel.call(request);

            return IntStream.range(0, batch.size())
                    .mapToObj(i -> buildResult(batch.get(i), response, i))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[Embedding] 批次向量化失败, batchSize={}", batch.size(), e);
            // 单批失败不影响其他批次，返回该批全部失败的结果
            return batch.stream()
                    .map(chunk -> EmbeddingResult.builder()
                            .chunkIndex(chunk.getIndex())
                            .text(chunk.getCleanedContent())
                            .success(false)
                            .errorMsg("Embedding 调用失败: " + e.getMessage())
                            .build())
                    .collect(Collectors.toList());
        }
    }

    /**
     * 根据 EmbeddingResponse 构建单个 EmbeddingResult。
     */
    private EmbeddingResult buildResult(DocumentChunk chunk, EmbeddingResponse response, int index) {
        return Optional.ofNullable(response)
                .filter(r -> r.getResults() != null && index < r.getResults().size())
                .map(r -> r.getResults().get(index))
                .filter(result -> result.getOutput() != null)
                .map(result -> EmbeddingResult.builder()
                        .chunkIndex(chunk.getIndex())
                        .text(chunk.getCleanedContent())
                        .embedding(toFloatList(result.getOutput()))
                        .success(true)
                        .build())
                .orElseGet(() -> EmbeddingResult.builder()
                        .chunkIndex(chunk.getIndex())
                        .text(chunk.getCleanedContent())
                        .success(false)
                        .errorMsg("Embedding 结果缺失")
                        .build());
    }

    /**
     * 将 float[] 转换为 List<Float>。
     */
    private List<Float> toFloatList(float[] floats) {
        return Optional.ofNullable(floats)
                .map(arr -> IntStream.range(0, arr.length)
                        .mapToObj(i -> arr[i])
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    /**
     * 将列表按指定大小分组。
     *
     * @param list 原始列表
     * @param size 每批大小
     * @param <T>  元素类型
     * @return 分组后的列表
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
