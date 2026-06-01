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

import java.util.*;

/**
 * OPDS 2.0 服务
 * 实现 OPDS 2.0 协议（JSON 格式）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Opds2Service {

    private static final int PAGE_SIZE = 50;

    private final BookRepository bookRepository;

    /**
     * 根目录
     */
    public Map<String, Object> getRootCatalog(User user) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("metadata", Map.of(
            "title", "汗牛充栋 - 书库",
            "modified", java.time.Instant.now().toString()
        ));
        catalog.put("links", List.of(
            Map.of("rel", "self", "href", "/opds/v2", "type", "application/opds+json")
        ));
        catalog.put("catalogs", List.of(
            createCatalogEntry("所有书籍", "/opds/v2/books"),
            createCatalogEntry("按格式分类", "/opds/v2/formats"),
            createCatalogEntry("我的收藏", "/opds/v2/favorites"),
            createCatalogEntry("正在阅读", "/opds/v2/reading")
        ));
        return catalog;
    }

    /**
     * 格式列表
     */
    public Map<String, Object> getFormatsCatalog(User user) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("metadata", Map.of(
            "title", "按格式分类",
            "modified", java.time.Instant.now().toString()
        ));
        catalog.put("links", List.of(
            Map.of("rel", "self", "href", "/opds/v2/formats", "type", "application/opds+json"),
            Map.of("rel", "start", "href", "/opds/v2", "type", "application/opds+json")
        ));

        String[] formats = {"epub", "pdf", "txt", "mobi", "azw3", "docx", "html", "md"};
        String[] formatNames = {"EPUB", "PDF", "TXT", "MOBI", "AZW3", "DOCX", "HTML", "Markdown"};

        List<Map<String, Object>> catalogs = new ArrayList<>();
        for (int i = 0; i < formats.length; i++) {
            catalogs.add(createCatalogEntry(formatNames[i], "/opds/v2/formats/" + formats[i]));
        }
        catalog.put("catalogs", catalogs);
        return catalog;
    }

    /**
     * 所有书籍
     */
    public Map<String, Object> getBooksCatalog(User user, int page) {
        Page<Book> books = bookRepository.findByUser(user, PageRequest.of(page, PAGE_SIZE));
        return buildPublicationsFeed(books, "所有书籍", "/opds/v2/books", page);
    }

    /**
     * 按格式获取书籍
     */
    public Map<String, Object> getBooksByFormat(User user, String format, int page) {
        Page<Book> books = bookRepository.findByUserAndFormat(user, format, PageRequest.of(page, PAGE_SIZE));
        return buildPublicationsFeed(books, format.toUpperCase() + " 书籍", "/opds/v2/formats/" + format, page);
    }

    /**
     * 收藏书籍
     */
    public Map<String, Object> getFavoriteBooks(User user, int page) {
        Page<Book> books = bookRepository.findByUserAndIsFavorite(user, true, PageRequest.of(page, PAGE_SIZE));
        return buildPublicationsFeed(books, "我的收藏", "/opds/v2/favorites", page);
    }

    /**
     * 正在阅读的书籍
     */
    public Map<String, Object> getReadingBooks(User user, int page) {
        Page<Book> books = bookRepository.findByUserAndReadingStatus(user, Book.ReadingStatus.READING,
                PageRequest.of(page, PAGE_SIZE));
        return buildPublicationsFeed(books, "正在阅读", "/opds/v2/reading", page);
    }

    /**
     * 搜索书籍
     */
    public Map<String, Object> searchBooks(User user, String query, int page) {
        Page<Book> books = bookRepository.searchByKeyword(user, query, PageRequest.of(page, PAGE_SIZE));
        return buildPublicationsFeed(books, "搜索: " + query, "/opds/v2/search", page);
    }

    /**
     * 构建 publications feed
     */
    private Map<String, Object> buildPublicationsFeed(Page<Book> page, String title, String basePath, int currentPage) {
        List<Book> books = page.getContent();
        Map<String, Object> feed = new LinkedHashMap<>();

        feed.put("metadata", Map.of(
            "title", title,
            "modified", java.time.Instant.now().toString()
        ));

        List<Map<String, String>> links = new ArrayList<>();
        links.add(Map.of("rel", "self", "href", basePath + "?page=" + currentPage, "type", "application/opds+json"));
        links.add(Map.of("rel", "start", "href", "/opds/v2", "type", "application/opds+json"));
        if (page.hasNext()) {
            links.add(Map.of("rel", "next", "href", basePath + "?page=" + (currentPage + 1), "type", "application/opds+json"));
        }
        if (currentPage > 0) {
            links.add(Map.of("rel", "prev", "href", basePath + "?page=" + (currentPage - 1), "type", "application/opds+json"));
        }
        feed.put("links", links);

        List<Map<String, Object>> publications = new ArrayList<>();
        for (Book book : books) {
            publications.add(convertBookToPublication(book));
        }
        feed.put("publications", publications);

        return feed;
    }

    /**
     * 将 Book 转换为 OPDS 2.0 publication 格式
     */
    private Map<String, Object> convertBookToPublication(Book book) {
        Map<String, Object> pub = new LinkedHashMap<>();

        // metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", book.getTitle());
        if (book.getAuthor() != null) {
            metadata.put("author", List.of(Map.of("name", book.getAuthor())));
        }
        if (book.getIsbn() != null) {
            metadata.put("identifier", book.getIsbn());
        }
        if (book.getPublisher() != null) {
            metadata.put("publisher", book.getPublisher());
        }
        if (book.getDescription() != null) {
            metadata.put("description", book.getDescription());
        }
        if (book.getLanguage() != null) {
            metadata.put("language", book.getLanguage());
        }
        if (book.getRating() != null) {
            metadata.put("rating", Map.of("value", book.getRating(), "scheme", "http://schema.org/Rating"));
        }
        pub.put("metadata", metadata);

        // links
        List<Map<String, String>> links = new ArrayList<>();
        String format = book.getFormat().toLowerCase();
        links.add(Map.of(
            "rel", "http://opds-spec.org/acquisition/open-access",
            "href", "/opds/books/" + book.getId() + "/download",
            "type", MimeTypeUtil.getContentType(format),
            "title", book.getTitle() + "." + format
        ));
        if (book.getCoverUrl() != null) {
            links.add(Map.of(
                "rel", "http://opds-spec.org/image",
                "href", book.getCoverUrl(),
                "type", "image/jpeg"
            ));
        }
        pub.put("links", links);

        // images (OPDS 2.0 uses images array)
        if (book.getCoverUrl() != null) {
            pub.put("images", List.of(Map.of(
                "href", book.getCoverUrl(),
                "type", "image/jpeg"
            )));
        }

        return pub;
    }

    /**
     * 创建目录条目
     */
    private Map<String, Object> createCatalogEntry(String title, String href) {
        return Map.of(
            "metadata", Map.of("title", title),
            "links", List.of(Map.of(
                "rel", "subsection",
                "href", href,
                "type", "application/opds+json"
            ))
        );
    }
}
