package com.aigc.orchestration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * AI 编排服务启动类
 * 职责：RAG 检索、Prompt 编排、模型路由、结果融合
 */
@Slf4j
@EnableAsync
@EnableFeignClients(basePackages = "com.aigc.orchestration.client")
@EnableDiscoveryClient
@SpringBootApplication
public class AiOrchestrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOrchestrationServiceApplication.class, args);

        // 演示 Lambda + Stream + Optional 组合使用
        List<String> downstreamServices = Arrays.asList(
                "knowledge-parse-service",
                "content-gen-service"
        );

        String joinedServices = downstreamServices.stream()
                .filter(s -> s.contains("service"))
                .map(String::toUpperCase)
                .collect(Collectors.joining(", "));

        Optional.of(joinedServices)
                .filter(s -> !s.isEmpty())
                .ifPresent(s -> log.info("AI 编排服务已注册下游服务: {}", s));

        log.info("AI 编排服务启动成功");
    }

    /**
     * 检索编排线程池：并发查询 Milvus + Elasticsearch
     */
    @Bean("retrievalTaskExecutor")
    public Executor retrievalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(24);
        executor.setQueueCapacity(300);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("retrieval-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 模型调用线程池：多模型并发调用/结果融合
     */
    @Bean("modelCallTaskExecutor")
    public Executor modelCallTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("model-call-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
