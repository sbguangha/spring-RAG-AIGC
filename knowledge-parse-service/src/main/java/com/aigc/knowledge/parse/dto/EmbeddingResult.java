package com.aigc.knowledge.parse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 批量处理结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResult {

    /**
     * 对应切片的索引。
     */
    private int chunkIndex;

    /**
     * 原始文本。
     */
    private String text;

    /**
     * Embedding 向量。
     */
    private List<Float> embedding;

    /**
     * 是否成功。
     */
    private boolean success;

    /**
     * 错误信息。
     */
    private String errorMsg;
}
