package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.Chunk;
import com.aigc.knowledge.parse.dto.ParseResult;
import com.aigc.knowledge.parse.exception.DocumentParseException;
import com.aigc.knowledge.parse.pipeline.TextCleanPipeline;
import com.aigc.knowledge.parse.pipeline.TextCleaner;
import com.aigc.knowledge.parse.util.FileTypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 文档解析服务。
 * <p>
 * 支持 PDF / TXT 文件上传，使用 JDK8 Stream API 对内容进行语义切片，
 * 通过 Lambda 装配文本清洗管道，并可批量调用 Embedding API 生成向量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    /**
     * 语义切片默认最大字符长度
     */
    private static final int DEFAULT_CHUNK_SIZE = 512;

    /**
     * 相邻切片之间的重叠字符数，用于保留上下文
     */
    private static final int DEFAULT_CHUNK_OVERLAP = 64;

    /**
     * 用于识别 Markdown/章节标题的简单正则：以 # 开头或中文数字加顿号
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6}\\s+|[一二三四五六七八九十]+、).+");

    /**
     * 段落分隔符：连续两个及以上换行
     */
    private static final Pattern PARAGRAPH_SPLITTER = Pattern.compile("\\n\\s*\\n+");

    private final EmbeddingClient embeddingClient;

    /**
     * 解析上传文件，默认不生成 Embedding。
     *
     * @param file 上传文件
     * @return 解析结果
     */
    public ParseResult parse(MultipartFile file) {
        return parse(file, false);
    }

    /**
     * 解析上传文件。
     *
     * @param file          上传文件
     * @param withEmbedding 是否调用 Embedding API 生成向量
     * @return 解析结果
     */
    public ParseResult parse(MultipartFile file, boolean withEmbedding) {
        long start = System.currentTimeMillis();

        // 1. 使用 Optional 安全校验文件并提取扩展名
        String extension = FileTypeUtil.validateAndExtractExtension(file);
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown");
        long fileSize = Optional.ofNullable(file.getSize()).orElse(0L);
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");

        try {
            // 2. 根据文件类型提取原始文本
            String rawText = extractText(file, extension);
            if (StringUtils.isBlank(rawText)) {
                return emptyResult(fileName, contentType, fileSize, "文档内容为空");
            }

            // 3. 文本清洗：Lambda 装配清洗管道
            TextCleaner cleaner = TextCleanPipeline.defaultPipeline();
            String cleanedText = cleaner.clean(rawText);

            // 4. 语义切片：Stream API 处理
            List<Chunk> chunks = splitIntoChunks(cleanedText, fileName);

            // 5. 可选：批量生成 Embedding（分批处理，控制并发）
            if (withEmbedding && !chunks.isEmpty()) {
                enrichEmbeddings(chunks);
            }

            long elapsed = System.currentTimeMillis() - start;

            return ParseResult.builder()
                    .fileName(fileName)
                    .contentType(contentType)
                    .fileSize(fileSize)
                    .totalChars(cleanedText.length())
                    .chunkCount(chunks.size())
                    .chunks(chunks)
                    .elapsedMillis(elapsed)
                    .status("SUCCESS")
                    .build();

        } catch (DocumentParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档解析失败: {}", fileName, e);
            throw new DocumentParseException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按文件类型提取文本。
     */
    private String extractText(MultipartFile file, String extension) throws IOException {
        if (FileTypeUtil.isPdf(extension)) {
            return extractPdfText(file.getInputStream());
        }
        if (FileTypeUtil.isTxt(extension)) {
            return extractTxtText(file.getInputStream());
        }
        throw new DocumentParseException("暂不支持的文件类型: ." + extension);
    }

    /**
     * 使用 PDFBox 提取 PDF 文本，并在完成后自动关闭文档资源。
     */
    private String extractPdfText(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return Optional.ofNullable(stripper.getText(document))
                    .orElse(StringUtils.EMPTY);
        }
    }

    /**
     * 使用 Stream 读取 TXT 文本内容。
     */
    private String extractTxtText(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new DocumentParseException("TXT 文件读取失败", e);
        }
    }

    /**
     * 语义切片：先按段落/标题拆分，再对超长段落按固定窗口滑动的二次切分。
     */
    private List<Chunk> splitIntoChunks(String text, String fileName) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }

        // 按段落拆分
        String[] paragraphs = PARAGRAPH_SPLITTER.split(text.trim());

        AtomicInteger index = new AtomicInteger(0);
        AtomicInteger offset = new AtomicInteger(0);

        return IntStream.range(0, paragraphs.length)
                .mapToObj(i -> paragraphs[i])
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                // 对超长段落进行二次滑动窗口切分，保持语义相对完整
                .flatMap(paragraph -> splitParagraph(paragraph, index, offset).stream())
                .map(chunk -> {
                    // 使用 Lambda 构建摘要
                    String summary = TextCleanPipeline.summarize(120).clean(chunk.getContent());
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", fileName);
                    metadata.put("heading", HEADING_PATTERN.matcher(chunk.getContent()).find());
                    chunk.setSummary(summary);
                    chunk.setMetadata(metadata);
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    /**
     * 对单个段落进行滑动窗口切分，确保每个切片不超过最大长度。
     */
    private List<Chunk> splitParagraph(String paragraph, AtomicInteger index, AtomicInteger offset) {
        List<Chunk> result = new ArrayList<>();
        if (paragraph.length() <= DEFAULT_CHUNK_SIZE) {
            int start = offset.getAndAdd(paragraph.length());
            result.add(buildChunk(index.getAndIncrement(), paragraph, null, start, start + paragraph.length()));
            return result;
        }

        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE, paragraph.length());
            String content = paragraph.substring(start, end);
            int globalStart = offset.getAndAdd(content.length());
            result.add(buildChunk(index.getAndIncrement(), content, null, globalStart, globalStart + content.length()));
            // 下一窗口起始位置 = 当前结束 - 重叠长度
            start = end - DEFAULT_CHUNK_OVERLAP;
            if (start >= paragraph.length() - DEFAULT_CHUNK_OVERLAP) {
                break;
            }
        }
        return result;
    }

    private Chunk buildChunk(int idx, String content, Integer pageNumber, int start, int end) {
        return Chunk.builder()
                .index(idx)
                .content(content)
                .pageNumber(pageNumber)
                .startOffset(start)
                .endOffset(end)
                .build();
    }

    /**
     * 批量调用 Embedding API，并将结果回填到 Chunk 中。
     */
    private void enrichEmbeddings(List<Chunk> chunks) {
        // 提取清洗后的文本（再次清洗确保无脏数据）
        List<String> texts = chunks.stream()
                .map(Chunk::getContent)
                .map(TextCleanPipeline.defaultPipeline()::clean)
                .collect(Collectors.toList());

        // 分批 Embedding，batchSize 默认 8，避免触发限流
        List<float[]> embeddings = embeddingClient.embedTexts(texts, 8);

        // 使用 Stream 将 embedding 回填到对应 Chunk
        IntStream.range(0, Math.min(chunks.size(), embeddings.size()))
                .boxed()
                .forEach(i -> chunks.get(i).setEmbedding(embeddings.get(i)));
    }

    /**
     * 构造空文档的解析结果。
     */
    private ParseResult emptyResult(String fileName, String contentType, long fileSize, String message) {
        return ParseResult.builder()
                .fileName(fileName)
                .contentType(contentType)
                .fileSize(fileSize)
                .totalChars(0)
                .chunkCount(0)
                .chunks(Collections.emptyList())
                .elapsedMillis(0L)
                .status("FAILED")
                .errorMessage(message)
                .build();
    }

    /**
     * 高级接口：允许调用方自定义清洗管道与切片大小。
     *
     * @param file          上传文件
     * @param cleaner       自定义清洗器
     * @param chunkMapper   切片后处理函数
     * @param withEmbedding 是否生成向量
     * @return 解析结果
     */
    public ParseResult parseWithPipeline(MultipartFile file,
                                          TextCleaner cleaner,
                                          Function<Chunk, Chunk> chunkMapper,
                                          boolean withEmbedding) {
        ParseResult base = parse(file, withEmbedding);

        List<Chunk> mappedChunks = Optional.ofNullable(base.getChunks())
                .orElse(Collections.emptyList())
                .stream()
                .map(chunk -> chunkMapper != null ? chunkMapper.apply(chunk) : chunk)
                .collect(Collectors.toList());

        base.setChunks(mappedChunks);
        base.setChunkCount(mappedChunks.size());
        return base;
    }
}
