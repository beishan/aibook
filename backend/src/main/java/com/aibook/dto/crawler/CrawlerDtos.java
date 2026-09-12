package com.aibook.dto.crawler;

import com.aibook.model.entity.CrawlerTask;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class CrawlerDtos {
    private CrawlerDtos() { }

    public record RulePayload(
            @NotBlank String titleSelector, String authorSelector, String coverSelector,
            String descriptionSelector, String categorySelector, String tagsSelector, String statusSelector,
            String latestChapterSelector, String chapterListUrlSelector,
            @NotBlank String chapterItemSelector, String chapterTitleSelector,
            @NotBlank String chapterUrlSelector, String contentTitleSelector,
            @NotBlank String contentSelector, String removeSelectors,
            String regexReplacementsJson, @Min(0) Integer minChapterLength,
            String discoveryItemSelector, String discoveryUrlSelector,
            String discoveryTitleSelector, String discoveryAuthorSelector,
            String discoveryCoverSelector, String discoveryCategorySelector,
            String discoveryLatestChapterSelector, String discoveryNextPageSelector,
            String xpathRemoveSelectors, String stringReplacementsJson,
            Boolean removeBlankLines, Boolean saveOriginalHtml) { }

    public record ProxyPayload(@NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 1000) String url, Boolean enabled) { }

    public record SitePayload(
            @NotBlank String siteName, @Pattern(regexp = "\\s*|[a-zA-Z0-9_-]+") String siteCode,
            @NotBlank String baseUrl, String homeUrl, Boolean enabled,
            Boolean autoScan, Boolean autoCrawl, Boolean autoUpdate, Boolean autoImportLibrary,
            @Min(100) Integer requestIntervalMillis, @Min(0) Integer randomDelayMillis,
            @Min(1) @Max(8) Integer maxConcurrency, String encoding,
            @Valid List<ProxyPayload> proxies,
            @Min(1) Integer scanIntervalMinutes, @Min(1) Integer updateIntervalMinutes,
            @Min(1) @Max(50) Integer maxDiscoveryPages,
            @Pattern(regexp = "(?i)TXT|EPUB|BOTH") String autoImportFormat,
            @Size(max = 50) List<@NotBlank @Size(max = 500) String> contentFailureMarkers) { }

    public record SiteView(Long id, String siteName, String siteCode, String baseUrl, String homeUrl,
            boolean enabled, boolean autoScan, boolean autoCrawl, boolean autoUpdate,
            boolean autoImportLibrary, int requestIntervalMillis, int randomDelayMillis,
            int maxConcurrency, String encoding, String proxy,
            List<ProxyPayload> proxies,
            int scanIntervalMinutes, int updateIntervalMinutes, int maxDiscoveryPages,
            String autoImportFormat, String status, long bookCount, RulePayload rule,
            Integer ruleVersion, Long activeRuleId, long ruleCount,
            LocalDateTime lastScanAt, LocalDateTime lastUpdateAt,
            LocalDateTime lastHealthCheckAt, String healthMessage, LocalDateTime createdAt,
            List<String> contentFailureMarkers) { }

    public record ManualCrawlRequest(@NotBlank String url) { }
    public record DiscoveryPagePayload(
            @NotBlank @Size(max = 100) String pageName,
            @NotBlank @Size(max = 1000) String pageUrl,
            Boolean autoScanEnabled,
            @Min(5) @Max(10080) Integer scanIntervalMinutes,
            @Min(1) @Max(500) Integer maxPages) { }
    public record DiscoveryPageView(Long id, Long siteId, String pageName, String pageUrl,
            boolean autoScanEnabled, int scanIntervalMinutes, int maxPages,
            LocalDateTime lastScanAt, LocalDateTime createdAt) { }
    public record ExportRequest(@NotEmpty List<@Pattern(regexp = "(?i)TXT|EPUB") String> formats) { }
    public record ImportRequest(
            @NotEmpty List<@Pattern(regexp = "(?i)TXT|EPUB") String> formats) { }
    public record BookCrawlStatusRequest(
            @NotBlank @Pattern(regexp = "DISCOVERED|WAITING|PAUSED|PARTIAL_SUCCESS|COMPLETED|FAILED") String status,
            Boolean autoUpdateEnabled) { }
    public record LibrarySyncRequest(@NotNull Boolean enabled) { }
    public record TaskUpdateRequest(@NotNull CrawlerTask.Priority priority) { }
    public record BatchBookRequest(@NotEmpty List<@NotNull Long> bookIds) { }
    public record DiscoveryStatusRequest(@NotEmpty List<@NotNull Long> bookIds,
            @NotBlank @Pattern(regexp = "ACTIVE|IGNORED|BLACKLISTED") String status) { }
    public record RuleTestRequest(@NotBlank String url, @Valid RulePayload rule) { }
    public record RuleTestView(boolean success, String title, String author, String description,
            String coverUrl, String category, List<String> tags, String bookStatus,
            String chapterListUrl, int chapterCount,
            String sampleChapter, int contentLength, String contentPreview,
            long durationMillis, String errorMessage) { }
    public record RuleSaveRequest(@Min(1) int version, @NotBlank @Size(max = 300) String changeSummary,
            @Valid @NotNull RulePayload rule, Boolean enabled) { }
    public record RuleStatusRequest(@NotNull Boolean enabled) { }
    public record RuleVersionView(Long id, int version, String changeSummary, boolean enabled,
            RulePayload rule, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record RuleExportView(int schemaVersion, String siteCode, int version,
            String changeSummary, RulePayload rule) { }
    public record RuleImportRequest(@Min(1) int schemaVersion, @Min(1) int version,
            @NotBlank @Size(max = 300) String changeSummary, @Valid @NotNull RulePayload rule,
            Boolean enabled) { }

    public record BookView(Long id, Long siteId, String siteName, String externalBookId,
            String bookUrl, String bookName, String author, String coverUrl, String description,
            String category, List<String> tags, String bookStatus, String latestChapter,
            Long discoveryPageId, String discoveryPageName, int chapterCount,
            int crawledChapterCount, int failedChapterCount, String crawlStatus,
            String discoveryStatus, String importStatus, boolean autoUpdateEnabled,
            boolean autoSyncLibrary,
            Long libraryBookId, LocalDateTime discoverTime,
            LocalDateTime lastCrawlStartedAt, LocalDateTime lastCrawlTime,
            LocalDateTime createdAt) { }

    public record ChapterView(Long id, int chapterIndex, String chapterName, String chapterUrl,
            int wordCount, String crawlStatus, String accessStatus, int retryCount,
            String errorMessage, LocalDateTime crawlTime, LocalDateTime createdAt) { }

    public record ChapterFocusView(ChapterView chapter, int page) { }

    public record CrawlerLogView(
            Long id, String description, String details, LocalDateTime createdAt) { }

    public record ScanBookResultView(Long id, Long bookId, String bookName, String bookUrl,
            String resultStatus, String errorMessage, LocalDateTime createdAt) { }

    public record TaskView(String id, String type, String status, String priority,
            Long siteId, String siteName, Long discoveryPageId, String discoveryPageName,
            Integer scanMaxPages, int scannedPageCount, int progressPercent, Long bookId, String bookName,
            int totalCount, int successCount, int newBookCount,
            int duplicateCount, int failedCount, int waitingCount, String currentChapter,
            long averageRequestMillis, String errorMessage, LocalDateTime startedAt,
            LocalDateTime finishedAt, LocalDateTime createdAt) { }

    public record ExportView(Long id, String format, long fileSize, String fileHash,
            LocalDateTime createdAt) { }

    public record DashboardView(long siteCount, long enabledSiteCount, long bookCount,
            long completedBookCount, long crawlingBookCount, long failedBookCount,
            long todayNewBooks, long todayNewChapters, long readyToImportCount,
            long importedCount, List<TaskView> recentTasks) { }
}
