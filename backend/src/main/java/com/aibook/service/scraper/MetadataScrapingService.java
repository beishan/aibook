package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.aibook.repository.BookRepository;
import com.aibook.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 元数据刮削服务
 * 协调多个刮削器，为书籍获取元数据
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataScrapingService {

    private final BookRepository bookRepository;
    private final List<MetadataScraper> scrapers;
    private final MetadataCacheService cacheService;
    private final CoverDownloadService coverDownloadService;
    private final SystemConfigService configService;

    /**
     * 为单本书籍刮削元数据
     * @param forceUpdate 是否强制更新已有字段
     */
    @Transactional
    public Book scrapeBook(Book book, boolean forceUpdate) {
        log.info("开始刮削书籍元数据: {} (forceUpdate={})", book.getTitle(), forceUpdate);

        // 强制更新时清除缓存，确保获取最新数据
        if (forceUpdate) {
            if (book.getIsbn() != null) {
                cacheService.evictCache(cacheService.isbnKey(book.getIsbn()));
            }
            cacheService.evictCache(cacheService.titleKey(book.getTitle()));
        }

        // 尝试从缓存获取（非强制更新时）
        if (!forceUpdate) {
            MetadataScraper.BookMetadata cachedMetadata = getCachedMetadata(book);
            if (cachedMetadata != null) {
                log.info("使用缓存元数据: {}", book.getTitle());
                applyMetadata(book, cachedMetadata, forceUpdate);
                bookRepository.save(book);
                return book;
            }
        }

        // 获取按优先级排序的已启用刮削器
        List<MetadataScraper> enabledScrapers = getEnabledScrapers();

        // 遍历刮削器
        for (MetadataScraper scraper : enabledScrapers) {
            try {
                if (!scraper.supports(book)) {
                    log.debug("刮削器 {} 不支持该书籍", scraper.getName());
                    continue;
                }

                log.info("使用 {} 刮削: {}", scraper.getName(), book.getTitle());
                MetadataScraper.BookMetadata metadata = scraper.scrape(book);

                if (metadata != null) {
                    // 保存到缓存
                    saveToCache(book, metadata);

                    // 应用元数据
                    applyMetadata(book, metadata, forceUpdate);
                    bookRepository.save(book);

                    // 下载封面（强制更新或无封面时）
                    if (metadata.getCoverUrl() != null && (forceUpdate || book.getCoverUrl() == null)) {
                        book.setCoverUrl(metadata.getCoverUrl());
                        coverDownloadService.downloadCover(book);
                    }

                    log.info("刮削成功: {} (来源: {})", book.getTitle(), scraper.getName());
                    return book;
                }
            } catch (Exception e) {
                log.error("刮削器 {} 失败: {}", scraper.getName(), e.getMessage());
            }
        }

        log.warn("所有刮削器均未找到元数据: {}", book.getTitle());
        return book;
    }

    /**
     * 为单本书籍刮削元数据（兼容旧接口，默认不强制更新）
     */
    @Transactional
    public Book scrapeBook(Book book) {
        return scrapeBook(book, false);
    }

    /**
     * 获取按优先级排序的已启用刮削器列表
     */
    public List<MetadataScraper> getEnabledScrapers() {
        return scrapers.stream()
                .filter(scraper -> {
                    String enabledKey = "scraper." + scraper.getConfigKey() + ".enabled";
                    // 默认值：douban 和 openlibrary 默认启用，google 默认禁用
                    boolean defaultValue = !"google".equals(scraper.getConfigKey());
                    return configService.getBooleanConfig(enabledKey, defaultValue);
                })
                .sorted(Comparator.comparingInt(scraper -> {
                    String priorityKey = "scraper." + scraper.getConfigKey() + ".priority";
                    return configService.getIntConfig(priorityKey, scraper.getOrder());
                }))
                .collect(Collectors.toList());
    }

    /**
     * 批量刮削书籍元数据
     * 注意：不使用@Transactional，因为异步批量执行时每本书需要独立事务
     */
    public List<ScrapeResult> scrapeBooks(List<Book> books) {
        List<ScrapeResult> results = new ArrayList<>();

        for (Book book : books) {
            ScrapeResult result = new ScrapeResult();
            result.setBookId(book.getId());
            result.setTitle(book.getTitle());

            try {
                Book updated = scrapeBook(book);
                result.setSuccess(true);
                result.setUpdatedFields(getUpdatedFields(book, updated));
            } catch (Exception e) {
                result.setSuccess(false);
                result.setError(e.getMessage());
            }

            results.add(result);
        }

        return results;
    }

    /**
     * 刮削所有缺少元数据的书籍
     * 注意：不使用@Transactional，因为异步批量执行时每本书需要独立事务
     */
    public List<ScrapeResult> scrapeAllIncomplete() {
        List<Book> books = bookRepository.findByAuthorIsNullOrDescriptionIsNull();
        log.info("找到 {} 本缺少元数据的书籍", books.size());
        return scrapeBooks(books);
    }

    /**
     * 从缓存获取元数据
     */
    private MetadataScraper.BookMetadata getCachedMetadata(Book book) {
        // 先尝试 ISBN 缓存
        if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
            MetadataScraper.BookMetadata cached = cacheService.getFromCache(cacheService.isbnKey(book.getIsbn()));
            if (cached != null) return cached;
        }

        // 再尝试书名缓存
        if (book.getTitle() != null) {
            MetadataScraper.BookMetadata cached = cacheService.getFromCache(cacheService.titleKey(book.getTitle()));
            if (cached != null) return cached;
        }

        return null;
    }

    /**
     * 保存元数据到缓存
     */
    private void saveToCache(Book book, MetadataScraper.BookMetadata metadata) {
        // 保存 ISBN 缓存
        if (metadata.getIsbn() != null) {
            cacheService.saveToCache(cacheService.isbnKey(metadata.getIsbn()), metadata);
        }

        // 保存书名缓存
        if (book.getTitle() != null) {
            cacheService.saveToCache(cacheService.titleKey(book.getTitle()), metadata);
        }
    }

    /**
     * 应用元数据到书籍
     */
    private void applyMetadata(Book book, MetadataScraper.BookMetadata metadata, boolean forceUpdate) {
        if (metadata.getTitle() != null && (forceUpdate || book.getTitle() == null || book.getTitle().length() < metadata.getTitle().length())) {
            book.setTitle(metadata.getTitle());
        }

        if (metadata.getAuthor() != null && (forceUpdate || book.getAuthor() == null)) {
            book.setAuthor(metadata.getAuthor());
        }

        if (metadata.getIsbn() != null && (forceUpdate || book.getIsbn() == null)) {
            book.setIsbn(metadata.getIsbn());
        }

        if (metadata.getPublisher() != null && (forceUpdate || book.getPublisher() == null)) {
            book.setPublisher(metadata.getPublisher());
        }

        if (metadata.getPublishDate() != null && (forceUpdate || book.getPublishDate() == null)) {
            book.setPublishDate(metadata.getPublishDate());
        }

        if (metadata.getDescription() != null && (forceUpdate || book.getDescription() == null)) {
            book.setDescription(metadata.getDescription());
        }

        if (metadata.getCoverUrl() != null && (forceUpdate || book.getCoverUrl() == null)) {
            book.setCoverUrl(metadata.getCoverUrl());
        }

        if (metadata.getLanguage() != null && (forceUpdate || book.getLanguage() == null)) {
            book.setLanguage(metadata.getLanguage());
        }

        if (metadata.getRating() != null && book.getRating() == null) {
            book.setRating(metadata.getRating().intValue());
        }
    }

    /**
     * 获取更新的字段列表
     */
    private List<String> getUpdatedFields(Book original, Book updated) {
        List<String> fields = new ArrayList<>();

        if (original.getAuthor() == null && updated.getAuthor() != null) {
            fields.add("author");
        }
        if (original.getIsbn() == null && updated.getIsbn() != null) {
            fields.add("isbn");
        }
        if (original.getPublisher() == null && updated.getPublisher() != null) {
            fields.add("publisher");
        }
        if (original.getPublishDate() == null && updated.getPublishDate() != null) {
            fields.add("publishDate");
        }
        if (original.getDescription() == null && updated.getDescription() != null) {
            fields.add("description");
        }
        if (original.getCoverUrl() == null && updated.getCoverUrl() != null) {
            fields.add("coverUrl");
        }
        if (original.getLanguage() == null && updated.getLanguage() != null) {
            fields.add("language");
        }
        if (original.getRating() == null && updated.getRating() != null) {
            fields.add("rating");
        }

        return fields;
    }

    /**
     * 刮削结果
     */
    @lombok.Data
    public static class ScrapeResult {
        private Long bookId;
        private String title;
        private boolean success;
        private List<String> updatedFields;
        private String error;
    }
}
