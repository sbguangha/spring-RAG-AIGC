package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.DocumentChunk;
import com.aigc.knowledge.parse.dto.ParseResult;
import com.aigc.knowledge.parse.service.cleaner.TextCleaner;
import com.aigc.knowledge.parse.service.cleaner.TextCleaners;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档解析服务。
 *
 * 核心职责：
 *   1. 支持 PDF / TXT 文件上传并提取文本；
 *   2. 使用可插拔的 TextCleaner 管道清洗文本；
 *   3. 调用 ChunkingService 进行语义切片；
 *   4. 使用 Optional 处理空值、格式错误、IO 异常，避免 NPE。
 *
 * JDK8 特性应用：
 *   - Stream API：lines().filter().map().collect() 处理 TXT 文本行；
 *   - Lambda：TextCleaner 组合管道；
 *   - Optional：防御性处理文件内容、空行、异常结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    private final ChunkingService chunkingService;

    /**
     * 解析上传的文件。
     *
     * @param file 上传的文件
     * @return 解析结果
     */
    public ParseResult parse(MultipartFile file) {
        return Optional.ofNullable(file)
                .filter(f -> !f.isEmpty())
                .map(this::doParse)
                .orElseGet(() -> ParseResult.builder()
                        .success(false)
                        .errorMsg("上传文件为空")
                        .build());
    }

    /**
     * 使用指定的清洗管道解析文件。
     *
     * @param file    上传的文件
     * @param cleaner 文本清洗管道
     * @return 解析结果
     */
    public ParseResult parse(MultipartFile file, TextCleaner cleaner) {
        return Optional.ofNullable(file)
                .filter(f -> !f.isEmpty())
                .map(f -> doParse(f, cleaner))
                .orElseGet(() -> ParseResult.builder()
                        .success(false)
                        .errorMsg("上传文件为空")
                        .build());
    }

    private ParseResult doParse(MultipartFile file) {
        return doParse(file, TextCleaners.defaultPipeline());
    }

    private ParseResult doParse(MultipartFile file, TextCleaner cleaner) {
        String originalFilename = StringUtils.defaultString(file.getOriginalFilename(), "unknown");
        String fileType = detectFileType(originalFilename).toUpperCase();

        try {
            // 1. 提取原始文本
            String rawText = extractText(file, fileType);

            // 2. 文本清洗（可插拔管道）
            String cleanedText = cleaner.clean(rawText);

            // 3. 语义切片
            List<DocumentChunk> chunks = chunkingService.split(cleanedText);

            return ParseResult.builder()
                    .fileName(originalFilename)
                    .fileType(fileType)
                    .rawText(rawText)
                    .cleanedText(cleanedText)
                    .chunks(chunks)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("[DocumentParse] 文件解析失败, file={}", originalFilename, e);
            return ParseResult.builder()
                    .fileName(originalFilename)
                    .fileType(fileType)
                    .success(false)
                    .errorMsg("解析失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 根据文件名后缀检测文件类型。
     */
    private String detectFileType(String fileName) {
        return Optional.ofNullable(fileName)
                .map(name -> name.lastIndexOf("."))
                .filter(dotIndex -> dotIndex > 0)
                .map(dotIndex -> fileName.substring(dotIndex + 1))
                .orElse("txt");
    }

    /**
     * 根据文件类型提取文本。
     */
    private String extractText(MultipartFile file, String fileType) throws IOException {
        if ("PDF".equalsIgnoreCase(fileType)) {
            return extractPdfText(file);
        } else if ("TXT".equalsIgnoreCase(fileType)) {
            return extractTxtText(file);
        } else {
            throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        }
    }

    /**
     * 使用 PDFBox 提取 PDF 文本。
     */
    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return Optional.ofNullable(stripper.getText(document))
                    .orElse("");
        }
    }

    /**
     * 使用 Stream 按行读取 TXT 文本。
     */
    private String extractTxtText(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("\n"));
        }
    }
}
