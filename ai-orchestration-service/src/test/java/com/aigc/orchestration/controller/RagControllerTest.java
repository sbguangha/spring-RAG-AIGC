package com.aigc.orchestration.controller;

import com.aigc.orchestration.dto.RagRequest;
import com.aigc.orchestration.service.ConcurrentRagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import org.springframework.test.context.TestPropertySource;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * RagController 单元测试。
 */
@WebFluxTest(RagController.class)
@TestPropertySource(properties = "spring.cloud.nacos.config.import-check.enabled=false")
class RagControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ConcurrentRagService concurrentRagService;

    @Test
    @DisplayName("空问题应返回提示信息")
    void streamChat_withBlankQuery_shouldReturnHint() {
        RagRequest request = new RagRequest();
        request.setQuery("   ");

        webTestClient.post()
                .uri("/api/rag/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("[系统提示] 问题不能为空。");
    }

    @Test
    @DisplayName("正常请求应返回流式内容")
    void streamChat_withValidQuery_shouldReturnStream() {
        when(concurrentRagService.streamChat(anyString()))
                .thenReturn(Flux.just("你好", "，", "世界"));

        RagRequest request = new RagRequest();
        request.setQuery("你好");

        webTestClient.post()
                .uri("/api/rag/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("你好，世界");
    }
}
