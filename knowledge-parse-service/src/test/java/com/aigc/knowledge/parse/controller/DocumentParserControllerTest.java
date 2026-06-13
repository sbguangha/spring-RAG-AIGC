package com.aigc.knowledge.parse.controller;

import com.aigc.knowledge.parse.dto.ParseResult;
import com.aigc.knowledge.parse.service.DocumentParserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DocumentParserController 单元测试。
 * <p>
 * 验证文件上传接口的参数绑定与返回结构。
 */
@WebMvcTest(DocumentParserController.class)
@TestPropertySource(properties = "spring.cloud.nacos.config.import-check.enabled=false")
class DocumentParserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentParserService documentParserService;

    @Test
    @DisplayName("上传 TXT 文件应返回 200 与解析结果")
    void parseDocument_shouldReturnOk() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "hello world".getBytes(StandardCharsets.UTF_8));

        ParseResult result = ParseResult.builder()
                .fileName("test.txt")
                .contentType("text/plain")
                .fileSize(11L)
                .totalChars(11)
                .chunkCount(1)
                .chunks(Collections.emptyList())
                .status("SUCCESS")
                .build();

        when(documentParserService.parse(any())).thenReturn(result);

        mockMvc.perform(multipart("/api/document/parse")
                        .file(file)
                        .param("withIndex", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.fileName").value("test.txt"));
    }
}
