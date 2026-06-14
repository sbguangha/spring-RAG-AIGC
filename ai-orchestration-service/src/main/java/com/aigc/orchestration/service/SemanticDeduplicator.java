package com.aigc.orchestration.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 语义去重器。
 * <p>
 * 基于 Embedding 向量余弦相似度，对检索结果进行语义去重：
 * 按归一化检索分数排序后，保留分数最高的文档，移除与其语义相似度超过阈值的文档。
 * 可识别"意思一样、表述不同"的重复内容。
 */
@Slf4j
@Component
public class SemanticDeduplicator {

    private final EmbeddingModel embeddingModel;

    private final boolean enabled;
    private final double similarityThreshold;

    public SemanticDeduplicator(EmbeddingModel embeddingModel) {
        this(embeddingModel, true, 0.92);
    }

    @Autowired
    public SemanticDeduplicator(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
                                @Value("${rag.semantic-dedup.enabled:true}") boolean enabled,
                                @Value("${rag.semantic-dedup.threshold:0.92}") double similarityThreshold) {
        this.embeddingModel = embeddingModel;
        this.enabled = enabled;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 对文档列表进行语义去重。
     *
     * @param documents 待去重文档列表
     * @return 去重后的文档列表
     */
    public List<Document> deduplicate(List<Document> documents) {
        if (!enabled || documents == null || documents.size() <= 1) {
            return documents == null ? Collections.emptyList() : documents;
        }

        // 1. 按归一化检索分数降序排列，优先保留高质量文档
        List<Document> sorted = documents.stream()
                .filter(doc -> doc != null && StringUtils.isNotBlank(doc.getText()))
                .sorted(Comparator.comparingDouble(this::extractNormalizedScore).reversed())
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 批量获取 Embedding（逐个调用并降级，避免整批失败）
        List<float[]> embeddings = sorted.stream()
                .map(doc -> embedSafely(doc.getText()))
                .collect(Collectors.toList());

        // 3. 贪心策略：保留当前文档，移除与其高相似的后续文档
        List<Document> result = new ArrayList<>();
        boolean[] removed = new boolean[sorted.size()];

        for (int i = 0; i < sorted.size(); i++) {
            if (removed[i]) {
                continue;
            }
            Document keeper = sorted.get(i);
            result.add(keeper);

            float[] keeperEmbedding = embeddings.get(i);
            if (keeperEmbedding.length == 0) {
                continue;
            }

            for (int j = i + 1; j < sorted.size(); j++) {
                if (!removed[j]) {
                    double sim = cosineSimilarity(keeperEmbedding, embeddings.get(j));
                    if (sim >= similarityThreshold) {
                        removed[j] = true;
                        if (log.isDebugEnabled()) {
                            log.debug("语义去重移除文档, sim={:.4f}, keeper={}, removed={}",
                                    sim,
                                    StringUtils.abbreviate(keeper.getText(), 30),
                                    StringUtils.abbreviate(sorted.get(j).getText(), 30));
                        }
                    }
                }
            }
        }

        log.info("语义去重完成, 原始文档数={}, 去重后文档数={}", documents.size(), result.size());
        return result;
    }

    /**
     * 安全调用 EmbeddingModel，失败或空响应时返回空数组。
     */
    private float[] embedSafely(String text) {
        try {
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(Collections.singletonList(text), null));
            return Optional.ofNullable(response)
                    .map(EmbeddingResponse::getResults)
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0))
                    .map(result -> result != null ? result.getOutput() : null)
                    .map(this::copyOf)
                    .orElse(new float[0]);
        } catch (Exception e) {
            log.warn("语义去重 Embedding 调用失败, text={}", StringUtils.abbreviate(text, 50), e);
            return new float[0];
        }
    }

    private float[] copyOf(float[] arr) {
        return Optional.ofNullable(arr)
                .filter(a -> a.length > 0)
                .map(a -> java.util.Arrays.copyOf(a, a.length))
                .orElse(new float[0]);
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 从 Document metadata 中提取归一化相似度分数。
     * <ul>
     *     <li>score：越大越相似（ES / Spring AI 语义）</li>
     *     <li>distance：越小越相似（Milvus L2 距离），需要反转为 1/(1+distance)</li>
     * </ul>
     */
    private double extractNormalizedScore(Document doc) {
        return Optional.ofNullable(doc.getMetadata())
                .map(this::doExtractNormalizedScore)
                .orElse(0.0);
    }

    private double doExtractNormalizedScore(Map<String, Object> metadata) {
        Object scoreObj = metadata.get("score");
        if (scoreObj != null) {
            return parseDouble(scoreObj.toString());
        }

        Object distanceObj = metadata.get("distance");
        if (distanceObj != null) {
            double distance = parseDouble(distanceObj.toString());
            return distance <= 0.0 ? 0.0 : 1.0 / (1.0 + distance);
        }

        return 0.0;
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
