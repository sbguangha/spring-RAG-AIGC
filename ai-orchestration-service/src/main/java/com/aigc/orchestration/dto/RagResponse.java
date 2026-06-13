package com.aigc.orchestration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 非流式对话响应（备用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 完整回答文本
     */
    private String answer;
}
