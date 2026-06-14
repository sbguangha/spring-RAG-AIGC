package com.aigc.content.gen.controller;

import com.aigc.content.gen.constant.ImageTaskConstants;
import com.aigc.content.gen.dto.ImageGenerationRequest;
import com.aigc.content.gen.dto.ImageTaskResponse;
import com.aigc.content.gen.service.ImageGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 文生图任务 REST 接口。
 *
 * 提供两个核心接口：
 *   1. POST /api/image/generate：提交生图任务，立即返回 taskId；
 *   2. GET /api/image/status/{taskId}：查询任务状态，使用 Optional 优雅处理任务不存在/未完成。
 */
@Slf4j
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageTaskController {

    private final ImageGenerationService imageGenerationService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 提交生图任务。
     *
     * 设计要点：
     *   - 校验 Prompt 非空；
     *   - 提交到 Redis 队列后立即返回 taskId，不等待 AI 生图完成；
     *   - HTTP 响应时间短，避免阻塞连接池和触发网关超时。
     *
     * @param request 生图请求
     * @return taskId
     */
    @PostMapping("/generate")
    public ImageTaskResponse generateImage(@Valid @RequestBody ImageGenerationRequest request) {
        String taskId = imageGenerationService.submitTask(request.getPrompt());
        return ImageTaskResponse.builder()
                .taskId(taskId)
                .status(ImageTaskConstants.STATUS_PENDING)
                .prompt(request.getPrompt())
                .build();
    }

    /**
     * 查询生图任务状态。
     *
     * JDK8 Optional 应用：
     *   - Optional.ofNullable 包装可能为 null 的 Redis Hash 结果；
     *   - map 转换字段值；
     *   - orElseGet 在任务不存在时返回 404 友好的响应。
     *
     * @param taskId 任务 ID
     * @return 任务状态响应
     */
    @GetMapping("/status/{taskId}")
    public ImageTaskResponse getStatus(@PathVariable("taskId") String taskId) {
        String key = imageGenerationService.buildResultKey(taskId);

        // 从 Redis Hash 读取任务状态，使用 Optional 处理 null/不存在的情况
        return Optional.ofNullable(redisTemplate.<String, String>opsForHash().entries(key))
                .filter(entries -> !entries.isEmpty())
                .map(entries -> ImageTaskResponse.builder()
                        .taskId(taskId)
                        .status(entries.get(ImageTaskConstants.FIELD_STATUS))
                        .imageUrl(entries.get(ImageTaskConstants.FIELD_IMAGE_URL))
                        .errorMsg(entries.get(ImageTaskConstants.FIELD_ERROR_MSG))
                        .prompt(entries.get(ImageTaskConstants.FIELD_PROMPT))
                        .createTime(parseLong(entries.get(ImageTaskConstants.FIELD_CREATE_TIME)))
                        .finishTime(parseLong(entries.get(ImageTaskConstants.FIELD_FINISH_TIME)))
                        .build())
                .orElseGet(() -> {
                    log.warn("[ImageGen] 查询任务不存在, taskId={}", taskId);
                    return ImageTaskResponse.builder()
                            .taskId(taskId)
                            .status("NOT_FOUND")
                            .errorMsg("任务不存在或已过期")
                            .build();
                });
    }

    /**
     * 安全解析 Long，解析失败返回 null。
     */
    private Long parseLong(String value) {
        return Optional.ofNullable(value)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .orElse(null);
    }
}
