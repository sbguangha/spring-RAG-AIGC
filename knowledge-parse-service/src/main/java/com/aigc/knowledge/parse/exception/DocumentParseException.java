package com.aigc.knowledge.parse.exception;

/**
 * 文档解析异常。
 * <p>
 * 当文件格式不合法、IO 失败或解析过程中发生不可恢复错误时抛出，
 * 便于统一异常处理与日志追踪。
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
