package com.aigc.content.gen.service;

import com.aigc.content.gen.constant.ImageTaskConstants;
import com.aigc.content.gen.dto.ImageTaskMessage;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文生图任务提交服务。
 *
 * 设计思路：
 *   大模型生图 API 耗时较长（5-15 秒），如果同步等待会阻塞 HTTP 线程、占用连接池，
 *   并容易触发网关/客户端超时。因此采用"异步任务"模式：
 *     1. 接收 Prompt 后生成 taskId；
 *     2. 将任务元数据写入 Redis Hash（状态=PENDING）；
 *     3. 将任务消息推入 Redis List 队列；
 *     4. 立即返回 taskId，调用方通过轮询接口查询结果。
 *
 * 为什么使用 Redis List 而不是 RabbitMQ？
 *   - 技术栈已包含 Redis，无需额外部署 RabbitMQ；
 *   - Redis List 的 LPUSH/BRPOP 语义足以支撑轻量级任务队列；
 *   - 对于面试演示场景，减少中间件依赖更便于快速启动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final StringRedisTemplate redisTemplate;

    @Value("${content.image-gen.task-queue-key:image:gen:queue}")
    private String taskQueueKey;

    @Value("${content.image-gen.result-key-prefix:image:gen:result}")
    private String resultKeyPrefix;

    /**
     * 提交生图任务。
     *
     * @param prompt 用户生图 Prompt
     * @return 任务 ID
     */
    public String submitTask(String prompt) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        // 1. 初始化任务状态到 Redis Hash
        Map<String, String> taskMeta = new HashMap<>();
        taskMeta.put(ImageTaskConstants.FIELD_STATUS, ImageTaskConstants.STATUS_PENDING);
        taskMeta.put(ImageTaskConstants.FIELD_PROMPT, prompt);
        taskMeta.put(ImageTaskConstants.FIELD_CREATE_TIME, String.valueOf(now));
        redisTemplate.opsForHash().putAll(buildResultKey(taskId), taskMeta);
        // 设置结果 Hash 24 小时过期，避免长期堆积
        redisTemplate.expire(buildResultKey(taskId), Duration.ofHours(24));

        // 2. 构建任务消息并推入队列（LPUSH 入队，消费者 BRPOP 出队）
        ImageTaskMessage message = ImageTaskMessage.builder()
                .taskId(taskId)
                .prompt(prompt)
                .createTime(now)
                .build();
        redisTemplate.opsForList().leftPush(taskQueueKey, JSON.toJSONString(message));

        log.info("[ImageGen] 任务已提交, taskId={}, prompt={}", taskId, prompt);
        return taskId;
    }

    /**
     * 构建任务结果 Redis Hash Key。
     */
    public String buildResultKey(String taskId) {
        return resultKeyPrefix + ":" + taskId;
    }
}
