package com.aigc.knowledge.parse.controller;

import com.aigc.knowledge.parse.dto.ParseResult;
import com.aigc.knowledge.parse.service.DocumentParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析控制器。
 * <p>
 * 提供 PDF / TXT 文件上传接口，支持是否同步生成 Embedding 向量的可选参数。
 */
@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentParserController {

    private final DocumentParserService documentParserService;

    /**
     * 上传文档并解析为语义切片。
     *
     * @param file          上传文件，仅支持 PDF / TXT
     * @param withEmbedding 是否同步调用 Embedding API 生成向量，默认 false
     * @return 解析结果
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parseDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "withEmbedding", required = false, defaultValue = "false") boolean withEmbedding) {

        log.info("收到文档解析请求: {}, withEmbedding={}", file.getOriginalFilename(), withEmbedding);
        ParseResult result = documentParserService.parse(file, withEmbedding);
        return ResponseEntity.ok(result);
    }
}
