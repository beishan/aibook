package com.aibook.controller;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.SystemConfigService;
import com.aibook.service.scraper.CoverDownloadService;
import com.aibook.service.scraper.MetadataCacheService;
import com.aibook.service.scraper.MetadataScraper;
import com.aibook.service.scraper.MetadataScrapingService;
import com.aibook.service.scraper.MetadataScrapingService.ScrapeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元数据刮削控制器
 */
@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
@Slf4j
public class ScraperController {

    private final MetadataScrapingService scrapingService;
    private final CoverDownloadService coverDownloadService;
    private final MetadataCacheService cacheService;
    private final BookRepository bookRepository;
    private final SystemConfigService configService;
    private final List<MetadataScraper> scrapers;

    /**
     * 刮削单本书籍
     */
    @PostMapping("/books/{bookId}")
    public ResponseEntity<Map<String, Object>> scrapeBook(
            @PathVariable Long bookId,
            @AuthenticationPrincipal User user) {

        Book book = bookRepository.findById(bookId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElse(null);

        if (book == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Book updated = scrapingService.scrapeBook(book);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "刮削完成",
                    "book", updated
            ));
        } catch (Exception e) {
            log.error("刮削书籍失败: {}", bookId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "刮削失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量刮削书籍
     */
    @PostMapping("/books/batch")
    public ResponseEntity<Map<String, Object>> scrapeBooks(
            @RequestBody List<Long> bookIds,
            @AuthenticationPrincipal User user) {

        List<Book> books = bookRepository.findAllById(bookIds).stream()
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .toList();

        if (books.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "未找到指定书籍"
            ));
        }

        try {
            List<ScrapeResult> results = scrapingService.scrapeBooks(books);
            long successCount = results.stream().filter(ScrapeResult::isSuccess).count();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("刮削完成，成功 %d/%d 本", successCount, results.size()),
                    "results", results
            ));
        } catch (Exception e) {
            log.error("批量刮削失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "批量刮削失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 刮削所有缺少元数据的书籍
     */
    @PostMapping("/scrape-all")
    public ResponseEntity<Map<String, Object>> scrapeAll(
            @AuthenticationPrincipal User user) {

        try {
            List<ScrapeResult> results = scrapingService.scrapeAllIncomplete();
            long successCount = results.stream().filter(ScrapeResult::isSuccess).count();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("刮削完成，成功 %d/%d 本", successCount, results.size()),
                    "results", results
            ));
        } catch (Exception e) {
            log.error("全量刮削失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "全量刮削失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 下载书籍封面
     */
    @PostMapping("/covers/{bookId}")
    public ResponseEntity<Map<String, Object>> downloadCover(
            @PathVariable Long bookId,
            @AuthenticationPrincipal User user) {

        Book book = bookRepository.findById(bookId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElse(null);

        if (book == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Book updated = coverDownloadService.downloadCover(book);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "封面下载完成",
                    "coverUrl", updated.getCoverUrl() != null ? updated.getCoverUrl() : ""
            ));
        } catch (Exception e) {
            log.error("封面下载失败: {}", bookId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "封面下载失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量下载缺失封面
     */
    @PostMapping("/covers/download-missing")
    public ResponseEntity<Map<String, Object>> downloadMissingCovers(
            @AuthenticationPrincipal User user) {

        try {
            int count = coverDownloadService.downloadMissingCovers();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("封面下载完成，处理 %d 本书籍", count),
                    "count", count
            ));
        } catch (Exception e) {
            log.error("批量封面下载失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "批量封面下载失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 清除书籍缓存
     */
    @DeleteMapping("/cache/{bookId}")
    public ResponseEntity<Map<String, Object>> evictCache(
            @PathVariable Long bookId,
            @AuthenticationPrincipal User user) {

        Book book = bookRepository.findById(bookId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElse(null);

        if (book == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            if (book.getIsbn() != null) {
                cacheService.evictCache(cacheService.isbnKey(book.getIsbn()));
            }
            if (book.getTitle() != null) {
                cacheService.evictCache(cacheService.titleKey(book.getTitle()));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "缓存已清除"
            ));
        } catch (Exception e) {
            log.error("清除缓存失败: {}", bookId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "清除缓存失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取刮削器状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getScraperStatus() {
        List<Map<String, Object>> statusList = scrapers.stream()
                .map(scraper -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("name", scraper.getName());
                    status.put("configKey", scraper.getConfigKey());

                    // 从配置获取启用状态
                    String enabledKey = "scraper." + scraper.getConfigKey() + ".enabled";
                    boolean defaultEnabled = !"google".equals(scraper.getConfigKey());
                    status.put("enabled", configService.getBooleanConfig(enabledKey, defaultEnabled));

                    // 从配置获取优先级
                    String priorityKey = "scraper." + scraper.getConfigKey() + ".priority";
                    status.put("priority", configService.getIntConfig(priorityKey, scraper.getOrder()));

                    // 是否需要 API Key
                    boolean needsApiKey = "google".equals(scraper.getConfigKey());
                    status.put("needsApiKey", needsApiKey);

                    // 获取 API Key（如果有）
                    if (needsApiKey) {
                        String apiKey = configService.getConfig("scraper.google.api-key", "");
                        status.put("hasApiKey", apiKey != null && !apiKey.isBlank());
                    }

                    return status;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(statusList);
    }
}
