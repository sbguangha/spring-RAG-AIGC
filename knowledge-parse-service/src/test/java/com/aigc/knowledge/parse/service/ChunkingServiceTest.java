package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChunkingService 单元测试。
 *
 * 验证：
 *   1. 按段落切分；
 *   2. 标题与正文绑定；
 *   3. 长文本滑动窗口切分；
 *   4. 空文本返回空列表。
 */
class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() throws Exception {
        chunkingService = new ChunkingService();
        // 通过反射设置 @Value 字段，避免启动 Spring 容器
        setField(chunkingService, "chunkMaxChars", 50);
        setField(chunkingService, "chunkOverlapChars", 10);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = ChunkingService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testSplitByParagraphsAndTitles() {
        String text = "# 第一章 概述\n\n这是第一章的第一段内容。\n\n这是第一章的第二段内容。\n\n# 第二章 细节\n\n这是第二章的第一段内容。";

        List<DocumentChunk> chunks = chunkingService.split(text);

        assertEquals(3, chunks.size());
        assertEquals(0, chunks.get(0).getIndex());
        assertEquals("# 第一章 概述", chunks.get(0).getTitle());
        assertTrue(chunks.get(0).getContent().contains("这是第一章的第一段内容"));

        assertEquals("# 第二章 细节", chunks.get(2).getTitle());
        assertTrue(chunks.get(2).getContent().contains("这是第二章的第一段内容"));
    }

    @Test
    void testSplitLongTextWithSlidingWindow() {
        // 生成 120 个字符的段落，超过 chunkMaxChars=50
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append("abcdefghij");
        }
        String text = sb.toString();

        List<DocumentChunk> chunks = chunkingService.split(text);

        // 50 字符窗口，10 字符重叠，120 字符大约需要 3 块
        assertTrue(chunks.size() >= 2, "长文本应被滑动窗口切分");

        // 验证 overlap：相邻块末尾/开头应有相同内容
        if (chunks.size() >= 2) {
            String firstTail = chunks.get(0).getCleanedContent().substring(40);
            String secondHead = chunks.get(1).getCleanedContent().substring(0, firstTail.length());
            assertEquals(firstTail, secondHead, "相邻 Chunk 应有重叠");
        }
    }

    @Test
    void testSplitEmptyText() {
        List<DocumentChunk> chunks = chunkingService.split("");
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());

        List<DocumentChunk> nullChunks = chunkingService.split(null);
        assertNotNull(nullChunks);
        assertTrue(nullChunks.isEmpty());
    }

    @Test
    void testSequentialIndex() {
        String text = "段落一。\n\n段落二。\n\n段落三。";
        List<DocumentChunk> chunks = chunkingService.split(text);

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex());
        }
    }
}
