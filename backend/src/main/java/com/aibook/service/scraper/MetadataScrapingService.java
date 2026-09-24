package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Tag;
import com.aibook.repository.BookRepository;
import com.aibook.service.AuthorService;
import com.aibook.service.SystemConfigService;
import com.aibook.service.TagService;
import com.aibook.util.BookMetadataSources;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 协调多个来源，按字段补齐书籍元信息。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataScrapingService {

    private final BookRepository bookRepository;
    private final List<MetadataScraper> scrapers;
    private final MetadataCacheService cacheService;
    private final CoverDownloadService coverDownloadService;
    private final SystemConfigService configService;
    private final AuthorService authorService;
    private final TagService tagService;

    @Transactional
    public ScrapeResult scrapeBookWithResult(Book book, boolean forceUpdate) {
        if (book.getId() != null) {
            book = bookRepository.findById(book.getId()).orElse(book);
        }
        Map<String, Object> before = snapshot(book);
        MetadataScraper.BookMetadata combined = MetadataScraper.BookMetadata.builder().build();
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        LinkedHashSet<String> usedSources = new LinkedHashSet<>();
        List<String> sourceErrors = new ArrayList<>();

        if (forceUpdate) {
            evictBookCache(book);
        } else {
            MetadataScraper.BookMetadata cached = getCachedMetadata(book);
            if (cached != null) {
                merge(combined, sources, cached, cached.getMetadataSources(), "缓存");
                if (hasMetadata(cached)) usedSources.addAll(sources.values());
            }
        }

        for (MetadataScraper scraper : getEnabledScrapers()) {
            if (!scraper.supports(book) || hasAllMetadata(combined)) continue;
            try {
                MetadataScraper.BookMetadata found = scraper.scrape(book);
                if (hasMetadata(found)) {
                    int knownFieldCount = sources.size();
                    merge(combined, sources, found, null, scraper.getName());
                    if (sources.size() > knownFieldCount) usedSources.add(scraper.getName());
                }
            } catch (Exception exception) {
                log.warn("刮削器 {} 失败: {}", scraper.getName(), exception.getMessage());
                sourceErrors.add(scraper.getName() + "：" + exception.getMessage());
            }
        }

        if (!hasMetadata(combined)) {
            String error = sourceErrors.isEmpty()
                    ? "已启用的来源均未找到匹配书籍"
                    : "未找到匹配书籍；" + String.join("；", sourceErrors);
            return ScrapeResult.builder()
                    .book(book)
                    .matched(false)
                    .success(false)
                    .updatedFields(List.of())
                    .sources(List.of())
                    .message(error)
                    .build();
        }

        if (!usedSources.isEmpty()) saveToCache(book, combined, sources);
        applyMetadata(book, combined, sources, forceUpdate);
        bookRepository.save(book);
        authorService.synchronizeBook(book);

        if (book.getCoverUrl() != null && book.getCoverUrl().startsWith("http")) {
            coverDownloadService.downloadCover(book);
        }

        List<String> updatedFields = changedFields(before, book);
        String message = updatedFields.isEmpty()
                ? "已找到匹配元信息；现有字段均已保留，没有字段需要更新"
                : "已更新 " + updatedFields.size() + " 个字段";
        return ScrapeResult.builder()
                .book(book)
                .matched(true)
                .success(true)
                .updatedFields(updatedFields)
                .sources(new ArrayList<>(usedSources))
                .message(message)
                .build();
    }

    @Transactional
    public Book scrapeBook(Book book, boolean forceUpdate) {
        return scrapeBookWithResult(book, forceUpdate).getBook();
    }

    @Transactional
    public Book scrapeBook(Book book) {
        return scrapeBook(book, false);
    }

    public List<MetadataScraper> getEnabledScrapers() {
        return scrapers.stream()
                .filter(scraper -> {
                    String key = "scraper." + scraper.getConfigKey() + ".enabled";
                    return configService.getBooleanConfig(
                            key, !"google".equals(scraper.getConfigKey()));
                })
                .sorted(Comparator.comparingInt(scraper -> configService.getIntConfig(
                        "scraper." + scraper.getConfigKey() + ".priority", scraper.getOrder())))
                .collect(Collectors.toList());
    }

    public List<ScrapeResult> scrapeBooks(List<Book> books, boolean forceUpdate) {
        return books.stream()
                .map(book -> scrapeBookWithResult(book, forceUpdate))
                .toList();
    }

    public List<ScrapeResult> scrapeBooks(List<Book> books) {
        return scrapeBooks(books, false);
    }

    public List<ScrapeResult> scrapeAllIncomplete(com.aibook.model.entity.User user) {
        return scrapeBooks(bookRepository.findIncompleteMetadataByUser(user), false);
    }

    private void evictBookCache(Book book) {
        if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
            cacheService.evictCache(cacheService.isbnKey(book.getIsbn()));
        }
        if (book.getTitle() != null) cacheService.evictCache(cacheService.titleKey(book.getTitle()));
    }

    private MetadataScraper.BookMetadata getCachedMetadata(Book book) {
        if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
            MetadataScraper.BookMetadata cached = cacheService.getFromCache(
                    cacheService.isbnKey(book.getIsbn()));
            if (cached != null) return cached;
        }
        return book.getTitle() == null
                ? null
                : cacheService.getFromCache(cacheService.titleKey(book.getTitle()));
    }

    private void saveToCache(
            Book book,
            MetadataScraper.BookMetadata metadata,
            Map<String, String> sources) {
        metadata.setMetadataSources(new LinkedHashMap<>(sources));
        if (metadata.getIsbn() != null && !metadata.getIsbn().isBlank()) {
            cacheService.saveToCache(cacheService.isbnKey(metadata.getIsbn()), metadata);
        }
        if (book.getTitle() != null && !book.getTitle().isBlank()) {
            cacheService.saveToCache(cacheService.titleKey(book.getTitle()), metadata);
        }
    }

    private void merge(
            MetadataScraper.BookMetadata target,
            Map<String, String> targetSources,
            MetadataScraper.BookMetadata addition,
            Map<String, String> additionSources,
            String defaultSource) {
        if (addition == null) return;
        mergeText("title", target.getTitle(), addition.getTitle(), target::setTitle,
                targetSources, additionSources, defaultSource);
        mergeText("author", target.getAuthor(), addition.getAuthor(), target::setAuthor,
                targetSources, additionSources, defaultSource);
        mergeText("isbn", target.getIsbn(), addition.getIsbn(), target::setIsbn,
                targetSources, additionSources, defaultSource);
        mergeText(
                "publisher", target.getPublisher(), addition.getPublisher(), target::setPublisher,
                targetSources, additionSources, defaultSource);
        mergeText(
                "publishDate", target.getPublishDate(), addition.getPublishDate(), target::setPublishDate,
                targetSources, additionSources, defaultSource);
        mergeText(
                "description", target.getDescription(), addition.getDescription(), target::setDescription,
                targetSources, additionSources, defaultSource);
        mergeText(
                "coverUrl", target.getCoverUrl(), addition.getCoverUrl(), target::setCoverUrl,
                targetSources, additionSources, defaultSource);
        mergeText("language", target.getLanguage(), addition.getLanguage(), target::setLanguage,
                targetSources, additionSources, defaultSource);
        if (target.getRating() == null && addition.getRating() != null) {
            target.setRating(addition.getRating());
            targetSources.put("rating", sourceFor("rating", additionSources, defaultSource));
        }
        if (hasTags(addition.getTags())) {
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            if (target.getTags() != null) {
                tags.addAll(java.util.Arrays.asList(target.getTags()));
            }
            for (String tag : addition.getTags()) {
                if (tag != null && !tag.isBlank()) tags.add(tag.trim());
            }
            if (!tags.isEmpty()) {
                target.setTags(tags.toArray(String[]::new));
                targetSources.putIfAbsent("tags", sourceFor("tags", additionSources, defaultSource));
            }
        }
    }

    private void mergeText(
            String key,
            String current,
            String next,
            java.util.function.Consumer<String> setter,
            Map<String, String> sources,
            Map<String, String> incomingSources,
            String defaultSource) {
        if ((current == null || current.isBlank()) && next != null && !next.isBlank()) {
            setter.accept(next.trim());
            sources.put(key, sourceFor(key, incomingSources, defaultSource));
        }
    }

    private String sourceFor(
            String field, Map<String, String> sources, String defaultSource) {
        return sources != null && sources.get(field) != null
                ? sources.get(field)
                : defaultSource;
    }

    private boolean hasMetadata(MetadataScraper.BookMetadata metadata) {
        if (metadata == null) return false;
        return hasText(metadata.getTitle()) || hasText(metadata.getAuthor())
                || hasText(metadata.getIsbn()) || hasText(metadata.getPublisher())
                || hasText(metadata.getPublishDate()) || hasText(metadata.getDescription())
                || hasText(metadata.getCoverUrl()) || hasText(metadata.getLanguage())
                || metadata.getRating() != null || hasTags(metadata.getTags());
    }

    private boolean hasAllMetadata(MetadataScraper.BookMetadata metadata) {
        return hasText(metadata.getTitle()) && hasText(metadata.getAuthor())
                && hasText(metadata.getIsbn()) && hasText(metadata.getPublisher())
                && hasText(metadata.getPublishDate()) && hasText(metadata.getDescription())
                && hasText(metadata.getCoverUrl()) && hasText(metadata.getLanguage())
                && metadata.getRating() != null
                && hasTags(metadata.getTags());
    }

    private boolean hasTags(String[] tags) {
        if (tags == null) return false;
        for (String tag : tags) if (hasText(tag)) return true;
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void applyMetadata(
            Book book,
            MetadataScraper.BookMetadata metadata,
            Map<String, String> sources,
            boolean forceUpdate) {
        applyText(
                book.getTitle(), metadata.getTitle(), forceUpdate,
                book::setTitle, "title", sources, book);
        applyText(
                book.getAuthor(), metadata.getAuthor(), forceUpdate,
                book::setAuthor, "author", sources, book);
        applyText(
                book.getIsbn(), metadata.getIsbn(), forceUpdate,
                book::setIsbn, "isbn", sources, book);
        applyText(
                book.getPublisher(), metadata.getPublisher(), forceUpdate,
                book::setPublisher, "publisher", sources, book);
        applyText(
                book.getPublishDate(), metadata.getPublishDate(), forceUpdate,
                book::setPublishDate, "publishDate", sources, book);
        applyText(
                book.getDescription(), metadata.getDescription(), forceUpdate,
                book::setDescription, "description", sources, book);
        applyText(
                book.getCoverUrl(), metadata.getCoverUrl(), forceUpdate,
                book::setCoverUrl, "coverUrl", sources, book);
        applyText(
                book.getLanguage(), metadata.getLanguage(), forceUpdate,
                book::setLanguage, "language", sources, book);
        if (metadata.getRating() != null && (forceUpdate || book.getRating() == null)) {
            book.setRating(metadata.getRating().intValue());
            BookMetadataSources.mark(book, "rating", sources.get("rating"));
        }
        if (hasTags(metadata.getTags()) && book.getUser() != null) {
            LinkedHashSet<Tag> scrapedTags = new LinkedHashSet<>(
                    tagService.getOrCreateTags(
                            java.util.Arrays.asList(metadata.getTags()), book.getUser()));
            if (forceUpdate) book.setTags(scrapedTags);
            else book.getTags().addAll(scrapedTags);
            BookMetadataSources.mark(book, "tags", sources.get("tags"));
        }
    }

    private void applyText(
            String current,
            String next,
            boolean forceUpdate,
            java.util.function.Consumer<String> setter,
            String field,
            Map<String, String> sources,
            Book book) {
        if (hasText(next) && (forceUpdate || !hasText(current))) {
            setter.accept(next.trim());
            BookMetadataSources.mark(book, field, sources.get(field));
        }
    }

    private Map<String, Object> snapshot(Book book) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", book.getTitle());
        values.put("author", book.getAuthor());
        values.put("isbn", book.getIsbn());
        values.put("publisher", book.getPublisher());
        values.put("publishDate", book.getPublishDate());
        values.put("description", book.getDescription());
        values.put("coverUrl", book.getCoverUrl());
        values.put("language", book.getLanguage());
        values.put("rating", book.getRating());
        values.put("tags", book.getTags().stream().map(Tag::getId).sorted().toList());
        return values;
    }

    private List<String> changedFields(Map<String, Object> before, Book book) {
        Map<String, Object> after = snapshot(book);
        return before.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getValue(), after.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Data
    @Builder
    public static class ScrapeResult {
        @JsonIgnore
        private Book book;
        private boolean matched;
        private boolean success;
        private List<String> updatedFields;
        private List<String> sources;
        private String message;
    }
}
