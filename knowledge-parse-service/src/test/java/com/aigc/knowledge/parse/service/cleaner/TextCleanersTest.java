package com.aigc.knowledge.parse.service.cleaner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本清洗管道单元测试。
 *
 * 验证：
 *   1. 单个清洗器效果；
 *   2. 默认管道组合效果；
 *   3. 空值/Null 安全；
 *   4. andThen 组合自定义管道。
 */
class TextCleanersTest {

    @Test
    void testRemoveMarkdownTags() {
        String raw = "# 标题\n\n这是[链接](http://example.com)和![图片](http://img.png)\n以及<div>HTML标签</div>";
        String cleaned = TextCleaners.REMOVE_MARKDOWN_TAGS.clean(raw);

        assertFalse(cleaned.contains("["));
        assertFalse(cleaned.contains("]"));
        assertFalse(cleaned.contains("("));
        assertFalse(cleaned.contains(")"));
        assertFalse(cleaned.contains("<div>"));
        assertTrue(cleaned.contains("链接"));
        assertTrue(cleaned.contains("HTML标签"));
    }

    @Test
    void testRemoveSpecialChars() {
        String raw = "Hello 世界！@#￥%……&*（）123";
        String cleaned = TextCleaners.REMOVE_SPECIAL_CHARS.clean(raw);

        assertTrue(cleaned.contains("Hello"));
        assertTrue(cleaned.contains("世界"));
        assertTrue(cleaned.contains("123"));
        assertFalse(cleaned.contains("@"));
        assertFalse(cleaned.contains("#"));
        assertFalse(cleaned.contains("￥"));
    }

    @Test
    void testTrimWhitespace() {
        String raw = "  前面有空格   中间多空格    \n\n\n\n  后面有空格  ";
        String cleaned = TextCleaners.TRIM_WHITESPACE.clean(raw);

        assertEquals('前', cleaned.charAt(0));
        assertFalse(cleaned.contains("   "));
        assertFalse(cleaned.contains("\n\n\n"));
    }

    @Test
    void testSummary() {
        String raw = "这是一段很长的文本，用于测试摘要功能。".repeat(10);
        String summary = TextCleaners.summary(50).clean(raw);

        assertEquals(53, summary.length()); // 50 + "..."
        assertTrue(summary.endsWith("..."));
    }

    @Test
    void testDefaultPipeline() {
        String raw = "# 标题\n\n  这是   [链接](http://a.com)  和 @#$ 特殊字符  ";
        String cleaned = TextCleaners.defaultPipeline().clean(raw);

        assertFalse(cleaned.contains("["));
        assertFalse(cleaned.contains("@"));
        assertFalse(cleaned.contains("#"));
        assertFalse(cleaned.contains("   "));
        assertTrue(cleaned.contains("链接"));
    }

    @Test
    void testNullSafety() {
        assertEquals("", TextCleaners.REMOVE_MARKDOWN_TAGS.clean(null));
        assertEquals("", TextCleaners.REMOVE_SPECIAL_CHARS.clean(null));
        assertEquals("", TextCleaners.TRIM_WHITESPACE.clean(null));
        assertEquals("", TextCleaners.summary(100).clean(null));
    }

    @Test
    void testCustomPipelineWithAndThen() {
        TextCleaner custom = TextCleaners.TRIM_WHITESPACE
                .andThen(TextCleaners.REMOVE_SPECIAL_CHARS)
                .andThen(TextCleaners.summary(20));

        // 使用足够长的文本，确保 summary 会截断
        String raw = "  Hello 世界！@# 这是一段很长很长的文本，用于测试自定义管道。  ";
        String cleaned = custom.clean(raw);

        assertEquals(23, cleaned.length()); // 20 + "..."
        assertFalse(cleaned.contains("@"));
        assertFalse(cleaned.contains("#"));
        assertTrue(cleaned.endsWith("..."));
    }
}
