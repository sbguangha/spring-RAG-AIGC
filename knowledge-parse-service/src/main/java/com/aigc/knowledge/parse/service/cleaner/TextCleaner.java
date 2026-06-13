package com.aigc.knowledge.parse.service.cleaner;

/**
 * 文本清洗器函数式接口。
 *
 * JDK8 特性：函数式接口 + Lambda，支持将多个清洗步骤组合成可插拔管道。
 */
@FunctionalInterface
public interface TextCleaner {

    /**
     * 对输入文本进行清洗，返回清洗后的文本。
     *
     * @param text 原始文本
     * @return 清洗后的文本
     */
    String clean(String text);

    /**
     * 默认方法：将当前清洗器与另一个清洗器组合，形成管道。
     * JDK8 默认方法允许在接口中提供组合逻辑，而无需修改实现类。
     *
     * @param after 后置清洗器
     * @return 组合后的清洗器
     */
    default TextCleaner andThen(TextCleaner after) {
        return text -> after.clean(this.clean(text));
    }
}
