package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.Chunk;
import com.aigc.knowledge.parse.dto.ParseResult;
import com.aigc.knowledge.parse.exception.DocumentParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * DocumentParserService 单元测试。
 * <p>
 * 验证 PDF/TXT 解析、语义切片、文本清洗、空文件处理、不支持的文件类型以及 Embedding 调用。
 */
@ExtendWith(MockitoExtension.class)
class DocumentParserServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    private DocumentParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new DocumentParserService(embeddingClient);
    }

    @Test
    @DisplayName("解析 TXT 文件应返回语义切片")
    void parse_txtFile_shouldReturnChunks() {
        String content = "第一章 简介\n\n" +
                "这是第一段内容，包含一些介绍性文字。\n\n" +
                "第二章 核心概念\n\n" +
                "这是第二段内容，介绍核心概念与实现细节。";

        MultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        ParseResult result = parserService.parse(file);

        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getChunkCount() > 0);
        assertTrue(result.getTotalChars() > 0);
        assertTrue(result.getChunks().stream()
                .allMatch(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank()));

        // 验证摘要已生成
        List<String> summaries = result.getChunks().stream()
                .map(Chunk::getSummary)
                .toList();
        assertTrue(summaries.stream().allMatch(s -> s != null && !s.isBlank()));
    }

    @Test
    @DisplayName("解析 PDF 文件应返回语义切片")
    void parse_pdfFile_shouldReturnChunks() throws IOException {
        byte[] pdfBytes = createSimplePdf("Chapter 1 Overview\n\nThis is the body content of the PDF document.");

        MultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                pdfBytes);

        ParseResult result = parserService.parse(file);

        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getChunkCount() >= 1);
        assertTrue(result.getChunks().get(0).getContent().contains("PDF"));
    }

    @Test
    @DisplayName("空 TXT 文件应返回 FAILED 状态")
    void parse_emptyFile_shouldReturnFailed() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]);

        ParseResult result = parserService.parse(file);

        assertEquals("FAILED", result.getStatus());
        assertEquals(0, result.getChunkCount());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("不支持的文件类型应抛出 DocumentParseException")
    void parse_unsupportedType_shouldThrowException() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                "data".getBytes(StandardCharsets.UTF_8));

        assertThrows(DocumentParseException.class, () -> parserService.parse(file));
    }

    @Test
    @DisplayName("withEmbedding=true 时应调用 EmbeddingClient")
    void parse_withEmbedding_shouldCallEmbeddingClient() {
        String content = "这是用于测试向量化的文本内容。";
        MultipartFile file = new MockMultipartFile(
                "file",
                "embed.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        when(embeddingClient.embedTexts(anyList(), anyInt()))
                .thenReturn(Arrays.asList(new float[]{0.1f, 0.2f}));

        ParseResult result = parserService.parse(file, true);

        assertEquals("SUCCESS", result.getStatus());
        verify(embeddingClient, times(1)).embedTexts(anyList(), anyInt());

        Chunk firstChunk = result.getChunks().get(0);
        assertNotNull(firstChunk.getEmbedding());
        assertEquals(2, firstChunk.getEmbedding().length);
    }

    @Test
    @DisplayName("超长段落应被滑动窗口二次切分")
    void parse_longParagraph_shouldSplitWithOverlap() {
        String longParagraph = "a".repeat(1200);
        MultipartFile file = new MockMultipartFile(
                "file",
                "long.txt",
                "text/plain",
                longParagraph.getBytes(StandardCharsets.UTF_8));

        ParseResult result = parserService.parse(file);

        assertTrue(result.getChunkCount() > 1);
        // 每个切片长度不应超过 512
        assertTrue(result.getChunks().stream()
                .allMatch(chunk -> chunk.getContent().length() <= 512));
    }

    /**
     * 使用 PDFBox 在内存中生成一个简单的单页 PDF。
     */
    private byte[] createSimplePdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 700);

                String[] lines = text.split("\\n");
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -15);
                }
                contentStream.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
