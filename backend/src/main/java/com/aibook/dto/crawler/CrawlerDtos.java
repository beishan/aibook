package com.aibook.dto.crawler;

import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.CrawlerQueueExecutor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
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

    public record ContentMarkerPayload(
            @NotBlank @Size(max = 500) String marker,
            @NotBlank @Pattern(regexp = "FAILED|PENDING_RELEASE") String status) { }

    public record SiteAccessWindowPayload(
            @NotBlank @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String startTime,
            @NotBlank @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String endTime) { }

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
            @Size(max = 50) List<@Valid ContentMarkerPayload> contentMarkers,
            Boolean respectRobotsTxt,
            @Pattern(regexp = "#[0-9a-fA-F]{6}") String themeColor,
            @Min(100) Integer maxRequestIntervalMillis,
            @Size(max = 50) List<@NotNull @Valid SiteAccessWindowPayload> blockedAccessWindows,
            @Min(1) @Max(100) Integer cooldownFailureThreshold) {
        public SitePayload(
                String siteName, String siteCode, String baseUrl, String homeUrl,
                Boolean enabled, Boolean autoScan, Boolean autoCrawl, Boolean autoUpdate,
                Boolean autoImportLibrary, Integer requestIntervalMillis,
                Integer randomDelayMillis, Integer maxConcurrency, String encoding,
                List<ProxyPayload> proxies, Integer scanIntervalMinutes,
                Integer updateIntervalMinutes, Integer maxDiscoveryPages,
                String autoImportFormat, List<ContentMarkerPayload> contentMarkers,
                Boolean respectRobotsTxt, String themeColor, Integer maxRequestIntervalMillis,
                List<SiteAccessWindowPayload> blockedAccessWindows) {
            this(siteName, siteCode, baseUrl, homeUrl, enabled, autoScan, autoCrawl,
                    autoUpdate, autoImportLibrary, requestIntervalMillis, randomDelayMillis,
                    maxConcurrency, encoding, proxies, scanIntervalMinutes, updateIntervalMinutes,
                    maxDiscoveryPages, autoImportFormat, contentMarkers, respectRobotsTxt,
                    themeColor, maxRequestIntervalMillis, blockedAccessWindows, null);
        }

        public SitePayload(
                String siteName, String siteCode, String baseUrl, String homeUrl,
                Boolean enabled, Boolean autoScan, Boolean autoCrawl, Boolean autoUpdate,
                Boolean autoImportLibrary, Integer requestIntervalMillis,
                Integer randomDelayMillis, Integer maxConcurrency, String encoding,
                List<ProxyPayload> proxies, Integer scanIntervalMinutes,
                Integer updateIntervalMinutes, Integer maxDiscoveryPages,
                String autoImportFormat, List<ContentMarkerPayload> contentMarkers,
                Boolean respectRobotsTxt, String themeColor) {
            this(siteName, siteCode, baseUrl, homeUrl, enabled, autoScan, autoCrawl,
                    autoUpdate, autoImportLibrary, requestIntervalMillis, randomDelayMillis,
                    maxConcurrency, encoding, proxies, scanIntervalMinutes,
                    updateIntervalMinutes, maxDiscoveryPages, autoImportFormat,
                    contentMarkers, respectRobotsTxt, themeColor, null, List.of(), null);
        }

        public SitePayload(
                String siteName, String siteCode, String baseUrl, String homeUrl,
                Boolean enabled, Boolean autoScan, Boolean autoCrawl, Boolean autoUpdate,
                Boolean autoImportLibrary, Integer requestIntervalMillis,
                Integer randomDelayMillis, Integer maxConcurrency, String encoding,
                List<ProxyPayload> proxies, Integer scanIntervalMinutes,
                Integer updateIntervalMinutes, Integer maxDiscoveryPages,
                String autoImportFormat, List<ContentMarkerPayload> contentMarkers,
                Boolean respectRobotsTxt) {
            this(siteName, siteCode, baseUrl, homeUrl, enabled, autoScan, autoCrawl,
                    autoUpdate, autoImportLibrary, requestIntervalMillis, randomDelayMillis,
                    maxConcurrency, encoding, proxies, scanIntervalMinutes,
                    updateIntervalMinutes, maxDiscoveryPages, autoImportFormat,
                    contentMarkers, respectRobotsTxt, null, null, List.of(), null);
        }
    }

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
            List<ContentMarkerPayload> contentMarkers, boolean respectRobotsTxt,
            CrawlerProtectionView protection, String themeColor,
            int maxRequestIntervalMillis, List<SiteAccessWindowPayload> blockedAccessWindows,
            int cooldownFailureThreshold, SiteFreezeView manualFreeze) {
        public SiteView(Long id, String siteName, String siteCode, String baseUrl,
                String homeUrl, boolean enabled, boolean autoScan, boolean autoCrawl,
                boolean autoUpdate, boolean autoImportLibrary, int requestIntervalMillis,
                int randomDelayMillis, int maxConcurrency, String encoding, String proxy,
                List<ProxyPayload> proxies, int scanIntervalMinutes,
                int updateIntervalMinutes, int maxDiscoveryPages, String autoImportFormat,
                String status, long bookCount, RulePayload rule, Integer ruleVersion,
                Long activeRuleId, long ruleCount, LocalDateTime lastScanAt,
                LocalDateTime lastUpdateAt, LocalDateTime lastHealthCheckAt,
                String healthMessage, LocalDateTime createdAt,
                List<ContentMarkerPayload> contentMarkers, boolean respectRobotsTxt,
                CrawlerProtectionView protection, String themeColor) {
            this(id, siteName, siteCode, baseUrl, homeUrl, enabled, autoScan, autoCrawl,
                    autoUpdate, autoImportLibrary, requestIntervalMillis, randomDelayMillis,
                    maxConcurrency, encoding, proxy, proxies, scanIntervalMinutes,
                    updateIntervalMinutes, maxDiscoveryPages, autoImportFormat, status,
                    bookCount, rule, ruleVersion, activeRuleId, ruleCount, lastScanAt,
                    lastUpdateAt, lastHealthCheckAt, healthMessage, createdAt, contentMarkers,
                    respectRobotsTxt, protection, themeColor,
                    (int) Math.min(Integer.MAX_VALUE,
                            (long) requestIntervalMillis + Math.max(0, randomDelayMillis)),
                    List.of(), 5, new SiteFreezeView(false, null, 60));
        }

        public SiteView(Long id, String siteName, String siteCode, String baseUrl,
                String homeUrl, boolean enabled, boolean autoScan, boolean autoCrawl,
                boolean autoUpdate, boolean autoImportLibrary, int requestIntervalMillis,
                int randomDelayMillis, int maxConcurrency, String encoding, String proxy,
                List<ProxyPayload> proxies, int scanIntervalMinutes,
                int updateIntervalMinutes, int maxDiscoveryPages, String autoImportFormat,
                String status, long bookCount, RulePayload rule, Integer ruleVersion,
                Long activeRuleId, long ruleCount, LocalDateTime lastScanAt,
                LocalDateTime lastUpdateAt, LocalDateTime lastHealthCheckAt,
                String healthMessage, LocalDateTime createdAt,
                List<ContentMarkerPayload> contentMarkers, boolean respectRobotsTxt,
                CrawlerProtectionView protection) {
            this(id, siteName, siteCode, baseUrl, homeUrl, enabled, autoScan, autoCrawl,
                    autoUpdate, autoImportLibrary, requestIntervalMillis, randomDelayMillis,
                    maxConcurrency, encoding, proxy, proxies, scanIntervalMinutes,
                    updateIntervalMinutes, maxDiscoveryPages, autoImportFormat, status,
                    bookCount, rule, ruleVersion, activeRuleId, ruleCount, lastScanAt,
                    lastUpdateAt, lastHealthCheckAt, healthMessage, createdAt, contentMarkers,
                    respectRobotsTxt, protection, com.aibook.model.entity.CrawlerSite.DEFAULT_THEME_COLOR);
        }
    }

    public record SiteFreezeRequest(@NotNull Boolean frozen,
            @NotNull @Min(0) @Max(525600) Integer durationMinutes) { }

    public record SiteFreezeView(boolean frozen, Instant until, int durationMinutes) { }

    public record CrawlerProtectionView(boolean coolingDown, Instant blockedUntil, String reason,
            String pageUrl, int consecutiveFailures, long adaptiveDelayMillis) {
        public CrawlerProtectionView(boolean coolingDown, Instant blockedUntil, String reason,
                int consecutiveFailures, long adaptiveDelayMillis) {
            this(coolingDown, blockedUntil, reason, null, consecutiveFailures, adaptiveDelayMillis);
        }
    }

    public record RobotsTxtView(String url, int statusCode, String content, Instant fetchedAt) { }

    public record ManualCrawlRequest(@NotBlank String url, Boolean autoImportEnabled,
            List<@Pattern(regexp = "(?i)STRUCTURED|TXT|EPUB") String> autoImportFormats) { }

    public record AutoImportRequest(@NotEmpty @Size(max = 1000) List<@NotNull Long> ids,
            @NotNull Boolean enabled,
            @NotEmpty List<@Pattern(regexp = "(?i)STRUCTURED|TXT|EPUB") String> formats) { }
    public record DiscoveryPagePayload(
            @NotBlank @Size(max = 100) String pageName,
            @NotBlank @Size(max = 1000) String pageUrl,
            Boolean autoScanEnabled,
            @Min(5) @Max(10080) Integer scanIntervalMinutes,
            @Min(1) Integer maxPages) { }
    public record DiscoveryPageView(Long id, Long siteId, String pageName, String pageUrl,
            boolean autoScanEnabled, int scanIntervalMinutes, int maxPages,
            LocalDateTime lastScanAt, LocalDateTime createdAt) { }
    public record ExportRequest(@NotEmpty List<@Pattern(regexp = "(?i)TXT|EPUB") String> formats) { }
    public record ImportRequest(
            @NotEmpty List<@Pattern(regexp = "(?i)STRUCTURED|TXT|EPUB") String> formats) { }
    public record BookCrawlStatusRequest(
            @NotBlank @Pattern(regexp = "DISCOVERED|WAITING|PAUSED|PARTIAL_SUCCESS|COMPLETED|FAILED") String status,
            Boolean autoUpdateEnabled) { }
    public record LibrarySyncRequest(@NotNull Boolean enabled) { }
    public record FavoriteRequest(@NotNull Boolean favorite) { }
    public record BookListSelectionRequest(
            @NotNull @Size(max = 100) List<@NotNull Long> bookListIds) { }
    public record TaskUpdateRequest(@NotNull CrawlerTask.Priority priority) { }
    public record TaskBatchRequest(
            @NotEmpty @Size(max = 200) List<@NotBlank String> taskIds,
            @NotBlank @Pattern(regexp = "pause|resume|cancel|delete|priority") String action,
            CrawlerTask.Priority priority) { }
    public record TaskBatchResult(int affectedCount) { }
    public record TaskQueueOrderRequest(@NotEmpty List<@NotBlank String> taskIds) { }
    public record TaskQueueSettingsRequest(@NotNull @Min(1) @Max(16) Integer maxConcurrentTasks) { }
    public record TaskQueueSettingsView(int maxConcurrentTasks, int runningCount, int queuedCount) { }
    public record TaskQueuePayload(
            @NotNull @Min(1) @Max(16) Integer maxConcurrentTasks,
            @NotNull @Min(0) @Max(3600) Integer taskIntervalSeconds,
            @Size(max = 100) String queueName) { }
    public record TaskQueueCreateRequest(Long siteId, @Size(max = 100) String queueName) { }
    public record TaskQueueReorderRequest(@NotEmpty List<@NotNull Long> queueIds) { }
    public record TaskQueueAssignmentRequest(@NotNull Long queueId) { }
    public record QueueExecutorProxyPayload(
            @NotNull Long proxyConfigId,
            @Min(10) @Max(604800) Integer cooldownSeconds) { }
    public record QueueExecutorPayload(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            Boolean enabled,
            @NotNull CrawlerQueueExecutor.ProxyMode proxyMode,
            @NotNull CrawlerQueueExecutor.SelectionStrategy selectionStrategy,
            @Min(10) @Max(604800) Integer defaultProxyCooldownSeconds,
            @Valid List<@NotNull QueueExecutorProxyPayload> proxies) { }
    public record QueueExecutorProxyView(
            Long proxyConfigId, String proxyName, int sortOrder,
            Integer cooldownSeconds, java.time.Instant coolingUntil,
            boolean available, int consecutiveFailures, String freezeReason) { }
    public record QueueExecutorView(
            Long id, Long queueId, String name, String description,
            boolean defaultExecutor, boolean enabled, String proxyMode, String selectionStrategy,
            Integer defaultProxyCooldownSeconds,
            List<QueueExecutorProxyView> proxies,
            int availableProxyCount, java.time.Instant nextAvailableAt,
            List<QueueExecutorProxyStateView> proxyStates) { }
    public record QueueExecutorProxyStateView(String proxyKey, String proxyName,
            boolean available, int consecutiveFailures, java.time.Instant coolingUntil,
            String freezeReason) { }
    public record QueueProxyOptionView(
            Long id, String name, boolean effectiveEnabled) { }
    public record TaskQueueView(
            Long id, Long siteId, String siteName, String queueName,
            int maxConcurrentTasks, int taskIntervalSeconds,
            int runningCount, int waitingCount, int pausedCount,
            int activeTaskCount, int progressPercent,
            LocalDateTime lastTaskStartedAt, String siteThemeColor) { }
    public record BatchBookRequest(@NotEmpty List<@NotNull Long> bookIds) { }
    public record BatchBookStatusRequest(
            @NotEmpty @Size(max = 200) List<@NotNull Long> bookIds,
            @NotBlank @Pattern(regexp = "DISCOVERED|WAITING|PAUSED|PARTIAL_SUCCESS|COMPLETED|FAILED") String status) { }
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

    public record SiteConfigurationPayload(
            @Min(1) int schemaVersion,
            @NotBlank @Pattern(regexp = "AIBOOK_CRAWLER_SITE") String type,
            @Valid @NotNull SitePayload site,
            @Size(max = 100) List<@Valid @NotNull RuleSaveRequest> rules,
            @Size(max = 100) List<@Valid @NotNull DiscoveryPagePayload> discoveryPages) { }

    public record BookView(Long id, Long siteId, String siteName, String externalBookId,
            String bookUrl, String bookName, String author, String coverUrl, String description,
            String category, List<String> tags, String bookStatus, String latestChapter,
            Long discoveryPageId, String discoveryPageName, int chapterCount,
            int crawledChapterCount, int pendingReleaseChapterCount, int failedChapterCount,
            String crawlStatus,
            String discoveryStatus, String importStatus, boolean autoUpdateEnabled,
            boolean autoSyncLibrary,
            boolean favorite, List<Long> bookListIds,
            Long libraryBookId, LocalDateTime discoverTime,
            LocalDateTime lastCrawlStartedAt, LocalDateTime lastCrawlTime,
            LocalDateTime createdAt, boolean suspectedDuplicate, String siteThemeColor,
            boolean libraryHasStructuredChapters, boolean autoImportEnabled,
            List<String> autoImportFormats) { }

    public record ChapterContentSaveRequest(@NotNull @Size(max = 2_000_000) String content,
            Boolean syncLibrary) { }

    public record ChapterView(Long id, int chapterIndex, String chapterName, String chapterUrl,
            int wordCount, String crawlStatus, String accessStatus, int retryCount,
            String errorMessage, LocalDateTime crawlTime, LocalDateTime createdAt,
            LocalDateTime crawlStartedAt, LocalDateTime crawlFinishedAt) { }

    public record ChapterFocusView(ChapterView chapter, int page) { }

    public record CrawlerLogView(
            Long id, String description, String details, LocalDateTime createdAt) { }

    public record SiteActivityView(Long id, String eventType, String taskId,
            LocalDateTime createdAt, String description, String details) { }

    public record ScanBookResultView(Long id, Long bookId, String bookName, String bookUrl,
            String resultStatus, String errorMessage, LocalDateTime createdAt) { }

    public record TaskView(String id, String type, String status, String priority,
            Long siteId, String siteName, Long discoveryPageId, String discoveryPageName,
            Integer scanMaxPages, int scannedPageCount, int progressPercent, Long bookId, String bookName,
            boolean favorite,
            int totalCount, int successCount, int newBookCount,
            int duplicateCount, int failedCount, int waitingCount, String currentChapter,
            long averageRequestMillis, String errorMessage, LocalDateTime startedAt,
            LocalDateTime finishedAt, LocalDateTime createdAt, String siteThemeColor,
            Long queueId) { }

    public record TaskExecutionView(String executorName, String proxyMode,
            String mihomoProxyUrl, String mihomoGroupName, String mihomoNode) { }

    public record ExecutorLogView(Long id, String taskId, String executorName,
            String siteName, String bookName, String chapterName, String description,
            String details, String proxyName, String proxyNode, boolean failed,
            LocalDateTime createdAt) { }

    public record ExportView(Long id, String format, long fileSize, String fileHash,
            LocalDateTime createdAt) { }

    public record DashboardView(long siteCount, long enabledSiteCount, long bookCount,
            long completedBookCount, long crawlingBookCount, long failedBookCount,
            long todayNewBooks, long todayNewChapters, long readyToImportCount,
            long importedCount, List<TaskView> recentTasks) { }

    public record DailyStatisticsView(LocalDate date, long newChapters,
            long successfulChapters, long newBooks, long finishedTasks,
            long successfulTasks, long successfulBooks, long failedTasks) { }

    public record SiteContributionView(Long siteId, String siteName,
            long newBooks, long newChapters) { }

    public record CrawlerFunnelView(long discoveredBooks, long taskedBooks,
            long completedBooks, long importedBooks) { }

    public record DashboardStatisticsView(int days, List<DailyStatisticsView> daily,
            List<SiteContributionView> siteContributions, CrawlerFunnelView funnel) { }

    public record ChapterAttemptDailyView(LocalDate date, long attempts,
            long collectionMillis, long fixedWaitMillis, long randomWaitMillis,
            long otherWaitMillis, long totalElapsedMillis) { }

    public record ChapterAttemptView(Long id, String taskId, String siteName,
            String siteThemeColor,
            String bookName, String chapterName, Integer chapterIndex,
            LocalDateTime attemptStartedAt, LocalDateTime attemptFinishedAt,
            long collectionMillis, long fixedWaitMillis, long randomWaitMillis,
            long otherWaitMillis, long totalElapsedMillis, String outcome) { }

    public record ChapterAttemptStatisticsView(int days,
            List<ChapterAttemptDailyView> daily,
            org.springframework.data.domain.Page<ChapterAttemptView> attempts) { }
}
