package com.aigc.content.gen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文生图任务消息。
 *
 * 设计为可序列化的 DTO，便于通过 Redis List 进行 JSON 传输。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageTaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务唯一标识。
     */
    private String taskId;

    /**
     * 用户输入的生图 Prompt。
     */
    private String prompt;

    /**
     * 任务创建时间戳。
     */
    private Long createTime;
}
