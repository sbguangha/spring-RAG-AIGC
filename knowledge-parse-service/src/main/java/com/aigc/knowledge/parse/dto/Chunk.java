package com.aigc.knowledge.parse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文档切片（Chunk）数据传输对象。
 * <p>
 * 每个 Chunk 代表文档中的一个语义片段，通常按段落或标题边界切分，
 * 并附带元数据与向量嵌入结果，用于后续的向量库存储与检索。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {

    /**
     * 切片在文档中的全局序号，从 0 开始
     */
    private int index;

    /**
     * 切片文本内容
     */
    private String content;

    /**
     * 切片来源页码，PDF 文档适用；TXT 等无页码文档可置空
     */
    private Integer pageNumber;

    /**
     * 切片字符在原文中的起始偏移量
     */
    private int startOffset;

    /**
     * 切片字符在原文中的结束偏移量
     */
    private int endOffset;

    /**
     * 切片摘要，可由清洗管道生成
     */
    private String summary;

    /**
     * 切片文本对应的向量嵌入，由 Embedding API 批量生成
     */
    private float[] embedding;

    /**
     * 扩展元数据，例如文件名、标题层级、章节名等
     */
    private Map<String, Object> metadata;
}
