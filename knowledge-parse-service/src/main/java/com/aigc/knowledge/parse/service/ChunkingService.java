package com.aigc.knowledge.parse.service;

import com.aigc.knowledge.parse.dto.DocumentChunk;
import com.aigc.knowledge.parse.service.cleaner.TextCleaners;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 文档切片服务。
 *
 * 职责：将清洗后的长文本按段落/标题语义切分为 Chunk 列表。
 *
 * JDK8 特性：
 *   - Stream：按段落过滤、映射、收集；
 *   - IntStream：生成连续序号和位置信息；
 *   - Lambda：标题判断、滑动窗口切分。
 */
@Service
public class ChunkingService {

    @Value("${parse.chunk-max-chars:512}")
    private int chunkMaxChars;

    @Value("${parse.chunk-overlap-chars:64}")
    private int chunkOverlapChars;

    /**
     * 将清洗后的文本切分为 Chunk 列表。
     *
     * @param cleanedText 清洗后的文本
     * @return Chunk 列表
     */
    public List<DocumentChunk> split(String cleanedText) {
        if (StringUtils.isBlank(cleanedText)) {
            return Collections.emptyList();
        }

        // 1. 按段落拆分
        List<String> paragraphs = Arrays.stream(cleanedText.split("\\n\\n"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        // 2. 识别标题并绑定上下文，长段落滑动窗口切分
        List<DocumentChunk> chunks = new ArrayList<>();
        String currentTitle = "";
        int globalPos = 0;

        for (String paragraph : paragraphs) {
            if (isTitle(paragraph)) {
                currentTitle = paragraph;
                continue;
            }

            List<String> subChunks = splitLongText(paragraph, chunkMaxChars, chunkOverlapChars);

            for (String subChunk : subChunks) {
                String title = StringUtils.defaultIfBlank(currentTitle, "");
                String content = StringUtils.isNotBlank(title)
                        ? title + "\n" + subChunk
                        : subChunk;

                int start = globalPos;
                int end = globalPos + content.length();
                globalPos = end;

                chunks.add(DocumentChunk.builder()
                        .index(chunks.size())
                        .title(title)
                        .content(content)
                        .cleanedContent(subChunk)
                        .summary(TextCleaners.summary(100).clean(subChunk))
                        .startPos(start)
                        .endPos(end)
                        .build());
            }
        }

        // 3. 使用 IntStream 重新编号
        return IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    DocumentChunk chunk = chunks.get(i);
                    chunk.setIndex(i);
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    /**
     * 判断一行文本是否为标题。
     */
    private boolean isTitle(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        String trimmed = line.trim();
        return trimmed.startsWith("#")
                || Pattern.matches("^\\d+[\\.\\、].+", trimmed)
                || (trimmed.length() < 50 && !trimmed.contains("。"));
    }

    /**
     * 对超长文本使用滑动窗口切分。
     */
    private List<String> splitLongText(String text, int maxChars, int overlap) {
        if (text.length() <= maxChars) {
            return Collections.singletonList(text);
        }

        List<String> parts = new ArrayList<>();
        int step = maxChars - overlap;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + maxChars, text.length());
            parts.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
        }
        return parts;
    }
}
