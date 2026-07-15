package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.util.MimeTypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OPDS 服务
 * 实现 OPDS 1.2 兼容的电子书目录服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpdsService {

    private static final int PAGE_SIZE = 50;
    private static final String OPDS_TYPE = "application/atom+xml;profile=opds-catalog";

    private final BookRepository bookRepository;

    /**
     * 获取 OpenSearch 描述
     */
    public String getSearchDescription() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<OpenSearchDescription xmlns=\"http://a9.com/-/spec/opensearch/1.1/\">\n"
            + "  <ShortName>汗牛充栋</ShortName>\n"
            + "  <Description>搜索私人书库</Description>\n"
            + "  <InputEncoding>UTF-8</InputEncoding>\n"
            + "  <OutputEncoding>UTF-8</OutputEncoding>\n"
            + "  <Url type=\"" + OPDS_TYPE + "\" template=\"/opds/search?query={searchTerms}\"/>\n"
            + "</OpenSearchDescription>";
    }

    /**
     * 获取根目录
     */
    public String getRootCatalog(User user) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:opds=\"http://opds-spec.org/2010/catalog\">\n");
        xml.append("  <title>汗牛充栋 - 书库</title>\n");
        xml.append("  <subtitle>您的私人书库</subtitle>\n");
        xml.append("  <updated>").append(java.time.Instant.now()).append("</updated>\n");
        xml.append("  <id>urn:aibook:root</id>\n");
        xml.append("  <author>\n");
        xml.append("    <name>汗牛充栋</name>\n");
        xml.append("  </author>\n");
        xml.append("  <link rel=\"self\" type=\"").append(OPDS_TYPE).append("\" href=\"/opds\"/>\n");
        xml.append("  <link rel=\"start\" type=\"").append(OPDS_TYPE).append("\" href=\"/opds\"/>\n");
        xml.append("  <link rel=\"search\" type=\"application/opensearchdescription+xml\" href=\"/opds/search.xml\" title=\"搜索\"/>\n");

        // 所有书籍
        xml.append("  <entry>\n");
        xml.append("    <title>所有书籍</title>\n");
        xml.append("    <id>urn:aibook:all</id>\n");
        xml.append("    <updated>").append(java.time.Instant.now()).append("</updated>\n");
        xml.append("    <content type=\"text\">所有书籍</content>\n");
        xml.append("    <link rel=\"subsection\" type=\"application/atom+xml;profile=opds-catalog\" href=\"/opds/books\"/>\n");
        xml.append("  </entry>\n");

        // 按格式分类
        xml.append("  <entry>\n");
        xml.append("    <title>按格式分类</title>\n");
        xml.append("    <id>urn:aibook:formats</id>\n");
        xml.append("    <updated>").append(java.time.Instant.now()).append("</updated>\n");
        xml.append("    <content type=\"text\">按格式分类</content>\n");
        xml.append("    <link rel=\"subsection\" type=\"application/atom+xml;profile=opds-catalog\" href=\"/opds/formats\"/>\n");
        xml.append("  </entry>\n");

        // 收藏
        xml.append("  <entry>\n");
        xml.append("    <title>我的收藏</title>\n");
        xml.append("    <id>urn:aibook:favorites</id>\n");
        xml.append("    <updated>").append(java.time.Instant.now()).append("</updated>\n");
        xml.append("    <content type=\"text\">我的收藏</content>\n");
        xml.append("    <link rel=\"subsection\" type=\"application/atom+xml;profile=opds-catalog\" href=\"/opds/favorites\"/>\n");
        xml.append("  </entry>\n");

        // 正在阅读
        xml.append("  <entry>\n");
        xml.append("    <title>正在阅读</title>\n");
        xml.append("    <id>urn:aibook:reading</id>\n");
        xml.append("    <updated>").append(java.time.Instant.now()).append("</updated>\n");
        xml.append("    <content type=\"text\">正在阅读</content>\n");
        xml.append("    <link rel=\"subsection\" type=\"application/atom+xml;profile=opds-catalog\" href=\"/opds/reading\"/>\n");
        xml.append("  </entry>\n");

        xml.append("</feed>");
        return xml.toString();
    }

    /**
     * 获取格式列表目录
     */
    public String getFormatsCatalog(User user) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:opds=\"http://opds-spec.org/2010/catalog\">\n");
        xml.append("  <title>按格式分类</title>\n");
        xml.append("  <updated>").append(java.time.Instant.now()).append("</updated>\n");
        xml.append("  <id>urn:aibook:formats</id>\n");
        xml.append("  <link rel=\"start\" type=\"application/atom+xml;profile=opds-catalog\" href=\"/opds\"/>\n");

        String[] formats = {"epub", "pdf", "txt", "mobi", "azw3", "docx", "html", "md"};
        String[] formatNames = {"EPUB", "PDF", "TXT", "MOBI", "AZW3", "DOCX", "HTML", "Markdown"};

        for (int i = 0; i < formats.length; i++) {
            xml.append("  <entry>\n");
            xml.append("    <title>").append(formatNames[i]).append("</title>\n");
            xml.append("    <id>urn:aibook:format:").append(formats[i]).append("</id>\n");
            xml.append("    <updated>").append(java.time.Instant.now()).append("</updated>\n");
            xml.append("    <content type=\"text\">").append(formatNames[i]).append(" 格式书籍</content>\n");
            xml.append("    <link rel=\"subsection\" type=\"application/atom+xml;profile=opds-catalog\" href=\"/opds/formats/").append(formats[i]).append("\"/>\n");
            xml.append("  </entry>\n");
        }

        xml.append("</feed>");
        return xml.toString();
    }

    /**
     * 获取书籍列表（分页）
     */
    public String getBooksCatalog(User user, int page) {
        Page<Book> books = bookRepository.findByUser(user, PageRequest.of(page, PAGE_SIZE));
        return buildBooksFeed(books, "所有书籍", "urn:aibook:all", "/opds/books", page);
    }

    /**
     * 按格式获取书籍（分页）
     */
    public String getBooksByFormat(User user, String format, int page) {
        Page<Book> books = bookRepository.findByUserAndFormat(user, format, PageRequest.of(page, PAGE_SIZE));
        return buildBooksFeed(books, format.toUpperCase() + " 书籍", "urn:aibook:format:" + format,
                "/opds/formats/" + format, page);
    }

    /**
     * 获取收藏书籍（分页）
     */
    public String getFavoriteBooks(User user, int page) {
        Page<Book> books = bookRepository.findByUserAndIsFavorite(user, true, PageRequest.of(page, PAGE_SIZE));
        return buildBooksFeed(books, "我的收藏", "urn:aibook:favorites", "/opds/favorites", page);
    }

    /**
     * 获取正在阅读的书籍（分页）
     */
    public String getReadingBooks(User user, int page) {
        Page<Book> books = bookRepository.findByUserAndReadingStatus(user, Book.ReadingStatus.READING,
                PageRequest.of(page, PAGE_SIZE));
        return buildBooksFeed(books, "正在阅读", "urn:aibook:reading", "/opds/reading", page);
    }

    /**
     * 搜索书籍（分页）
     */
    public String searchBooks(User user, String query, int page) {
        Page<Book> books = bookRepository.searchByKeyword(user, query, PageRequest.of(page, PAGE_SIZE));
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return buildBooksFeed(books, "搜索: " + query, "urn:aibook:search:" + query,
                "/opds/search?query=" + encodedQuery, page);
    }

    /**
     * 构建书籍 Feed（分页版本）
     */
    private String buildBooksFeed(Page<Book> page, String title, String id, String basePath, int currentPage) {
        List<Book> books = page.getContent();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:opds=\"http://opds-spec.org/2010/catalog\">\n");
        xml.append("  <title>").append(escapeXml(title)).append("</title>\n");
        xml.append("  <updated>").append(java.time.Instant.now()).append("</updated>\n");
        xml.append("  <id>").append(id).append("</id>\n");
        xml.append("  <author>\n");
        xml.append("    <name>汗牛充栋</name>\n");
        xml.append("  </author>\n");

        String pageSeparator = basePath.contains("?") ? "&amp;page=" : "?page=";

        // 返回根目录链接
        xml.append("  <link rel=\"self\" type=\"").append(OPDS_TYPE).append("\" href=\"")
           .append(basePath).append(pageSeparator).append(currentPage).append("\"/>\n");
        xml.append("  <link rel=\"start\" type=\"").append(OPDS_TYPE).append("\" href=\"/opds\"/>\n");

        // 分页：下一页链接
        if (page.hasNext()) {
            xml.append("  <link rel=\"next\" type=\"").append(OPDS_TYPE).append("\" href=\"")
               .append(basePath).append(pageSeparator).append(currentPage + 1).append("\"/>\n");
        }

        // 分页：上一页链接
        if (currentPage > 0) {
            xml.append("  <link rel=\"prev\" type=\"").append(OPDS_TYPE).append("\" href=\"")
               .append(basePath).append(pageSeparator).append(currentPage - 1).append("\"/>\n");
        }

        for (Book book : books) {
            xml.append("  <entry>\n");
            xml.append("    <title>").append(escapeXml(book.getTitle())).append("</title>\n");
            xml.append("    <id>urn:aibook:book:").append(book.getId()).append("</id>\n");
            xml.append("    <updated>").append(formatAtomTimestamp(
                    book.getUpdatedAt() != null ? book.getUpdatedAt() : book.getCreatedAt()
            )).append("</updated>\n");

            if (book.getAuthor() != null) {
                xml.append("    <author>\n");
                xml.append("      <name>").append(escapeXml(book.getAuthor())).append("</name>\n");
                xml.append("    </author>\n");
            }

            if (book.getDescription() != null) {
                xml.append("    <content type=\"text\">").append(escapeXml(book.getDescription())).append("</content>\n");
            } else {
                xml.append("    <content type=\"text\">").append(escapeXml(book.getTitle())).append("</content>\n");
            }

            // 封面
            if (book.getCoverUrl() != null) {
                String coverUrl = escapeXml(book.getCoverUrl());
                xml.append("    <link rel=\"http://opds-spec.org/image\" type=\"image/jpeg\" href=\"").append(coverUrl).append("\"/>\n");
                xml.append("    <link rel=\"http://opds-spec.org/image/thumbnail\" type=\"image/jpeg\" href=\"").append(coverUrl).append("\"/>\n");
            }

            // 下载链接
            String ext = book.getFormat().toLowerCase();
            String contentType = MimeTypeUtil.getContentType(ext);
            xml.append("    <link rel=\"http://opds-spec.org/acquisition/open-access\" type=\"").append(contentType).append("\" href=\"/opds/books/").append(book.getId()).append("/download\"/>\n");

            xml.append("  </entry>\n");
        }

        xml.append("</feed>");
        return xml.toString();
    }

    /**
     * XML 转义
     */
    public static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    static String formatAtomTimestamp(LocalDateTime value) {
        if (value == null) {
            return java.time.Instant.EPOCH.toString();
        }
        return value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
