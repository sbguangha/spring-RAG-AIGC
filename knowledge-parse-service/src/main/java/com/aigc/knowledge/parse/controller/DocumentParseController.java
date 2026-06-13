package com.aigc.knowledge.parse.controller;

import com.aigc.knowledge.parse.dto.EmbeddingResult;
import com.aigc.knowledge.parse.dto.ParseResult;
import com.aigc.knowledge.parse.service.DocumentParserService;
import com.aigc.knowledge.parse.service.EmbeddingBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * 文档解析 REST 接口。
 *
 * 提供：
 *   1. POST /api/document/parse：上传 PDF/TXT 并解析切片；
 *   2. POST /api/document/parse-and-embed：上传后解析并批量生成 Embedding。
 */
@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentParseController {

    private final DocumentParserService documentParserService;
    private final EmbeddingBatchService embeddingBatchService;

    /**
     * 上传并解析文档。
     *
     * @param file PDF 或 TXT 文件
     * @return 解析结果
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parseDocument(@RequestParam("file") MultipartFile file) {
        ParseResult result = documentParserService.parse(file);
        return result.isSuccess()
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result);
    }

    /**
     * 上传、解析并批量向量化。
     *
     * @param file PDF 或 TXT 文件
     * @return 解析结果 + Embedding 列表
     */
    @PostMapping("/parse-and-embed")
    public ResponseEntity<ParseAndEmbedResponse> parseAndEmbed(@RequestParam("file") MultipartFile file) {
        ParseResult result = documentParserService.parse(file);
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(
                    new ParseAndEmbedResponse(result, null));
        }

        List<EmbeddingResult> embeddings = embeddingBatchService.embedChunks(result.getChunks());
        return ResponseEntity.ok(new ParseAndEmbedResponse(result, embeddings));
    }

    /**
     * 解析 + Embedding 组合响应。
     */
    public record ParseAndEmbedResponse(ParseResult parseResult, List<EmbeddingResult> embeddings) {
    }
}
