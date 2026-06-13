package com.aigc.knowledge.parse.pipeline;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文本清洗管道。
 * <p>
 * 基于 JDK8 Lambda 构建的可插拔清洗链路，内置常见清洗步骤：
 * <ul>
 *     <li>去除首尾空白与控制字符</li>
 *     <li>合并连续空白行与重复空格</li>
 *     <li>去除特殊符号（保留中英文常见标点）</li>
 *     <li>提取前 N 个字符作为摘要</li>
 * </ul>
 * 开发者可通过 {@link TextCleaner#andThen(TextCleaner)} 自由扩展。
 */
public final class TextCleanPipeline {

    private TextCleanPipeline() {
        // 工具类禁止实例化
    }

    /**
     * 去除首尾空白及不可见控制字符。
     */
    public static final TextCleaner TRIM = text ->
            Optional.ofNullable(text)
                    .map(String::trim)
                    .orElse(StringUtils.EMPTY);

    /**
     * 合并连续空白行、制表符与多个空格为单个空格。
     */
    public static final TextCleaner COLLAPSE_WHITESPACE = text ->
            Optional.ofNullable(text)
                    .orElse(StringUtils.EMPTY)
                    .replaceAll("[\\t\\r\\n]+", " ")
                    .replaceAll("\\s{2,}", " ");

    /**
     * 去除除常见中英文标点外的特殊符号，例如 *、#、^ 等 Markdown/富文本标记。
     */
    public static final TextCleaner REMOVE_SPECIAL_CHARS = text ->
            Optional.ofNullable(text)
                    .orElse(StringUtils.EMPTY)
                    .replaceAll("[^\\u4e00-\\u9fa5\\w\\s，。！？、；：\"\"''（）【】《》]", "");

    /**
     * 获取默认清洗管道：先 trim，再合并空白，再去除特殊字符。
     */
    public static TextCleaner defaultPipeline() {
        return TRIM.andThen(COLLAPSE_WHITESPACE).andThen(REMOVE_SPECIAL_CHARS).andThen(TRIM);
    }

    /**
     * 为文本生成摘要，默认取前 120 个字符并去除首尾空白。
     *
     * @param maxLength 摘要最大长度
     * @return 摘要清洗器
     */
    public static TextCleaner summarize(int maxLength) {
        return text -> {
            String cleaned = Optional.ofNullable(text).orElse(StringUtils.EMPTY).trim();
            if (cleaned.length() <= maxLength) {
                return cleaned;
            }
            return cleaned.substring(0, maxLength) + "...";
        };
    }

    /**
     * 将一组自定义清洗器顺序组合成一个完整管道。
     *
     * @param cleaners 清洗器列表
     * @return 组合后的清洗器
     */
    public static TextCleaner compose(List<TextCleaner> cleaners) {
        return cleaners.stream()
                .reduce(TextCleaner::andThen)
                .orElse(text -> text);
    }

    /**
     * 便捷方法：使用默认管道批量清洗文本。
     *
     * @param texts 原始文本列表
     * @return 清洗后的文本列表
     */
    public static List<String> cleanAll(List<String> texts) {
        TextCleaner pipeline = defaultPipeline();
        return texts.stream()
                .map(text -> Optional.ofNullable(text).orElse(StringUtils.EMPTY))
                .map(pipeline::clean)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    /**
     * 便捷方法：使用 Arrays.asList 快速构建清洗器数组为管道。
     */
    @SafeVarargs
    public static TextCleaner of(TextCleaner... cleaners) {
        return Arrays.stream(cleaners)
                .reduce(TextCleaner::andThen)
                .orElse(text -> text);
    }
}
