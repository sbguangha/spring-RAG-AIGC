# RAG 语义去重与向量库入库改造文档

## 1. 背景与目标

### 1.1 背景

当前 `ai-orchestration-service` 的 RAG 流程已实现：

- 并发检索 Milvus + Elasticsearch
- 历史上下文拼接
- 流式调用豆包/DeepSeek

但存在两个关键缺陷：

1. **去重逻辑太初级**：仅用 `Objects.hash(text)` 判断内容是否完全相同，无法识别"意思一样、表述不同"的语义重复。
2. **缺少数据写入链路**：`knowledge-parse-service` 只解析文档、生成切片和 Embedding，但没有把结果写入 Milvus/Elasticsearch，导致检索端永远召回不到用户资料。

### 1.2 目标

1. 在 `ai-orchestration-service` 引入基于 `doubao-embedding-large` 的语义去重。
2. 修复 Milvus `distance` 与 Elasticsearch `score` 语义不一致的问题。
3. 在 `knowledge-parse-service` 补充文档切片写入 Milvus/Elasticsearch 的链路。
4. 保证整体流程可本地运行、可测试。

---

## 2. 现状分析

### 2.1 当前去重代码

文件：`ai-orchestration-service/src/main/java/com/aigc/orchestration/service/ConcurrentRagService.java`

```java
private List<Document> deduplicateByContent(List<Document> documents) {
    return documents.stream()
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
```

问题：

- 只能去完全重复的文本。
- 相同语义不同表述会被认为不同。
- `extractVectorScore` 没有区分 `score` 和 `distance`，把 Milvus 的 L2 距离当成越大越好，可能导致选择错误。

### 2.2 当前分数提取代码

```java
private double extractVectorScore(Document doc) {
    return Optional.ofNullable(doc.getMetadata())
            .map(metadata -> metadata.get("score"))
            .map(Object::toString)
            .map(this::parseDoubleSafely)
            .or(() -> Optional.ofNullable(doc.getMetadata())
                    .map(metadata -> metadata.get("distance"))
                    .map(Object::toString)
                    .map(this::parseDoubleSafely))
            .orElse(0.0);
}
```

问题：

- `distance` 越小越相似，但代码当成越大越好。
- 没有统一归一化。

### 2.3 当前向量库写入现状

`knowledge-parse-service` 的 `DocumentParserService.parse()` 可以生成 Embedding，但只回填到 `Chunk` 对象，没有写入任何 VectorStore。

`ai-orchestration-service` 依赖 `milvusVectorStore` 和 `elasticsearchVectorStore` Bean，但项目里**没有配置类声明这两个 Bean**，启动会失败或无法注入。

---

## 3. 改造方案

### 3.1 整体架构

```
用户上传文档
    |
    v
knowledge-parse-service: 解析 -> 清洗 -> 切片 -> Embedding -> 写入 Milvus + ES
    |
    v
ai-orchestration-service: 并发检索 Milvus + ES -> 规则去重 -> 语义去重 -> 重排序 -> 拼 Prompt -> 生成
```

### 3.2 改造模块

| 服务 | 改动点 |
|------|--------|
| `knowledge-parse-service` | 1. 新增 `VectorStoreWriter` 写入 Milvus/ES<br>2. `DocumentParserController` 新增 `parseAndIndex` 接口<br>3. 配置 `MilvusVectorStore`、`ElasticsearchVectorStore` Bean |
| `ai-orchestration-service` | 1. 新增 `SemanticDeduplicator` 语义去重<br>2. 修复 `extractVectorScore` score/distance 处理<br>3. 在 `processDocuments` 中接入语义去重<br>4. 配置 `MilvusVectorStore`、`ElasticsearchVectorStore` Bean |

---

## 4. 具体实现

### 4.1 配置 Milvus/ES VectorStore Bean

#### 4.1.1 `knowledge-parse-service` 新增配置类

文件：`knowledge-parse-service/src/main/java/com/aigc/knowledge/parse/config/VectorStoreConfig.java`

```java
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore milvusVectorStore(MilvusServiceClient milvusClient,
                                         EmbeddingModel embeddingModel,
                                         @Value("${vector.milvus.collection-name:knowledge_chunks}") String collectionName,
                                         @Value("${vector.milvus.dimension:1536}") int dimension,
                                         @Value("${vector.milvus.database-name:aigc_knowledge}") String databaseName) {
        // 初始化 collection（不存在则创建）
        ensureCollection(milvusClient, databaseName, collectionName, dimension);

        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .embeddingDimension(dimension)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .build();
    }

    @Bean
    public VectorStore elasticsearchVectorStore(RestClient restClient,
                                                EmbeddingModel embeddingModel,
                                                @Value("${vector.elasticsearch.index-prefix:aigc}") String indexPrefix) {
        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .indexName(indexPrefix + "_knowledge_chunks")
                .similarity(SimilarityFunction.COSINE)
                .initializeSchema(true)
                .build();
    }

    private void ensureCollection(MilvusServiceClient client, String dbName, String collectionName, int dimension) {
        // 具体实现：检查 collection 是否存在，不存在则创建
        // 字段：id(pk, VARCHAR), content, metadata(JSON), embedding(FLOAT_VECTOR)
        // 参考 Milvus Java SDK 文档
    }
}
```

注意：需要引入 `spring-ai-milvus-store` / `spring-ai-elasticsearch-store` 的正确自动配置依赖。当前 `pom.xml` 引的是 `spring-ai-starter-vector-store-milvus`，应能自动装配，但建议先检查版本兼容性。

#### 4.1.2 `ai-orchestration-service` 新增配置类

文件：`ai-orchestration-service/src/main/java/com/aigc/orchestration/config/VectorStoreConfig.java`

内容与上面类似，但只读不写，不需要 `ensureCollection`。

如果 Spring AI starter 能自动配置，可以省略手动 Bean；但当前项目没有显式配置，需要补上以保证注入成功。

---

### 4.2 `knowledge-parse-service` 写入向量库

#### 4.2.1 新增 `VectorStoreWriter`

文件：`knowledge-parse-service/src/main/java/com/aigc/knowledge/parse/service/VectorStoreWriter.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreWriter {

    private final VectorStore milvusVectorStore;
    private final VectorStore elasticsearchVectorStore;

    public void writeChunks(List<Chunk> chunks, String fileName) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<Document> documents = chunks.stream()
            .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
            .map(chunk -> {
                Map<String, Object> metadata = new HashMap<>(Optional.ofNullable(chunk.getMetadata()).orElse(Collections.emptyMap()));
                metadata.put("source", fileName);
                metadata.put("chunkIndex", chunk.getIndex());
                metadata.put("summary", chunk.getSummary());
                return new Document(chunk.getContent(), metadata);
            })
            .collect(Collectors.toList());

        if (documents.isEmpty()) {
            log.warn("没有可写入向量库的切片, fileName={}", fileName);
            return;
        }

        // 双写
        try {
            milvusVectorStore.add(documents);
            log.info("写入 Milvus 成功, fileName={}, count={}", fileName, documents.size());
        } catch (Exception e) {
            log.error("写入 Milvus 失败, fileName={}", fileName, e);
        }

        try {
            elasticsearchVectorStore.add(documents);
            log.info("写入 ES 成功, fileName={}, count={}", fileName, documents.size());
        } catch (Exception e) {
            log.error("写入 ES 失败, fileName={}", fileName, e);
        }
    }
}
```

注意：`Spring AI` 的 `VectorStore.add()` 会自己调用 EmbeddingModel 对 `Document` 编码。如果你已经在 `DocumentParserService` 里生成了 Embedding，这里会重复调用一次。有两种处理方式：

- **方式 A（推荐）**：解析时不再生成 Embedding，入库时由 `VectorStore` 统一生成。
- **方式 B**：自定义 VectorStore 实现，直接接收预计算 Embedding。

考虑到简单性，建议**方式 A**：`withEmbedding` 参数可以废弃或改为 `withIndex`（是否同时入库）。

#### 4.2.2 修改 `DocumentParserController`

新增接口：

```java
@PostMapping("/parse-and-index")
public ResponseEntity<ParseResult> parseAndIndex(
        @RequestParam("file") MultipartFile file) {
    ParseResult result = documentParserService.parse(file, false);
    vectorStoreWriter.writeChunks(result.getChunks(), result.getFileName());
    return ResponseEntity.ok(result);
}
```

或者直接在 `parse` 接口增加 `withIndex` 参数。

#### 4.2.3 修改 `DocumentParserService`

建议把 `withEmbedding` 参数改为控制是否入库，或者新增 `withIndex`。保持 `EmbeddingClient` 仍可独立使用，供测试场景。

---

### 4.3 `ai-orchestration-service` 语义去重

#### 4.3.1 新增 `SemanticDeduplicator`

文件：`ai-orchestration-service/src/main/java/com/aigc/orchestration/service/SemanticDeduplicator.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticDeduplicator {

    private final EmbeddingModel embeddingModel;

    @Value("${rag.semantic-dedup.threshold:0.92}")
    private double similarityThreshold;

    /**
     * 对文档列表进行语义去重。
     * 策略：保留向量分数最高的文档，与其语义相似度超过阈值的文档被移除。
     */
    public List<Document> deduplicate(List<Document> documents) {
        if (documents == null || documents.size() <= 1) {
            return documents;
        }

        List<Document> sorted = documents.stream()
            .sorted(Comparator.comparingDouble(this::extractNormalizedScore).reversed())
            .collect(Collectors.toList());

        List<float[]> embeddings = sorted.stream()
            .map(doc -> embed(doc.getText()))
            .collect(Collectors.toList());

        List<Document> result = new ArrayList<>();
        boolean[] removed = new boolean[sorted.size()];

        for (int i = 0; i < sorted.size(); i++) {
            if (removed[i]) continue;

            Document keeper = sorted.get(i);
            result.add(keeper);

            for (int j = i + 1; j < sorted.size(); j++) {
                if (!removed[j]) {
                    double sim = cosineSimilarity(embeddings.get(i), embeddings.get(j));
                    if (sim >= similarityThreshold) {
                        removed[j] = true;
                        log.debug("语义去重移除文档, sim={}, keeper={}, removed={}",
                                sim, keeper.getText().substring(0, 30), sorted.get(j).getText().substring(0, 30));
                    }
                }
            }
        }

        return result;
    }

    private float[] embed(String text) {
        try {
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
            return Optional.ofNullable(response.getResults())
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0).getOutput())
                    .map(this::copy)
                    .orElse(new float[0]);
        } catch (Exception e) {
            log.error("Embedding 调用失败, text={}", text.substring(0, 50), e);
            return new float[0];
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double extractNormalizedScore(Document doc) {
        // 见 4.3.2
        return 0.0;
    }

    private float[] copy(float[] arr) {
        return Arrays.copyOf(arr, arr.length);
    }
}
```

#### 4.3.2 修复分数提取逻辑

修改 `ConcurrentRagService.extractVectorScore`：

```java
private double extractVectorScore(Document doc) {
    Map<String, Object> metadata = doc.getMetadata();
    if (metadata == null) return 0.0;

    Object scoreObj = metadata.get("score");
    Object distanceObj = metadata.get("distance");

    if (scoreObj != null) {
        // ES / Spring AI score: 越大越相似
        return parseDoubleSafely(scoreObj.toString());
    }

    if (distanceObj != null) {
        // Milvus L2 distance: 越小越相似，需要反转
        double distance = parseDoubleSafely(distanceObj.toString());
        return distance <= 0 ? 0.0 : 1.0 / (1.0 + distance);
    }

    return 0.0;
}
```

同时新增 `extractNormalizedScore` 给 `SemanticDeduplicator` 使用，逻辑一致。

#### 4.3.3 修改 `processDocuments` 流程

```java
private List<Document> processDocuments(List<Document> documents, String query) {
    if (documents == null || documents.isEmpty()) {
        return Collections.emptyList();
    }

    // 1. 防御性过滤
    List<Document> filteredDocs = documents.stream()
        .filter(doc -> doc != null && StringUtils.isNotBlank(doc.getText()))
        .filter(doc -> extractVectorScore(doc) >= scoreThreshold)
        .collect(Collectors.toList());

    // 2. 完全重复去重（保留现有）
    List<Document> uniqueDocs = deduplicateByContent(filteredDocs);

    // 3. 语义去重（新增）
    List<Document> semanticallyUniqueDocs = semanticDeduplicator.deduplicate(uniqueDocs);

    // 4. 重排序并截断 TopK
    return semanticallyUniqueDocs.stream()
        .map(doc -> new ScoredDocument(doc, computeHybridScore(doc, query)))
        .sorted(Comparator.comparingDouble(ScoredDocument::getScore).reversed())
        .limit(topK)
        .map(ScoredDocument::getDocument)
        .collect(Collectors.toList());
}
```

---

### 4.4 配置更新

#### 4.4.1 `ai-orchestration-service/application.yml`

新增：

```yaml
rag:
  semantic-dedup:
    enabled: true
    threshold: 0.92
```

#### 4.4.2 `knowledge-parse-service/application.yml`

确认已有：

```yaml
vector:
  milvus:
    host: ${MILVUS_HOST:127.0.0.1}
    port: ${MILVUS_PORT:19530}
    database-name: aigc_knowledge
    collection-name: knowledge_chunks
    dimension: 1536
  elasticsearch:
    hosts: ${ES_HOSTS:http://127.0.0.1:9200}
```

如需使用 Spring AI 自动配置，需要补充 Spring AI 官方 starter 的 `spring.ai.vectorstore.milvus.*` / `spring.ai.vectorstore.elasticsearch.*` 属性。参考 Spring AI 文档。

---

## 5. 测试方案

### 5.1 单元测试

#### `SemanticDeduplicatorTest`

构造三篇文档：

- A："Spring Boot 是一个用于构建微服务的 Java 框架"
- B："Spring Boot 是 Java 微服务开发框架"（语义相似）
- C："Redis 是一个内存数据库"（语义不同）

断言：结果中 A/C 保留，B 被移除。

#### `VectorStoreWriterTest`

Mock `VectorStore`，验证 `add()` 被调用，且 Document 的 metadata 包含 source/chunkIndex。

#### `ConcurrentRagServiceTest`

Mock `VectorStore`、`ChatClient`、`RedisTemplate`，验证：
- 返回空文档时不报错
- score/distance 归一化正确
- 语义去重被调用

### 5.2 集成测试

1. 确保本地 Milvus 已启动（用户已确认）。
2. 调用 `POST /api/document/parse-and-index` 上传 `test.txt`。
3. 观察日志：写入 Milvus/ES 成功。
4. 调用 `ai-orchestration-service` 的 RAG 接口（需要先确认暴露的 HTTP 入口，当前代码只有 `streamChat` service 方法，没有 Controller）。
5. 验证能召回资料并生成基于上下文的回答。

---

## 6. 风险与注意事项

1. **Milvus collection 初始化**：第一次写入前必须创建 collection。建议通过 `MilvusVectorStore` 的 `initializeSchema=true` 或手动 `ensureCollection` 处理。
2. **Embedding 重复调用**：`VectorStore.add()` 内部会重新 embedding。如果文档已经生成过 embedding，会造成浪费。建议解析和入库分开，或自定义 VectorStore。
3. **ES 版本**：当前 `elasticsearch-java` 版本 `8.14.3`，需要本地 ES 也是 8.x。
4. **Nacos**：本地测试时可以用 `application-local.yml` 禁用 Nacos，避免依赖。
5. **RAG 接口缺失**：`ConcurrentRagService` 只有 service 层，需要补充 Controller 和 DTO 才能 HTTP 测试。

---

## 7. 任务拆分建议

交给另一个程序员时可以拆成 4 个独立任务：

| 任务 | 文件 | 说明 |
|------|------|------|
| 任务 1：配置 VectorStore Bean | 两个服务的 `VectorStoreConfig.java` | 先让 Milvus/ES 能注入成功 |
| 任务 2：写入向量库 | `VectorStoreWriter.java` + Controller | 实现 parse-and-index 接口 |
| 任务 3：语义去重 | `SemanticDeduplicator.java` | 单独实现并加单测 |
| 任务 4：接入 RAG 流程 | `ConcurrentRagService.java` | 接入语义去重 + 修复分数逻辑 |
