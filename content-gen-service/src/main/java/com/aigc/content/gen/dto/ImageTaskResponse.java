package com.aigc.content.gen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文生图任务状态响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageTaskResponse {

    /**
     * 任务 ID。
     */
    private String taskId;

    /**
     * 任务状态：PENDING / GENERATING / SUCCESS / FAILED。
     */
    private String status;

    /**
     * 生成的图片 URL（SUCCESS 时返回）。
     */
    private String imageUrl;

    /**
     * 错误信息（FAILED 时返回）。
     */
    private String errorMsg;

    /**
     * 用户原始 Prompt。
     */
    private String prompt;

    /**
     * 创建时间戳。
     */
    private Long createTime;

    /**
     * 完成时间戳。
     */
    private Long finishTime;
}
