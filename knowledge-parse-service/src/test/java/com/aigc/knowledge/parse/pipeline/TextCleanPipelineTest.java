package com.aigc.knowledge.parse.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本清洗管道单元测试。
 * <p>
 * 验证 JDK8 Lambda 装配的清洗器组合、默认管道、摘要生成及批量清洗行为。
 */
class TextCleanPipelineTest {

    @Test
    @DisplayName("默认管道应去除首尾空白、合并连续空白并移除特殊符号")
    void defaultPipeline_shouldTrimCollapseAndRemoveSpecialChars() {
        String raw = "  ##  第一章  简介   \n\n\n  这是***重点***内容，包含  很多  空格  。  ";
        String cleaned = TextCleanPipeline.defaultPipeline().clean(raw);

        assertFalse(cleaned.contains("#"));
        assertFalse(cleaned.contains("*"));
        assertFalse(cleaned.contains("\n"));
        // 多个空格应合并
        assertEquals(-1, cleaned.indexOf("  "));
        assertTrue(cleaned.startsWith("第一章 简介"));
    }

    @Test
    @DisplayName("自定义 Lambda 清洗器组合应顺序执行")
    void compose_shouldExecuteCleanersInOrder() {
        TextCleaner toUpper = String::toUpperCase;
        TextCleaner addPrefix = text -> "[PREFIX]" + text;

        TextCleaner pipeline = TextCleanPipeline.of(toUpper, addPrefix);
        String result = pipeline.clean("hello");

        assertEquals("[PREFIX]HELLO", result);
    }

    @Test
    @DisplayName("摘要生成器应在超长文本末尾添加省略号")
    void summarize_shouldTruncateLongText() {
        String raw = "a".repeat(200);
        String summary = TextCleanPipeline.summarize(50).clean(raw);

        assertEquals(53, summary.length());
        assertTrue(summary.endsWith("..."));
    }

    @Test
    @DisplayName("批量清洗应过滤空值与空字符串")
    void cleanAll_shouldFilterNullAndBlank() {
        List<String> rawList = Arrays.asList(
                "  有效内容1  ",
                null,
                "   ",
                "##  有效内容2  ##"
        );

        List<String> cleaned = TextCleanPipeline.cleanAll(rawList);

        assertEquals(2, cleaned.size());
        assertEquals("有效内容1", cleaned.get(0));
        assertEquals("有效内容2", cleaned.get(1));
    }

    @Test
    @DisplayName("空列表组合应返回恒等清洗器")
    void composeEmptyList_shouldReturnIdentityCleaner() {
        TextCleaner cleaner = TextCleanPipeline.compose(Collections.emptyList());
        assertEquals("hello", cleaner.clean("hello"));
        assertEquals("", cleaner.clean(""));
    }

    @Test
    @DisplayName("清洗器对 null 输入应安全返回空字符串")
    void cleaners_shouldHandleNullInput() {
        assertEquals("", TextCleanPipeline.TRIM.clean(null));
        assertEquals("", TextCleanPipeline.COLLAPSE_WHITESPACE.clean(null));
        assertEquals("", TextCleanPipeline.REMOVE_SPECIAL_CHARS.clean(null));
    }
}
