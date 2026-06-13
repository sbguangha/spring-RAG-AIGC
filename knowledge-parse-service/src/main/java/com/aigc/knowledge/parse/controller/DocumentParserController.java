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
 * 提供 PDF / TXT 文件上传接口，支持仅解析或解析后写入向量库。
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
     * @param file      上传文件，仅支持 PDF / TXT
     * @param withIndex 解析后是否直接写入 Milvus + ES，默认 false
     * @return 解析结果
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parseDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "withIndex", required = false, defaultValue = "false") boolean withIndex) {

        log.info("收到文档解析请求: {}, withIndex={}", file.getOriginalFilename(), withIndex);
        ParseResult result = withIndex
                ? documentParserService.parseAndIndex(file)
                : documentParserService.parse(file);
        return ResponseEntity.ok(result);
    }

    /**
     * 上传文档、解析并直接写入向量库。
     *
     * @param file 上传文件
     * @return 解析结果
     */
    @PostMapping("/parse-and-index")
    public ResponseEntity<ParseResult> parseAndIndex(@RequestParam("file") MultipartFile file) {
        log.info("收到文档解析并入库请求: {}", file.getOriginalFilename());
        ParseResult result = documentParserService.parseAndIndex(file);
        return ResponseEntity.ok(result);
    }
}
