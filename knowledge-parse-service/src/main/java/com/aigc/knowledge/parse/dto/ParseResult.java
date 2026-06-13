package com.aigc.knowledge.parse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档解析结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    /**
     * 文件名。
     */
    private String fileName;

    /**
     * 文件类型：PDF / TXT。
     */
    private String fileType;

    /**
     * 原始文本内容。
     */
    private String rawText;

    /**
     * 清洗后的文本内容。
     */
    private String cleanedText;

    /**
     * 切片列表。
     */
    private List<DocumentChunk> chunks;

    /**
     * 解析是否成功。
     */
    private boolean success;

    /**
     * 错误信息（解析失败时）。
     */
    private String errorMsg;
}
