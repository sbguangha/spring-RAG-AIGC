package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.DocumentChunk;
import com.aigc.knowledge.parse.dto.ParseResult;
import com.aigc.knowledge.parse.service.cleaner.TextCleaners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentParserService 单元测试。
 *
 * 验证：
 *   1. TXT 文件解析与切片；
 *   2. PDF 文件类型检测；
 *   3. 空文件返回失败；
 *   4. 自定义清洗管道生效。
 */
class DocumentParserServiceTest {

    private DocumentParserService documentParserService;
    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() throws Exception {
        chunkingService = new ChunkingService();
        setField(chunkingService, "chunkMaxChars", 100);
        setField(chunkingService, "chunkOverlapChars", 20);

        documentParserService = new DocumentParserService(chunkingService);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testParseTxtFile() {
        String content = "# 标题一\n\n这是第一段正文内容，包含一些需要清洗的特殊字符 @#$。\n\n# 标题二\n\n这是第二段正文内容。";
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );

        ParseResult result = documentParserService.parse(file);

        assertTrue(result.isSuccess());
        assertEquals("test.txt", result.getFileName());
        assertEquals("TXT", result.getFileType());
        assertNotNull(result.getRawText());
        assertNotNull(result.getCleanedText());

        List<DocumentChunk> chunks = result.getChunks();
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).getContent().contains("标题一"));
        assertFalse(result.getCleanedText().contains("@"));
        assertFalse(result.getCleanedText().contains("#"));
    }

    @Test
    void testParseEmptyFile() {
        MultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        ParseResult result = documentParserService.parse(emptyFile);

        assertFalse(result.isSuccess());
        assertEquals("上传文件为空", result.getErrorMsg());
    }

    @Test
    void testParseNullFile() {
        ParseResult result = documentParserService.parse(null);

        assertFalse(result.isSuccess());
        assertEquals("上传文件为空", result.getErrorMsg());
    }

    @Test
    void testParseWithCustomCleaner() {
        String content = "  原始文本   带空格  ";
        MultipartFile file = new MockMultipartFile(
                "file",
                "custom.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );

        // 仅使用去空白管道
        ParseResult result = documentParserService.parse(file, TextCleaners.TRIM_WHITESPACE);

        assertTrue(result.isSuccess());
        assertEquals('原', result.getCleanedText().charAt(0));
        assertFalse(result.getCleanedText().contains("   "));
    }

    @Test
    void testPdfFileTypeDetection() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "fake pdf content".getBytes(StandardCharsets.UTF_8)
        );

        // PDFBox 无法解析伪造内容，会抛出异常，验证错误处理路径
        ParseResult result = documentParserService.parse(file);

        assertFalse(result.isSuccess());
        assertEquals("PDF", result.getFileType());
        assertNotNull(result.getErrorMsg());
    }
}
