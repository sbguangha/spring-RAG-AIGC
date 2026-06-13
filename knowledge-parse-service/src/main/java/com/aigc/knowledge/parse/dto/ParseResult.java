package com.aigc.knowledge.parse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档解析结果。
 * <p>
 * 包含原始文件元信息与解析后得到的全部 Chunk 列表，
 * 用于控制器返回给调用方。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件 MIME 类型，例如 application/pdf、text/plain
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private long fileSize;

    /**
     * 文档总字符数
     */
    private int totalChars;

    /**
     * 解析生成的切片总数
     */
    private int chunkCount;

    /**
     * 解析后的语义切片列表
     */
    private List<Chunk> chunks;

    /**
     * 解析耗时（毫秒）
     */
    private long elapsedMillis;

    /**
     * 解析状态：SUCCESS / PARTIAL / FAILED
     */
    private String status;

    /**
     * 失败时的简要错误信息
     */
    private String errorMessage;
}
