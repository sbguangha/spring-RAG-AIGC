package com.aigc.knowledge.parse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Embedding 模型配置。
 * <p>
 * Chat 仍可使用火山方舟 OpenAI 兼容接口；知识解析向量化单独使用百炼
 * OpenAI 兼容接口。使用 JDK HttpClient 替代默认 Jetty client，避免容器内
 * Jetty 与百炼 OpenAI 兼容接口的 401/协议异常问题。
 */
@Slf4j
@Configuration
public class EmbeddingModelConfig {

    /**
     * 连接超时：建立 TCP/TLS 连接的最大等待时间。
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 读取超时：等待响应体的最大时间。
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean("bailianEmbeddingModel")
    public EmbeddingModel bailianEmbeddingModel(
            @Value("${spring.ai.openai.embedding.base-url:${ALI_AI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}}") String baseUrl,
            @Value("${spring.ai.openai.embedding.api-key:${ALI_AI_API_KEY:}}") String apiKey,
            @Value("${spring.ai.openai.embedding.embeddings-path:${ALI_AI_EMBEDDINGS_PATH:/embeddings}}") String embeddingsPath,
            @Value("${spring.ai.openai.embedding.options.model:${OPENAI_EMBEDDING_MODEL:text-embedding-v4}}") String model) {

        log.info("初始化 Bailian EmbeddingModel, baseUrl={}, model={}, apiKeyLength={}",
                baseUrl, model, apiKey != null ? apiKey.length() : -1);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .embeddingsPath(embeddingsPath)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .dimensions(1024)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options) {
            /**
             * 显式返回维度，避免父类通过调用 embedding API 推断维度。
             * 在容器网络不稳定或百炼 API 对探测请求返回 401 时，
             * 可防止 MilvusVectorStore 初始化失败。
             */
            @Override
            public int dimensions() {
                return 1024;
            }
        };
    }
}
