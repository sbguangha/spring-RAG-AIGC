package com.aigc.content.gen.service;

import com.aigc.content.gen.constant.ImageTaskConstants;
import com.aigc.content.gen.dto.ImageTaskMessage;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文生图任务消费者。
 *
 * 设计思路：
 *   1. 应用启动后开启独立后台线程池，专门监听 Redis 任务队列；
 *   2. 使用 BRPOP（阻塞弹出）语义，没有任务时线程挂起，避免空轮询浪费 CPU；
 *   3. 每个任务在独立线程中执行生图 API 调用，单个任务失败不会阻塞其他任务；
 *   4. Lambda 表达式封装异常处理，失败时将状态更新为 FAILED 并记录错误；
 *   5. 成功后将图片 URL 写入 Redis Hash，状态更新为 SUCCESS。
 *
 * 线程池设计：
 *   - 固定大小线程池，大小建议与生图 API 并发配额匹配（默认 4）；
 *   - 线程名前缀 image-consumer-，便于监控和日志排查。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageTaskConsumer {

    private final StringRedisTemplate redisTemplate;

    /**
     * 注入 DashScope 图像模型（通义万相）。
     * 在 Spring AI Alibaba 1.0.0.4 中，dashscope starter 会提供 dashScopeImageModel Bean。
     */
    @Qualifier("dashScopeImageModel")
    private final ImageModel imageModel;

    @Value("${content.image-gen.task-queue-key:image:gen:queue}")
    private String taskQueueKey;

    @Value("${content.image-gen.result-key-prefix:image:gen:result}")
    private String resultKeyPrefix;

    @Value("${content.image-gen.poll-timeout-seconds:5}")
    private long pollTimeoutSeconds;

    @Value("${content.image-gen.poll-interval-ms:1000}")
    private long pollIntervalMs;

    /**
     * 消费者线程池。
     */
    private ExecutorService consumerExecutor;

    /**
     * 停止标志，用于优雅关闭。
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 消费者工作线程数量，与生图 API 并发能力匹配。
     */
    @Value("${content.image-gen.consumer-threads:4}")
    private int consumerThreads;

    /**
     * 应用启动后初始化消费者线程池并开始监听队列。
     */
    @PostConstruct
    public void start() {
        running.set(true);
        consumerExecutor = Executors.newFixedThreadPool(consumerThreads, r -> {
            Thread t = new Thread(r);
            t.setName("image-consumer-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < consumerThreads; i++) {
            consumerExecutor.submit(this::consumeLoop);
        }

        log.info("[ImageGen] 消费者启动，线程数={}", consumerThreads);
    }

    /**
     * 应用关闭前优雅停止消费者线程池。
     */
    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
            try {
                if (!consumerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("[ImageGen] 消费者线程池未在 10 秒内完全关闭");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[ImageGen] 关闭消费者线程池时被中断", e);
            }
        }
    }

    /**
     * 消费者主循环：阻塞监听 Redis List，取到任务后调用 processTask。
     */
    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // BRPOP 语义：从队列右侧阻塞弹出任务，超时时间为 pollTimeoutSeconds。
                // 超时后会返回 null，进入下一次循环，同时检查 running 状态。
                String messageJson = redisTemplate.opsForList()
                        .rightPop(taskQueueKey, pollTimeoutSeconds, TimeUnit.SECONDS);

                if (messageJson == null || messageJson.isBlank()) {
                    continue;
                }

                ImageTaskMessage task = JSON.parseObject(messageJson, ImageTaskMessage.class);
                if (task == null || task.getTaskId() == null) {
                    log.warn("[ImageGen] 消费到非法任务消息: {}", messageJson);
                    continue;
                }

                // 使用 Lambda 包装任务处理，确保异常被捕获且不扩散到循环层
                consumerExecutor.submit(() -> {
                    try {
                        processTask(task);
                    } catch (Exception e) {
                        log.error("[ImageGen] 任务处理异常, taskId={}", task.getTaskId(), e);
                        markFailed(task.getTaskId(), "消费者内部异常: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                log.error("[ImageGen] 消费循环异常", e);
                // 非阻塞兜底：出现异常后短暂休眠，避免无限快速重试打满日志
                sleepQuietly(pollIntervalMs);
            }
        }
    }

    /**
     * 执行实际的 AI 生图调用。
     *
     * @param task 任务消息
     */
    private void processTask(ImageTaskMessage task) {
        String taskId = task.getTaskId();
        String prompt = task.getPrompt();

        log.info("[ImageGen] 开始生图, taskId={}, prompt={}", taskId, prompt);

        // 更新状态为生成中
        updateStatus(taskId, ImageTaskConstants.STATUS_GENERATING, null, null);

        try {
            // 构建生图 Prompt 和 Options
            ImageOptions options = ImageOptionsBuilder.builder()
                    .width(1024)
                    .height(1024)
                    .build();

            ImagePrompt imagePrompt = new ImagePrompt(prompt, options);

            // 调用大模型生图 API（通义万相 / Gemini 等，由 ImageModel 实现决定）
            ImageResponse response = imageModel.call(imagePrompt);

            // Lambda 提取图片 URL：对空结果做防御性处理
            String imageUrl = Optional.ofNullable(response)
                    .map(r -> r.getResult())
                    .map(gen -> gen.getOutput())
                    .map(img -> img.getUrl())
                    .filter(url -> url != null && !url.isBlank())
                    .orElseThrow(() -> new RuntimeException("生图 API 未返回有效图片 URL"));

            markSuccess(taskId, imageUrl);
            log.info("[ImageGen] 生图成功, taskId={}, imageUrl={}", taskId, imageUrl);

        } catch (Exception e) {
            log.error("[ImageGen] 生图失败, taskId={}", taskId, e);
            markFailed(taskId, "生图 API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 标记任务成功，写入图片 URL。
     */
    private void markSuccess(String taskId, String imageUrl) {
        updateStatus(taskId, ImageTaskConstants.STATUS_SUCCESS, imageUrl, null);
    }

    /**
     * 标记任务失败，写入错误信息。
     */
    private void markFailed(String taskId, String errorMsg) {
        updateStatus(taskId, ImageTaskConstants.STATUS_FAILED, null, errorMsg);
    }

    /**
     * 更新 Redis Hash 中的任务状态。
     *
     * @param taskId   任务 ID
     * @param status   状态
     * @param imageUrl 图片 URL（失败时为 null）
     * @param errorMsg 错误信息（成功时为 null）
     */
    private void updateStatus(String taskId, String status, String imageUrl, String errorMsg) {
        try {
            String key = resultKeyPrefix + ":" + taskId;
            Map<String, String> updates = Map.of(
                    ImageTaskConstants.FIELD_STATUS, status,
                    ImageTaskConstants.FIELD_IMAGE_URL, imageUrl == null ? "" : imageUrl,
                    ImageTaskConstants.FIELD_ERROR_MSG, errorMsg == null ? "" : errorMsg,
                    ImageTaskConstants.FIELD_FINISH_TIME, String.valueOf(System.currentTimeMillis())
            );
            redisTemplate.opsForHash().putAll(key, updates);
            redisTemplate.expire(key, Duration.ofHours(24));
        } catch (Exception e) {
            log.error("[ImageGen] 更新任务状态异常, taskId={}, status={}", taskId, status, e);
        }
    }

    /**
     * 静默休眠，不抛出中断异常。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
