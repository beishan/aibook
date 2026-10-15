package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Tag;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
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
import java.util.Set;
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
    @org.springframework.beans.factory.annotation.Autowired
    private ScraperProxyService proxyService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BookVersionRepository bookVersionRepository;

    @Transactional
    public ScrapeResult scrapeBookWithResult(Book book, boolean forceUpdate) {
        return scrapeBookWithResult(book, forceUpdate, false);
    }

    @Transactional
    public ScrapeResult scrapeBookWithResult(Book book, boolean forceUpdate, boolean bypassCache) {
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
        } else if (!bypassCache) {
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
                    if (proxyService != null) proxyService.taskSucceeded(scraper.getConfigKey());
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

    /** 只应用服务端已加载并经用户确认的候选，保留现有非空字段。 */
    @Transactional
    public ScrapeResult applyConfirmedCandidate(Long bookId, com.aibook.model.entity.User user,
            MetadataScraper.BookMetadata metadata) {
        return applyConfirmedCandidate(bookId, user, metadata, Set.of());
    }

    @Transactional
    public ScrapeResult applyConfirmedCandidate(Long bookId, com.aibook.model.entity.User user,
            MetadataScraper.BookMetadata metadata, Set<String> overwriteFields) {
        return applyConfirmedMetadata(bookId, user, metadata, "豆瓣读书", overwriteFields);
    }

    @Transactional
    public ScrapeResult applyConfirmedCandidate(Long bookId, com.aibook.model.entity.User user,
            MetadataScraper.BookMetadata metadata, Set<String> overwriteFields,
            String descriptionTarget, Long versionId) {
        return applyConfirmedMetadata(bookId, user, metadata, "豆瓣读书", overwriteFields,
                descriptionTarget, versionId);
    }

    @Transactional
    public ScrapeResult applyConfirmedMetadata(Long bookId, com.aibook.model.entity.User user,
            MetadataScraper.BookMetadata metadata, String source) {
        return applyConfirmedMetadata(bookId, user, metadata, source, Set.of());
    }

    @Transactional
    public ScrapeResult applyConfirmedMetadata(Long bookId, com.aibook.model.entity.User user,
            MetadataScraper.BookMetadata metadata, String source, Set<String> requestedFields) {
        return applyConfirmedMetadata(bookId, user, metadata, source, requestedFields, "SERIES", null);
    }

    @Transactional
    public ScrapeResult applyConfirmedMetadata(Long bookId, com.aibook.model.entity.User user,
            MetadataScraper.BookMetadata metadata, String source, Set<String> requestedFields,
            String descriptionTarget, Long versionId) {
        Set<String> overwriteFields = validateOverwriteFields(metadata, requestedFields);
        String attribution = source + "（人工确认）";
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "书籍不存在或无权访问"));
        String target = descriptionTarget == null ? "SERIES" : descriptionTarget.toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("SERIES", "VERSION", "BOTH").contains(target)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "简介保存位置无效");
        }
        com.aibook.model.entity.BookVersion version = null;
        String oldVersionDescription = null;
        if ("VERSION".equals(target) || "BOTH".equals(target)) {
            if (versionId == null || bookVersionRepository == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "请选择当前书籍版本后再保存版本简介");
            }
            version = bookVersionRepository.findByIdAndBook(versionId, book)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "书籍版本不存在或不属于当前书籍"));
            oldVersionDescription = version.getDescription();
        }
        Map<String, Object> before = snapshot(book);
        Map<String, String> sources = new LinkedHashMap<>();
        for (String field : List.of("title", "author", "isbn", "publisher", "publishDate",
                "description", "coverUrl", "language", "rating", "tags")) {
            sources.put(field, attribution);
        }
        evictBookCache(book);
        MetadataScraper.BookMetadata bookMetadata = metadata;
        if ("VERSION".equals(target)) {
            bookMetadata = MetadataScraper.BookMetadata.builder()
                    .title(metadata.getTitle())
                    .author(metadata.getAuthor())
                    .isbn(metadata.getIsbn())
                    .publisher(metadata.getPublisher())
                    .publishDate(metadata.getPublishDate())
                    .coverUrl(metadata.getCoverUrl())
                    .language(metadata.getLanguage())
                    .rating(metadata.getRating())
                    .tags(metadata.getTags())
                    .metadataSources(metadata.getMetadataSources())
                    .build();
        }
        applyMetadata(book, bookMetadata, sources, false, overwriteFields);
        boolean versionDescriptionUpdated = false;
        if (version != null && hasText(metadata.getDescription())
                && (overwriteFields.contains("description") || !hasText(oldVersionDescription))) {
            version.setDescription(metadata.getDescription().strip());
            bookVersionRepository.save(version);
            versionDescriptionUpdated = !Objects.equals(oldVersionDescription, version.getDescription());
        }
        bookRepository.save(book);
        authorService.synchronizeBook(book);
        if (!Objects.equals(before.get("coverUrl"), book.getCoverUrl())
                && book.getCoverUrl() != null && book.getCoverUrl().startsWith("http")) {
            coverDownloadService.downloadCover(book);
        }
        evictBookCache(book);
        List<String> fields = new ArrayList<>(changedFields(before, book));
        if (versionDescriptionUpdated && !fields.contains("description")) fields.add("description");
        return ScrapeResult.builder().book(book).matched(true).success(true)
                .updatedFields(fields).sources(List.of(attribution))
                .message(fields.isEmpty() ? "没有字段需要更新"
                        : "已保存 " + fields.size() + " 个字段（简介保存到"
                                + ("BOTH".equals(target) ? "系列和当前版本"
                                        : version == null ? "系列" : "当前版本") + "）")
                .build();
    }

    private Set<String> validateOverwriteFields(MetadataScraper.BookMetadata metadata, Set<String> requested) {
        if (requested == null || requested.isEmpty()) return Set.of();
        Set<String> allowed = Set.of("title", "author", "isbn", "publisher", "publishDate",
                "description", "coverUrl", "language", "rating", "tags");
        if (requested.stream().anyMatch(field -> field == null || !allowed.contains(field))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "包含不支持覆盖的字段");
        }
        for (String field : requested) {
            boolean available = switch (field) {
                case "title" -> hasText(metadata.getTitle());
                case "author" -> hasText(metadata.getAuthor());
                case "isbn" -> hasText(metadata.getIsbn());
                case "publisher" -> hasText(metadata.getPublisher());
                case "publishDate" -> hasText(metadata.getPublishDate());
                case "description" -> hasText(metadata.getDescription());
                case "coverUrl" -> hasText(metadata.getCoverUrl());
                case "language" -> hasText(metadata.getLanguage());
                case "rating" -> metadata.getRating() != null;
                case "tags" -> hasTags(metadata.getTags());
                default -> false;
            };
            if (!available) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "所选覆盖字段缺少有效的来源数据，请重新核对结果");
            }
        }
        return Set.copyOf(requested);
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
        applyMetadata(book, metadata, sources, forceUpdate, null);
    }

    private void applyMetadata(Book book, MetadataScraper.BookMetadata metadata,
            Map<String, String> sources, boolean forceUpdate, Set<String> overwriteFields) {
        Set<String> overrides = overwriteFields == null ? Set.of() : overwriteFields;
        applyText(
                book.getTitle(), metadata.getTitle(), forceUpdate || overrides.contains("title"),
                book::setTitle, "title", sources, book);
        applyText(
                book.getAuthor(), metadata.getAuthor(), forceUpdate || overrides.contains("author"),
                book::setAuthor, "author", sources, book);
        applyText(
                book.getIsbn(), metadata.getIsbn(), forceUpdate || overrides.contains("isbn"),
                book::setIsbn, "isbn", sources, book);
        applyText(
                book.getPublisher(), metadata.getPublisher(), forceUpdate || overrides.contains("publisher"),
                book::setPublisher, "publisher", sources, book);
        applyText(
                book.getPublishDate(), metadata.getPublishDate(), forceUpdate || overrides.contains("publishDate"),
                book::setPublishDate, "publishDate", sources, book);
        applyText(
                book.getDescription(), metadata.getDescription(), forceUpdate || overrides.contains("description"),
                book::setDescription, "description", sources, book);
        applyText(
                book.getCoverUrl(), metadata.getCoverUrl(), forceUpdate || overrides.contains("coverUrl"),
                book::setCoverUrl, "coverUrl", sources, book);
        applyText(
                book.getLanguage(), metadata.getLanguage(), forceUpdate || overrides.contains("language"),
                book::setLanguage, "language", sources, book);
        if (metadata.getRating() != null && (forceUpdate || overrides.contains("rating") || book.getRating() == null)) {
            book.setRating(metadata.getRating().intValue());
            BookMetadataSources.mark(book, "rating", sources.get("rating"));
        }
        if (hasTags(metadata.getTags()) && book.getUser() != null
                && (forceUpdate || overrides.contains("tags") || overwriteFields == null || book.getTags().isEmpty())) {
            LinkedHashSet<Tag> scrapedTags = new LinkedHashSet<>(
                    tagService.getOrCreateTags(
                            java.util.Arrays.asList(metadata.getTags()), book.getUser(),
                            sources.get("tags")));
            if (forceUpdate || overrides.contains("tags")) book.setTags(scrapedTags);
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
