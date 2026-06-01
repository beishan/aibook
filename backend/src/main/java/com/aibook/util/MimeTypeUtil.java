package com.aibook.util;

/**
 * MIME 类型工具类
 * 统一管理文件格式到 Content-Type 的映射
 */
public final class MimeTypeUtil {

    private MimeTypeUtil() {}

    /**
     * 根据文件格式获取 Content-Type
     */
    public static String getContentType(String format) {
        if (format == null) {
            return "application/octet-stream";
        }
        return switch (format.toLowerCase()) {
            case "epub" -> "application/epub+zip";
            case "pdf" -> "application/pdf";
            case "txt", "md" -> "text/plain";
            case "mobi", "azw3" -> "application/x-mobipocket-ebook";
            case "docx", "doc" -> "application/msword";
            case "html", "htm" -> "text/html";
            case "cbz" -> "application/x-cbz";
            case "cbr" -> "application/x-cbr";
            default -> "application/octet-stream";
        };
    }

    /**
     * 根据文件格式获取带字符集的 Content-Type
     * 适用于文本类格式（txt, md, html）
     */
    public static String getContentTypeWithCharset(String format) {
        String base = getContentType(format);
        if (format != null && isTextFormat(format)) {
            return base + "; charset=utf-8";
        }
        return base;
    }

    /**
     * 判断是否为文本格式
     */
    public static boolean isTextFormat(String format) {
        if (format == null) return false;
        return switch (format.toLowerCase()) {
            case "txt", "md", "html", "htm" -> true;
            default -> false;
        };
    }
}
