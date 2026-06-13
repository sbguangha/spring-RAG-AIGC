package com.aigc.orchestration.service;

import com.aigc.orchestration.util.MdcUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 并发 RAG（Retrieval-Augmented Generation）检索增强生成引擎。
 *
 * 核心流程：
 *   1. 接收用户 query；
 *   2. 并发执行：A) Milvus + Elasticsearch 混合向量检索；B) Redis 历史对话上下文获取；
 *   3. 使用 CompletableFuture.allOf 等待两个任务完成；
 *   4. 对检索结果使用 JDK8 Stream API 进行过滤、去重和重排序；
 *   5. 将 Context + History + Query 组装成 Prompt；
 *   6. 调用豆包 / DeepSeek（OpenAI 兼容接口）进行流式生成，返回 Flux<String>。
 *
 * 并发设计哲学：
 *   - 向量检索与 Redis 查询彼此无依赖，串行执行会浪费一次网络 RTT；
 *   - 通过 CompletableFuture + 自定义 IO 密集型线程池 ragExecutor，
 *     将总耗时从 T(retrieval) + T(redis) 降低为 max(T(retrieval), T(redis))；
 *   - 所有异步任务均通过 MdcUtil 包装，保证日志链路不中断。
 *
 * JDK8 特性应用点：
 *   - Lambda 表达式：CompletableFuture 回调、Stream 操作；
 *   - Stream API：filter/map/sorted/collect/Collectors.joining/Collectors.toMap；
 *   - Optional：防御性处理空值，避免 null 检查嵌套；
 *   - 方法引用：Document::getText、String::toUpperCase、Comparator.comparingDouble 等。
 */
@Slf4j
@Service
public class ConcurrentRagService {

    /**
     * Milvus 向量存储：负责基于 dense embedding 的相似度检索。
     */
    private final VectorStore milvusVectorStore;

    /**
     * Elasticsearch 向量存储：负责基于 keyword + vector 的混合检索。
     */
    private final VectorStore elasticsearchVectorStore;

    /**
     * 聊天客户端：通过 OpenAI 兼容接口调用豆包 / DeepSeek。
     * 使用 @Qualifier("openAiChatModel") 明确指定 OpenAI ChatModel，
     * 避免与 Spring AI Alibaba 的通义千问 ChatModel 冲突。
     */
    private final ChatClient chatClient;

    /**
     * Redis 模板：用于读取/写入历史对话上下文。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 自定义 RAG 线程池：IO 密集型，负责执行检索与 Redis 任务。
     */
    private final Executor ragExecutor;

    /**
     * 语义去重器：用于移除意思相同但表述不同的文档。
     */
    private final SemanticDeduplicator semanticDeduplicator;

    @Value("${rag.top-k:5}")
    private int topK;

    @Value("${rag.score-threshold:0.65}")
    private double scoreThreshold;

    @Value("${rag.rerank-enabled:true}")
    private boolean rerankEnabled;

    @Value("${rag.system-prompt:你是企业级智能知识库助手。请基于上下文回答问题。}")
    private String systemPrompt;

    @Value("${rag.history-key-prefix:rag:history}")
    private String historyKeyPrefix;

    @Value("${rag.history-rounds:5}")
    private int historyRounds;

    public ConcurrentRagService(@Qualifier("milvusVectorStore") VectorStore milvusVectorStore,
                                @Qualifier("elasticsearchVectorStore") VectorStore elasticsearchVectorStore,
                                @Qualifier("openAiChatModel") ChatModel chatModel,
                                SemanticDeduplicator semanticDeduplicator,
                                StringRedisTemplate redisTemplate,
                                @Qualifier("ragExecutor") Executor ragExecutor) {
        this.milvusVectorStore = milvusVectorStore;
        this.elasticsearchVectorStore = elasticsearchVectorStore;
        // 基于指定的 ChatModel 构建 ChatClient，用于流式对话
        this.chatClient = ChatClient.builder(chatModel).build();
        this.semanticDeduplicator = semanticDeduplicator;
        this.redisTemplate = redisTemplate;
        this.ragExecutor = ragExecutor;
    }

    /**
     * 流式 RAG 对话入口。
     *
     * 并发模型：
     *   - supplyAsync(taskA, ragExecutor)：向量检索；
     *   - supplyAsync(taskB, ragExecutor)：Redis 历史对话；
     *   - thenCombine：两个任务完成后合并结果；
     *   - Mono.fromFuture + flatMapMany：将 Future 结果桥接到 Reactor 的 Flux 流式响应。
     *
     * @param query 用户当前问题
     * @return Flux<String> 流式生成的文本片段
     */
    public Flux<String> streamChat(String query) {
        // 1. 从 MDC 获取 traceId 作为会话 ID；若无则生成 UUID，保证日志可追溯
        String sessionId = Optional.ofNullable(MDC.get("traceId"))
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));

        long startTime = System.currentTimeMillis();
        log.info("[RAG] 会话={}, 开始并发检索与生成, query={}", sessionId, query);

        // 2. 异步任务 A：向量相似度检索
        //    使用 MdcUtil.wrap 包装，保证子线程日志携带 traceId
        CompletableFuture<List<Document>> retrievalFuture = CompletableFuture.supplyAsync(
                MdcUtil.wrap(() -> doSimilaritySearch(query)),
                ragExecutor
        );

        // 3. 异步任务 B：Redis 历史对话上下文获取
        CompletableFuture<List<String>> historyFuture = CompletableFuture.supplyAsync(
                MdcUtil.wrap(() -> fetchHistoryContext(sessionId)),
                ragExecutor
        );

        // 4. 使用 allOf 等待 A、B 都完成，并通过 thenCombine 合并结果
        //    这里显式使用 allOf 语义：两个任务都成功后才会继续执行，任意失败都会传播异常。
        CompletableFuture<RagContext> contextFuture = retrievalFuture
                .thenCombine(historyFuture, (documents, history) -> {
                    // 合并点：两个任务均已完成，开始处理检索结果
                    log.info("[RAG] 会话={}, 检索完成, 原始文档数={}, 历史轮数={}",
                            sessionId, documents.size(), history.size() / 2);
                    List<Document> processedDocs = processDocuments(documents, query);
                    return new RagContext(processedDocs, history);
                });

        // 5. 将 CompletableFuture 桥接到 Reactor Flux，并在 ragExecutor 上订阅执行。
        //    flatMapMany：每个 RagContext 映射为一个 Flux<String> 流。
        return Mono.fromFuture(contextFuture)
                .subscribeOn(Schedulers.fromExecutor(ragExecutor))
                .flatMapMany(ctx -> {
                    long retrievalCost = System.currentTimeMillis() - startTime;
                    log.info("[RAG] 会话={}, 检索阶段耗时={}ms, 最终文档数={}, 历史轮数={}",
                            sessionId, retrievalCost, ctx.getDocuments().size(), ctx.getHistory().size() / 2);

                    String contextText = buildContextText(ctx.getDocuments());
                    String historyText = buildHistoryText(ctx.getHistory());
                    String userPrompt = buildUserPrompt(query, contextText, historyText);

                    // 用于收集完整回复，便于流式结束后异步写入 Redis 历史
                    StringBuilder answerBuilder = new StringBuilder();

                    return chatClient.prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .stream()
                            .content()
                            // doOnNext：每个流式片段到达时触发，可用于日志、SSE 推送、收集完整回复
                            .doOnNext(chunk -> {
                                answerBuilder.append(chunk);
                                if (log.isDebugEnabled()) {
                                    log.debug("[RAG] 会话={}, 流式片段={}", sessionId, chunk);
                                }
                            })
                            // doOnComplete：流式响应结束，异步保存本轮对话历史
                            .doOnComplete(() -> {
                                log.info("[RAG] 会话={}, 流式响应完成, 总耗时={}ms",
                                        sessionId, System.currentTimeMillis() - startTime);
                                saveHistoryAsync(sessionId, query, answerBuilder.toString());
                            })
                            // onErrorResume：流式过程中出现模型调用异常时，给出兜底响应，避免接口直接抛异常
                            .onErrorResume(throwable -> {
                                log.error("[RAG] 会话={}, 流式响应异常", sessionId, throwable);
                                return Flux.just("[系统提示] 模型调用异常，请稍后重试。");
                            });
                })
                // 若 allOf/thenCombine 阶段就抛异常，转换为友好的 Flux 错误流
                .onErrorResume(throwable -> {
                    log.error("[RAG] 会话={}, 检索或上下文组装异常", sessionId, throwable);
                    return Flux.just("[系统提示] 检索或上下文组装失败，请稍后重试。");
                });
    }

    /**
     * 执行混合向量相似度检索。
     *
     * 并发设计：
     *   同时查询 Milvus（纯向量检索）和 Elasticsearch（向量 + 关键词混合检索），
     *   两者彼此独立，使用 CompletableFuture.supplyAsync 在 ragExecutor 上并发执行，
     *   最终通过 allOf + join 合并结果，起到 1+1>2 的召回效果。
     *
     * @param query 用户问题
     * @return 合并去重后的文档列表
     */
    private List<Document> doSimilaritySearch(String query) {
        log.info("[RAG] 开始混合向量检索, query={}", query);

        // 子任务 A1：Milvus 向量检索
        CompletableFuture<List<Document>> milvusFuture = CompletableFuture.supplyAsync(
                MdcUtil.wrap(() -> searchVectorStore(milvusVectorStore, query, "Milvus")),
                ragExecutor
        );

        // 子任务 A2：Elasticsearch 混合检索
        CompletableFuture<List<Document>> esFuture = CompletableFuture.supplyAsync(
                MdcUtil.wrap(() -> searchVectorStore(elasticsearchVectorStore, query, "Elasticsearch")),
                ragExecutor
        );

        // 等待两个检索任务都完成
        CompletableFuture<List<Document>> combinedFuture = CompletableFuture
                .allOf(milvusFuture, esFuture)
                .thenApply(v -> {
                    List<Document> milvusDocs = milvusFuture.join();
                    List<Document> esDocs = esFuture.join();
                    log.info("[RAG] 混合检索完成, Milvus召回={}, ES召回={}", milvusDocs.size(), esDocs.size());
                    return mergeAndDeduplicate(milvusDocs, esDocs);
                });

        return combinedFuture.join();
    }

    /**
     * 对单个 VectorStore 执行检索，并统一封装异常处理。
     *
     * @param vectorStore 向量存储实现
     * @param query       查询文本
     * @param source      来源标识，用于日志
     * @return 检索结果
     */
    private List<Document> searchVectorStore(VectorStore vectorStore, String query, String source) {
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK * 2)
                            .similarityThreshold(scoreThreshold)
                            .build()
            );
            log.info("[RAG] {} 检索完成, 召回文档数={}", source, docs.size());
            return docs;
        } catch (Exception e) {
            // 单个向量库异常不应阻断整体流程，降级返回空列表
            log.warn("[RAG] {} 检索异常, 已降级", source, e);
            return Collections.emptyList();
        }
    }

    /**
     * 合并 Milvus 与 Elasticsearch 的检索结果，并按内容去重。
     *
     * JDK8 应用：
     *   使用 Stream.concat 将两个列表合并为一个流，
     *   再通过 Collectors.toMap 按内容哈希去重，保留相似度更高的文档。
     *
     * @param milvusDocs Milvus 检索结果
     * @param esDocs     Elasticsearch 检索结果
     * @return 合并去重后的文档列表
     */
    private List<Document> mergeAndDeduplicate(List<Document> milvusDocs, List<Document> esDocs) {
        return deduplicateByContent(Stream.concat(
                Optional.ofNullable(milvusDocs).orElse(Collections.emptyList()).stream(),
                Optional.ofNullable(esDocs).orElse(Collections.emptyList()).stream()
        ).collect(Collectors.toList()));
    }

    /**
     * 通用文档去重方法：按内容哈希去重，保留相似度更高的文档。
     *
     * @param documents 待去重文档列表
     * @return 去重后的文档列表
     */
    private List<Document> deduplicateByContent(List<Document> documents) {
        return Optional.ofNullable(documents)
                .orElse(Collections.emptyList())
                .stream()
                .filter(doc -> doc != null && StringUtils.isNotBlank(doc.getText()))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                doc -> hashContent(doc.getText()),
                                Function.identity(),
                                (existing, replacement) -> {
                                    double existingScore = extractVectorScore(existing);
                                    double replacementScore = extractVectorScore(replacement);
                                    return replacementScore > existingScore ? replacement : existing;
                                }
                        ),
                        map -> new ArrayList<>(map.values())
                ));
    }

    /**
     * 从 Redis 获取历史对话上下文。
     *
     * 存储结构：
     *   key = rag:history:{sessionId}
     *   value = Redis List，按顺序保存 "用户：xxx"、"助手：xxx" 字符串
     *   保留最近 historyRounds 轮（一问一答算一轮）。
     *
     * @param sessionId 会话 ID
     * @return 历史对话列表，顺序从早到晚
     */
    private List<String> fetchHistoryContext(String sessionId) {
        String key = historyKeyPrefix + ":" + sessionId;
        try {
            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                return Collections.emptyList();
            }
            // 只取最近 historyRounds * 2 条（一问一答为一轮）
            long start = Math.max(0, size - (long) historyRounds * 2);
            List<String> history = redisTemplate.opsForList().range(key, start, -1);
            return history == null ? Collections.emptyList() : history;
        } catch (Exception e) {
            // Redis 异常不应阻断主流程，返回空历史即可
            log.warn("[RAG] 会话={}, 获取历史对话异常, key={}", sessionId, key, e);
            return Collections.emptyList();
        }
    }

    /**
     * 处理检索结果：过滤、去重、重排序。
     *
     * JDK8 Stream 应用详解：
     *   1. filter：过滤掉空内容与分数明显过低的文档；
     *   2. deduplicateByContent：按内容哈希去重，遇到重复保留分数更高者；
     *   3. map + sorted：为每篇文档计算混合分数，按分数降序排列；
     *   4. limit：截取 topK 篇最相关的文档作为最终上下文。
     *
     * @param documents 原始检索结果
     * @param query     用户问题，用于关键词匹配重排序
     * @return 处理后的文档列表
     */
    private List<Document> processDocuments(List<Document> documents, String query) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        // 步骤 1：防御性过滤——检查文档内容与分数
        List<Document> filteredDocs = documents.stream()
                .filter(doc -> doc != null && StringUtils.isNotBlank(doc.getText()))
                .filter(doc -> {
                    double score = extractVectorScore(doc);
                    // 若 metadata 中没有分数，则保留；否则要求不低于阈值
                    return score <= 0.0 || score >= scoreThreshold;
                })
                .collect(Collectors.toList());

        // 步骤 2：按内容哈希去重，保留分数更高的版本
        List<Document> uniqueDocs = deduplicateByContent(filteredDocs);

        // 步骤 3：语义去重，移除意思相同但表述不同的文档
        List<Document> semanticallyUniqueDocs = semanticDeduplicator.deduplicate(uniqueDocs);

        // 步骤 4+5：重排序并截取 TopK
        return semanticallyUniqueDocs.stream()
                .map(doc -> new ScoredDocument(doc, computeHybridScore(doc, query)))
                .sorted(Comparator.comparingDouble(ScoredDocument::getScore).reversed())
                .limit(topK)
                .map(ScoredDocument::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * 从 Document metadata 中提取归一化相似度分数。
     *
     * 说明：不同向量库对分数字段命名不同，
     *       - score（ES / Spring AI）：越大越相似，直接使用；
     *       - distance（Milvus L2 距离）：越小越相似，需要反转为 1/(1+distance)。
     */
    private double extractVectorScore(Document doc) {
        return Optional.ofNullable(doc.getMetadata())
                .map(this::doExtractVectorScore)
                .orElse(0.0);
    }

    private double doExtractVectorScore(Map<String, Object> metadata) {
        Object scoreObj = metadata.get("score");
        if (scoreObj != null) {
            return parseDoubleSafely(scoreObj.toString());
        }

        Object distanceObj = metadata.get("distance");
        if (distanceObj != null) {
            double distance = parseDoubleSafely(distanceObj.toString());
            return distance <= 0.0 ? 0.0 : 1.0 / (1.0 + distance);
        }

        return 0.0;
    }

    /**
     * 安全解析 Double，解析失败返回 0.0。
     */
    private double parseDoubleSafely(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 对文档内容做简单哈希，用于去重 key。
     */
    private String hashContent(String content) {
        return String.valueOf(Objects.hash(content.trim()));
    }

    /**
     * 计算混合分数：向量分数 * 权重 + 关键词匹配分数 * 权重。
     *
     * 为什么需要重排序？
     *   纯向量相似度可能无法捕捉 query 中的精确关键词，
     *   通过引入关键词命中次数作为辅助分数，可以提升包含关键实体的文档排名。
     *
     * @param doc   文档
     * @param query 用户问题
     * @return 混合分数
     */
    private double computeHybridScore(Document doc, String query) {
        double vectorScore = extractVectorScore(doc);
        // 关键词匹配：统计 query 中每个词在文档中的出现次数之和
        double keywordScore = computeKeywordScore(doc.getText(), query);

        // 归一化：向量分数通常已在 [0,1] 区间；关键词分数做简单压缩
        double normalizedKeywordScore = Math.min(keywordScore / 10.0, 1.0);

        // 若 rerankEnabled 为 false，则只使用向量分数
        return rerankEnabled
                ? vectorScore * 0.7 + normalizedKeywordScore * 0.3
                : vectorScore;
    }

    /**
     * 计算关键词匹配分数。
     *
     * 实现：将 query 按非单词字符拆分，统计每个词在文档内容中的出现次数。
     * 这里使用 JDK8 的 Pattern + Stream，简洁且避免引入额外分词库。
     */
    private double computeKeywordScore(String content, String query) {
        if (StringUtils.isBlank(content) || StringUtils.isBlank(query)) {
            return 0.0;
        }
        String lowerContent = content.toLowerCase();
        String[] keywords = Pattern.compile("\\W+")
                .splitAsStream(query.toLowerCase())
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);

        return java.util.Arrays.stream(keywords)
                .mapToDouble(keyword -> {
                    int count = 0;
                    int index = 0;
                    while ((index = lowerContent.indexOf(keyword, index)) != -1) {
                        count++;
                        index += keyword.length();
                    }
                    return count;
                })
                .sum();
    }

    /**
     * 将文档列表组装为上下文文本。
     */
    private String buildContextText(List<Document> documents) {
        if (documents.isEmpty()) {
            return "【检索上下文】\n未检索到相关内容。";
        }
        return documents.stream()
                .map(Document::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(
                        "\n---\n",
                        "【检索上下文】\n",
                        ""
                ));
    }

    /**
     * 将历史对话列表组装为历史文本。
     */
    private String buildHistoryText(List<String> history) {
        if (history.isEmpty()) {
            return "【历史对话】\n无。";
        }
        return history.stream()
                .collect(Collectors.joining("\n", "【历史对话】\n", ""));
    }

    /**
     * 组装最终 Prompt：上下文 + 历史 + 当前问题。
     */
    private String buildUserPrompt(String query, String contextText, String historyText) {
        return contextText + "\n\n" + historyText + "\n\n【用户问题】\n" + query;
    }

    /**
     * 异步保存本轮对话历史到 Redis。
     *
     * 说明：保存操作不阻塞流式响应，使用 CompletableFuture.runAsync 在 ragExecutor 中执行；
     *       同时设置 24 小时过期时间，避免历史数据无限增长。
     */
    private void saveHistoryAsync(String sessionId, String query, String answer) {
        if (StringUtils.isBlank(query) || StringUtils.isBlank(answer)) {
            return;
        }
        CompletableFuture.runAsync(
                MdcUtil.wrap(() -> {
                    String key = historyKeyPrefix + ":" + sessionId;
                    try {
                        redisTemplate.opsForList().rightPushAll(key,
                                "用户：" + query,
                                "助手：" + answer);
                        redisTemplate.expire(key, Duration.ofHours(24));
                        log.info("[RAG] 会话={}, 历史对话保存成功", sessionId);
                    } catch (Exception e) {
                        log.warn("[RAG] 会话={}, 保存历史对话异常", sessionId, e);
                    }
                }),
                ragExecutor
        );
    }

    /**
     * 内部数据载体：保存处理后的文档与历史对话。
     * 使用 Lombok @Value 替代 record，保持 JDK8 语法风格的同时保证不可变性。
     */
    @lombok.Value
    private static class RagContext {
        List<Document> documents;
        List<String> history;
    }

    /**
     * 内部数据载体：带混合分数的文档，用于重排序。
     */
    @lombok.Value
    private static class ScoredDocument {
        Document document;
        double score;
    }
}
