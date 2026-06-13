package com.aigc.orchestration.controller;

import com.aigc.orchestration.dto.RagRequest;
import com.aigc.orchestration.service.ConcurrentRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * RAG 对话控制器。
 * <p>
 * 提供流式 RAG 查询接口，返回文本流。
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final ConcurrentRagService concurrentRagService;

    /**
     * 流式 RAG 对话。
     *
     * @param request 包含 query 和可选 sessionId
     * @return 流式生成的文本
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Flux<String> streamChat(@RequestBody RagRequest request) {
        String query = StringUtils.trim(request.getQuery());
        if (StringUtils.isBlank(query)) {
            return Flux.just("[系统提示] 问题不能为空。");
        }

        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(),
                UUID.randomUUID().toString().replace("-", ""));

        log.info("[RAG] 收到流式请求, sessionId={}, query={}", sessionId, query);
        return concurrentRagService.streamChat(query);
    }
}
