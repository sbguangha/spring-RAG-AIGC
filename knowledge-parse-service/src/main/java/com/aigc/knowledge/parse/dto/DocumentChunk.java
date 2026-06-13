package com.aigc.knowledge.parse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档切片（Chunk）DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /**
     * 切片在文档中的序号。
     */
    private int index;

    /**
     * 切片标题（如果基于标题切分）。
     */
    private String title;

    /**
     * 切片正文内容。
     */
    private String content;

    /**
     * 清洗后的文本（可选）。
     */
    private String cleanedContent;

    /**
     * 摘要（可选）。
     */
    private String summary;

    /**
     * 字符起始位置。
     */
    private int startPos;

    /**
     * 字符结束位置。
     */
    private int endPos;
}
