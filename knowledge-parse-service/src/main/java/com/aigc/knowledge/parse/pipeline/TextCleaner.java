package com.aigc.knowledge.parse.pipeline;

/**
 * 文本清洗器函数式接口。
 * <p>
 * 使用 JDK8 的 {@link java.util.function.Function} 风格定义，支持通过 Lambda 表达式
 * 快速组装可插拔的文本清洗管道。每个清洗器接收原始文本，返回清洗后的文本。
 */
@FunctionalInterface
public interface TextCleaner {

    /**
     * 对输入文本执行清洗操作。
     *
     * @param text 原始文本，可能为空
     * @return 清洗后的文本
     */
    String clean(String text);

    /**
     * 使用默认方法实现管道组合：先执行当前清洗器，再执行 after 清洗器。
     *
     * @param after 后置清洗器
     * @return 组合后的新清洗器
     */
    default TextCleaner andThen(TextCleaner after) {
        return text -> after.clean(this.clean(text));
    }
}
