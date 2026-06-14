package com.aigc.knowledge.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 知识解析服务启动类
 * 职责：文档解析、切片、向量化、索引构建
 */
@Slf4j
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication(excludeName = {
        "org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration"
})
public class KnowledgeParseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeParseServiceApplication.class, args);

        // 启动时打印banner/版本信息，演示 JDK8 Stream + Lambda + Optional
        List<String> modules = IntStream.rangeClosed(1, 3)
                .boxed()
                .map(idx -> "KnowledgeParseModule-" + idx)
                .collect(Collectors.toList());

        String firstModule = Optional.ofNullable(modules)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse("default-module");

        log.info("知识解析服务启动成功，首个模块: {}", firstModule);
    }

    /**
     * 文档解析专用线程池：CompletableFuture 执行自定义线程池
     */
    @Bean("parseTaskExecutor")
    public Executor parseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("parse-async-");
        // 拒绝策略：由调用线程执行，防止解析任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 向量化专用线程池：Milvus/ES 写入并发控制
     */
    @Bean("vectorizeTaskExecutor")
    public Executor vectorizeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("vectorize-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
