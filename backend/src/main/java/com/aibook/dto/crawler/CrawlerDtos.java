package com.aibook.dto.crawler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class CrawlerDtos {
    private CrawlerDtos() { }

    public record RulePayload(
            String titleSelector, String authorSelector, String coverSelector,
            String descriptionSelector, String categorySelector, String statusSelector,
            String latestChapterSelector, String chapterListUrlSelector,
            @NotBlank String chapterItemSelector, String chapterTitleSelector,
            @NotBlank String chapterUrlSelector, String contentTitleSelector,
            @NotBlank String contentSelector, String removeSelectors,
            String regexReplacementsJson, @Min(0) Integer minChapterLength,
            String discoveryItemSelector, String discoveryUrlSelector,
            String discoveryTitleSelector, String discoveryAuthorSelector,
            String discoveryCoverSelector, String discoveryCategorySelector,
            String discoveryLatestChapterSelector, String discoveryNextPageSelector) { }

    public record SitePayload(
            @NotBlank String siteName, @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]+") String siteCode,
            @NotBlank String baseUrl, String homeUrl, Boolean enabled,
            Boolean autoScan, Boolean autoCrawl, Boolean autoUpdate, Boolean autoImportLibrary,
            @Min(100) Integer requestIntervalMillis, @Min(0) Integer randomDelayMillis,
            @Min(1) @Max(8) Integer maxConcurrency, @Min(1000) Integer timeoutMillis,
            @Min(0) @Max(8) Integer retryCount, String encoding, String userAgent,
            String cookie, String headersJson, String proxy,
            @Min(1) Integer scanIntervalMinutes, @Min(1) Integer updateIntervalMinutes,
            @Min(1) @Max(50) Integer maxDiscoveryPages,
            @Pattern(regexp = "(?i)TXT|EPUB|BOTH") String autoImportFormat,
            @Valid @NotNull RulePayload rule) { }

    public record SiteView(Long id, String siteName, String siteCode, String baseUrl, String homeUrl,
            boolean enabled, boolean autoScan, boolean autoCrawl, boolean autoUpdate,
            boolean autoImportLibrary, int requestIntervalMillis, int randomDelayMillis,
            int maxConcurrency, int timeoutMillis, int retryCount, String encoding,
            String userAgent, String cookie, String headersJson, String proxy,
            int scanIntervalMinutes, int updateIntervalMinutes, int maxDiscoveryPages,
            String autoImportFormat, String status, long bookCount, RulePayload rule,
            LocalDateTime lastScanAt, LocalDateTime lastUpdateAt, LocalDateTime createdAt) { }

    public record ManualCrawlRequest(@NotBlank String url) { }
    public record ExportRequest(@NotEmpty List<@Pattern(regexp = "(?i)TXT|EPUB") String> formats) { }
    public record ImportRequest(@Pattern(regexp = "(?i)TXT|EPUB") String format) { }
    public record BatchBookRequest(@NotEmpty List<@NotNull Long> bookIds) { }
    public record DiscoveryStatusRequest(@NotEmpty List<@NotNull Long> bookIds,
            @NotBlank @Pattern(regexp = "ACTIVE|IGNORED|BLACKLISTED") String status) { }

    public record BookView(Long id, Long siteId, String siteName, String externalBookId,
            String bookUrl, String bookName, String author, String coverUrl, String description,
            String category, String bookStatus, String latestChapter, int chapterCount,
            int crawledChapterCount, int failedChapterCount, String crawlStatus,
            String discoveryStatus, String importStatus, Long libraryBookId, LocalDateTime discoverTime,
            LocalDateTime lastCrawlTime) { }

    public record ChapterView(Long id, int chapterIndex, String chapterName, String chapterUrl,
            int wordCount, String crawlStatus, String accessStatus, int retryCount,
            String errorMessage, LocalDateTime crawlTime) { }

    public record TaskView(String id, String type, String status, String priority,
            Long siteId, String siteName, Long bookId, String bookName, int totalCount,
            int successCount, int failedCount, int waitingCount, String currentChapter,
            long averageRequestMillis, String errorMessage, LocalDateTime startedAt,
            LocalDateTime finishedAt, LocalDateTime createdAt) { }

    public record ExportView(Long id, String format, long fileSize, String fileHash,
            LocalDateTime createdAt) { }

    public record DashboardView(long siteCount, long enabledSiteCount, long bookCount,
            long completedBookCount, long crawlingBookCount, long failedBookCount,
            long todayNewBooks, long todayNewChapters, long readyToImportCount,
            long importedCount, List<TaskView> recentTasks) { }
}
