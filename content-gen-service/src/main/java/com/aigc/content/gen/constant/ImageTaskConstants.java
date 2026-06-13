package com.aigc.content.gen.constant;

/**
 * 文生图任务相关常量。
 */
public final class ImageTaskConstants {

    private ImageTaskConstants() {
        // 工具类禁止实例化
    }

    /**
     * 任务状态：已提交，等待消费。
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * 任务状态：生图进行中。
     */
    public static final String STATUS_GENERATING = "GENERATING";

    /**
     * 任务状态：生成成功。
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 任务状态：生成失败。
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * Redis Hash 字段：任务状态。
     */
    public static final String FIELD_STATUS = "status";

    /**
     * Redis Hash 字段：图片 URL。
     */
    public static final String FIELD_IMAGE_URL = "imageUrl";

    /**
     * Redis Hash 字段：错误信息。
     */
    public static final String FIELD_ERROR_MSG = "errorMsg";

    /**
     * Redis Hash 字段：用户 Prompt。
     */
    public static final String FIELD_PROMPT = "prompt";

    /**
     * Redis Hash 字段：创建时间戳。
     */
    public static final String FIELD_CREATE_TIME = "createTime";

    /**
     * Redis Hash 字段：完成时间戳。
     */
    public static final String FIELD_FINISH_TIME = "finishTime";
}
