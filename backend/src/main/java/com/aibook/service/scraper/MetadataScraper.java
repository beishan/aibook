package com.aibook.service.scraper;

import com.aibook.model.entity.Book;

/**
 * 元数据刮削器接口
 */
public interface MetadataScraper {

    /**
     * 清理书籍标题，移除干扰文字后再用于搜索
     * 例如: "我在北京送快递 (胡安焉) (Z-Library)" -> "我在北京送快递"
     *
     * @param title 原始标题（可能来自文件名）
     * @return 清理后的标题，适合用于搜索 API
     */
    static String cleanSearchTitle(String title) {
        if (title == null || title.isBlank()) {
            return title;
        }

        String cleaned = title.trim();

        // 移除常见的文件名后缀噪音（如 _removed, _uploaded 等）
        cleaned = cleaned.replaceAll("(?i)[_\\s-](removed|uploaded|scanned|converted)$", "");

        // 移除尾部的括号内容（可能是作者、来源、年份等干扰信息）
        // 支持: (), （）, [], 【】
        // 循环移除多层尾部括号，如 "书名 (作者) (来源)"
        boolean changed;
        do {
            String before = cleaned;
            // 移除尾部的半角/全角括号及其内容
            cleaned = cleaned.replaceAll("\\s*[（(][^）)]*[）)]\\s*$", "");
            // 移除尾部的方括号/中括号及其内容
            cleaned = cleaned.replaceAll("\\s*[【\\[][^】\\]][】\\]]\\s*$", "");
            changed = !cleaned.equals(before);
        } while (changed && !cleaned.isEmpty());

        // 移除尾部的冒号/破折号后跟的内容（如 "书名: 副标题" 保留主标题）
        // 但保留中文书名中常见的冒号（如 "三体：黑暗森林"），仅处理明显是附加信息的情况
        // 这里不做激进处理，交给括号清理已覆盖大部分场景

        cleaned = cleaned.trim();

        // 如果清理后为空，返回原始标题
        return cleaned.isEmpty() ? title.trim() : cleaned;
    }

    /**
     * 刮削器名称
     */
    String getName();

    /**
     * 是否支持该书籍
     */
    boolean supports(Book book);

    /**
     * 刮削书籍元数据
     * @param book 书籍实体
     * @return 更新后的书籍信息，失败返回 null
     */
    BookMetadata scrape(Book book);

    /**
     * 通过 ISBN 刮削
     */
    BookMetadata scrapeByIsbn(String isbn);

    /**
     * 获取刮削器优先级（数字越小优先级越高）
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 获取配置键名（用于数据库配置，如 "douban", "google", "openlibrary"）
     */
    default String getConfigKey() {
        return getName().toLowerCase().replace(" ", "");
    }

    /**
     * 书籍元数据
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class BookMetadata {
        private String title;
        private String author;
        private String isbn;
        private String publisher;
        private String publishDate;
        private String description;
        private String coverUrl;
        private String language;
        private Double rating;
        private String[] tags;
    }
}
