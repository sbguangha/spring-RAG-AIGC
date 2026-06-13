package com.aigc.knowledge.parse.util;

import com.aigc.knowledge.parse.exception.DocumentParseException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Optional;

/**
 * 文件类型与参数校验工具类。
 * <p>
 * 使用 JDK8 Optional 对可能为空的文件对象进行安全处理，避免 NullPointerException。
 */
public final class FileTypeUtil {

    private FileTypeUtil() {
        // 工具类禁止实例化
    }

    /**
     * 当前服务支持的文件扩展名白名单。
     */
    public static final String SUPPORTED_TYPES = "pdf,txt";

    /**
     * 校验上传文件是否合法。
     *
     * @param file 上传文件
     * @return 标准化后的文件扩展名（小写）
     */
    public static String validateAndExtractExtension(MultipartFile file) {
        String originalName = Optional.ofNullable(file)
                .map(MultipartFile::getOriginalFilename)
                .filter(name -> !name.trim().isEmpty())
                .orElseThrow(() -> new DocumentParseException("上传文件为空或文件名缺失"));

        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == originalName.length() - 1) {
            throw new DocumentParseException("无法识别文件扩展名: " + originalName);
        }

        String extension = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(extension)) {
            throw new DocumentParseException("不支持的文件类型: ." + extension + "，当前仅支持: " + SUPPORTED_TYPES);
        }
        return extension;
    }

    /**
     * 判断是否为 PDF 文件。
     */
    public static boolean isPdf(String extension) {
        return Optional.ofNullable(extension)
                .map(ext -> "pdf".equalsIgnoreCase(ext))
                .orElse(false);
    }

    /**
     * 判断是否为 TXT 文件。
     */
    public static boolean isTxt(String extension) {
        return Optional.ofNullable(extension)
                .map(ext -> "txt".equalsIgnoreCase(ext))
                .orElse(false);
    }
}
