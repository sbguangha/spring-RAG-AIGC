package com.aigc.content.gen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 内容生成服务启动类
 * 职责：多模态文案、图片、视频脚本生成
 */
@Slf4j
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication
public class ContentGenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentGenServiceApplication.class, args);

        // 演示 Stream + Lambda + Optional：初始化内容生成类型映射
        Map<String, String> genTypeMap = Stream.of("text", "image", "video-script")
                .collect(Collectors.toMap(
                        type -> type,
                        type -> type.toUpperCase() + "_GEN"
                ));

        String defaultType = Optional.ofNullable(genTypeMap.get("text"))
                .orElse("UNKNOWN");

        log.info("内容生成服务启动成功，默认生成类型: {}", defaultType);
    }

    /**
     * 文本生成线程池：用于 CompletableFuture 并行生成多版本文案
     */
    @Bean("textGenTaskExecutor")
    public Executor textGenTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(400);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("text-gen-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 图片生成线程池：多模态图片生成任务隔离
     */
    @Bean("imageGenTaskExecutor")
    public Executor imageGenTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("image-gen-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
