package com.aigc.content.gen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文生图请求 DTO。
 */
@Data
public class ImageGenerationRequest {

    /**
     * 用户输入的生图 Prompt。
     */
    @NotBlank(message = "生图 Prompt 不能为空")
    private String prompt;
}
