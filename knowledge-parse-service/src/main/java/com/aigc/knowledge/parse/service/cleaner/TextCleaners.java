package com.aigc.knowledge.parse.service.cleaner;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 文本清洗工具类：提供若干可插拔的清洗器实现。
 *
 * 每个清洗器都是 TextCleaner 的 Lambda 实现，便于通过 andThen 组合成管道。
 */
public final class TextCleaners {

    private TextCleaners() {
        // 工具类禁止实例化
    }

    /**
     * 去除首尾空白及多余空行。
     */
    public static final TextCleaner TRIM_WHITESPACE = text ->
            Optional.ofNullable(text)
                    .map(String::trim)
                    .map(t -> t.replaceAll("[ \\t]+", " "))
                    .map(t -> t.replaceAll("\\n\\s*\\n\\s*\\n+", "\\n\\n"))
                    .orElse("");

    /**
     * 去除常见特殊字符（保留中文、英文、数字、基本标点）。
     */
    public static final TextCleaner REMOVE_SPECIAL_CHARS = text ->
            Optional.ofNullable(text)
                    .map(t -> t.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s\\n\\.\\,\\;\\:\\?\\!\\（\\）\\(\\)\\[\\]\\【\\】\"\"''、。，；：？！]", ""))
                    .orElse("");

    /**
     * 去除 Markdown/HTML 标签。
     */
    public static final TextCleaner REMOVE_MARKDOWN_TAGS = text ->
            Optional.ofNullable(text)
                    .map(t -> t.replaceAll("<[^>]+>", ""))
                    .map(t -> t.replaceAll("!\\[.*?\\]\\(.*?\\)", ""))
                    .map(t -> t.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1"))
                    .orElse("");

    /**
     * 生成固定长度的摘要（取前 maxChars 个字符）。
     *
     * @param maxChars 最大字符数
     * @return 摘要清洗器
     */
    public static TextCleaner summary(int maxChars) {
        return text -> Optional.ofNullable(text)
                .filter(StringUtils::isNotBlank)
                .map(t -> t.length() > maxChars ? t.substring(0, maxChars) + "..." : t)
                .orElse("");
    }

    /**
     * 默认清洗管道：去标签 → 去特殊字符 → 去空白。
     */
    public static TextCleaner defaultPipeline() {
        return REMOVE_MARKDOWN_TAGS
                .andThen(REMOVE_SPECIAL_CHARS)
                .andThen(TRIM_WHITESPACE);
    }
}
