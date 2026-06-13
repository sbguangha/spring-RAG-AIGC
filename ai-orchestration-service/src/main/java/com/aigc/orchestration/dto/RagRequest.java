package com.aigc.orchestration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 流式对话请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRequest {

    /**
     * 用户当前问题
     */
    private String query;

    /**
     * 会话 ID，可选；为空时由服务端生成
     */
    private String sessionId;
}
