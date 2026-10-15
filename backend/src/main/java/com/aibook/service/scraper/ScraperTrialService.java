package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 独立来源试验默认只查询；豆瓣候选经详情预览与显式确认后才允许补全书籍。 */
@Service
@RequiredArgsConstructor
public class ScraperTrialService {
    private final List<MetadataScraper> scrapers;
    @org.springframework.beans.factory.annotation.Autowired
    private ScraperProxyService proxyService;
    private final Cache<String, Trial> trials = Caffeine.newBuilder()
            .maximumSize(100).expireAfterWrite(Duration.ofMinutes(30)).build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "scraper-trial");
                thread.setDaemon(true);
                return thread;
            });

    public String start(String configKey, Book original, Long userId) {
        return start(configKey, original, userId, false);
    }

    public String startManual(String configKey, Book original, Long userId) {
        return start(configKey, original, userId, true);
    }

    private synchronized String start(String configKey, Book original, Long userId, boolean manual) {
        MetadataScraper scraper = scrapers.stream()
                .filter(item -> item.getConfigKey().equals(configKey)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "刮削配置不存在"));
        if (trials.asMap().values().stream().anyMatch(task ->
                Objects.equals(task.userId, userId) && !task.snapshot().finished())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有试刮削正在执行，请等待完成");
        }
        // 仅传递查询需要的标量，避免后台线程访问持久化实体或懒加载关联。
        Book query = new Book();
        query.setTitle(original.getTitle());
        query.setAuthor(original.getAuthor());
        query.setIsbn(original.getIsbn());
        Trial trial = new Trial(userId, original.getId(), scraper, manual);
        String id = UUID.randomUUID().toString();
        trial.log("已排队：" + scraper.getName() + " / " + query.getTitle());
        trials.put(id, trial);
        try {
            executor.execute(() -> run(trial, scraper, query));
        } catch (RejectedExecutionException exception) {
            trials.invalidate(id);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "试刮削繁忙，请稍后重试");
        }
        return id;
    }

    public TrialSnapshot get(String id, Long userId) {
        return ownedTrial(id, userId).snapshot();
    }

    public TrialSnapshot getManual(String id, Long userId) {
        return manualTrial(id, userId).snapshot();
    }

    private Trial manualTrial(String id, Long userId) {
        Trial trial = ownedTrial(id, userId);
        if (!trial.manual) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "手动刮削任务不存在或已过期");
        return trial;
    }

    public TrialSnapshot selectManualCandidate(String id, Long userId, String candidateId) {
        manualTrial(id, userId);
        return selectCandidate(id, userId, candidateId);
    }

    @FunctionalInterface
    public interface ManualApply {
        MetadataScrapingService.ScrapeResult apply(Long bookId, MetadataScraper.BookMetadata metadata, String source);
    }

    public MetadataScrapingService.ScrapeResult confirmManual(String id, Long userId, ManualApply apply) {
        Trial trial = manualTrial(id, userId);
        synchronized (trial) {
            boolean reviewReady = "REVIEW_REQUIRED".equals(trial.status)
                    && trial.selectedCandidateId != null;
            boolean singleReady = !(trial.scraper instanceof DoubanScraper) && "MATCHED".equals(trial.status);
            if (trial.applied || trial.metadata == null || !(reviewReady || singleReady)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "请先查询并核对元信息，或该结果已经保存");
            }
            var result = apply.apply(trial.bookId, trial.metadata, trial.scraper.getName());
            markApplied(trial, result);
            return result;
        }
    }

    private void markApplied(Trial trial, MetadataScrapingService.ScrapeResult result) {
        if (!result.isSuccess()) throw new ResponseStatusException(HttpStatus.CONFLICT, "元信息保存未完成，请重试");
        trial.applied = true;
        trial.status = "APPLIED";
        trial.updatedFields = List.copyOf(result.getUpdatedFields());
        trial.log("已人工确认并保存书籍元信息。" + result.getMessage());
    }

    private Trial ownedTrial(String id, Long userId) {
        Trial trial = trials.getIfPresent(id);
        if (trial == null || !Objects.equals(trial.userId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "试刮削不存在或已过期");
        }
        return trial;
    }

    /** 候选编号只能来自该账户任务中的服务端候选集合，不能传入任意 URL。 */
    public synchronized TrialSnapshot selectCandidate(String id, Long userId, String candidateId) {
        Trial trial = ownedTrial(id, userId);
        synchronized (trial) {
            if (!(trial.scraper instanceof DoubanScraper douban)
                    || trial.candidates.stream().noneMatch(item -> item.id().equals(candidateId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "候选不存在");
            }
            if (!trial.snapshot().finished() || trial.applied) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务正在执行或已经应用");
            }
            if (trials.asMap().values().stream().anyMatch(other -> other != trial
                    && Objects.equals(other.userId, userId) && !other.snapshot().finished())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "已有试刮削正在执行");
            }
            String previousStatus = trial.status;
            try {
                trial.status = "RUNNING";
                trial.ended = 0;
                executor.execute(() -> {
                    try (var diagnostics = ScrapeDiagnostics.open(trial::log)) {
                        diagnostics.stage("候选详情");
                        trial.log("正在加载豆瓣候选 " + candidateId + " 的完整元信息");
                        try {
                            var metadata = douban.getBookDetail(candidateId);
                            if (hasResult(metadata) && proxyService != null) proxyService.taskSucceeded("douban");
                            synchronized (trial) {
                                trial.selectedCandidateId = candidateId;
                                trial.finish(hasResult(metadata) ? "REVIEW_REQUIRED" : "NO_RESULT",
                                        hasResult(metadata) ? "候选详情已加载，请核对后确认补全；尚未写入书库" : "候选详情获取失败",
                                        metadata, diagnostics);
                            }
                        } catch (Exception exception) {
                            ScrapeDiagnostics.exception(exception);
                            trial.finish("FAILED", "候选详情获取失败", null, diagnostics);
                        }
                    }
                });
            } catch (RejectedExecutionException exception) {
                trial.status = previousStatus;
                trial.ended = System.currentTimeMillis();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "试刮削繁忙，请稍后重试");
            }
            return trial.snapshot();
        }
    }

    public MetadataScrapingService.ScrapeResult confirmCandidate(
            String id, Long userId, String candidateId,
            java.util.function.BiFunction<Long, MetadataScraper.BookMetadata,
                    MetadataScrapingService.ScrapeResult> apply) {
        Trial trial = ownedTrial(id, userId);
        synchronized (trial) {
            if (!"REVIEW_REQUIRED".equals(trial.status) || trial.applied || trial.metadata == null
                    || !Objects.equals(candidateId, trial.selectedCandidateId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "请先加载并核对当前候选详情，或该候选已经应用");
            }
            var result = apply.apply(trial.bookId, trial.metadata);
            markApplied(trial, result);
            return result;
        }
    }

    private void run(Trial trial, MetadataScraper scraper, Book book) {
        try (ScrapeDiagnostics diagnostics = ScrapeDiagnostics.open(trial::log)) {
            try {
                trial.log("检查来源支持情况；使用服务器已保存配置，忽略启用开关及缓存");
                if (!scraper.supports(book)) {
                    ScrapeDiagnostics.report("google".equals(scraper.getConfigKey())
                                    ? ScrapeDiagnostics.Code.CONFIG_MISSING : ScrapeDiagnostics.Code.UNSUPPORTED,
                            "google".equals(scraper.getConfigKey())
                                    ? "服务器未取得有效的 Google Books API Key。"
                                    : "此来源不支持当前书名的查询条件。");
                    trial.finish("UNSUPPORTED", "配置或来源支持检查未通过", null, diagnostics);
                    return;
                }
                if (scraper instanceof DoubanScraper douban) {
                    diagnostics.stage("豆瓣候选搜索");
                    trial.log("正在按书名搜索备选元信息：" + MetadataScraper.cleanSearchTitle(book.getTitle()));
                    var candidates = douban.searchCandidates(book.getTitle());
                    if (!candidates.isEmpty() && proxyService != null) proxyService.taskSucceeded("douban");
                    synchronized (trial) {
                        trial.candidates = List.copyOf(candidates);
                        trial.finish(candidates.isEmpty() ? "NO_RESULT" : "AWAITING_SELECTION",
                                candidates.isEmpty() ? "未取得候选书籍"
                                        : "找到 " + candidates.size() + " 个候选，请选择后加载详情；未写入书库",
                                null, diagnostics);
                    }
                    return;
                }
                MetadataScraper.BookMetadata result = null;
                if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
                    diagnostics.stage("ISBN 查询");
                    trial.log("正在按 ISBN 查询：" + book.getIsbn());
                    result = scraper.scrapeByIsbn(book.getIsbn());
                }
                if (!hasResult(result)) {
                    diagnostics.stage("书名查询");
                    trial.log("正在按书名查询：" + MetadataScraper.cleanSearchTitle(book.getTitle()));
                    book.setIsbn(null);
                    result = scraper.scrape(book);
                }
                if (hasResult(result)) {
                    if (proxyService != null) proxyService.taskSucceeded(scraper.getConfigKey());
                    trial.finish("MATCHED", "已取得元信息，请核对是否为目标书籍；未写入书库", result, diagnostics);
                } else {
                    trial.finish("NO_RESULT", "未取得元信息", null, diagnostics);
                }
            } catch (Exception exception) {
                ScrapeDiagnostics.exception(exception);
                trial.finish("FAILED", "试刮削失败", null, diagnostics);
            }
        }
    }

    static boolean hasResult(MetadataScraper.BookMetadata value) {
        return value != null && (List.of(
                Objects.toString(value.getTitle(), ""), Objects.toString(value.getAuthor(), ""),
                Objects.toString(value.getIsbn(), ""), Objects.toString(value.getDescription(), ""),
                Objects.toString(value.getPublisher(), ""), Objects.toString(value.getPublishDate(), ""),
                Objects.toString(value.getCoverUrl(), ""), Objects.toString(value.getLanguage(), ""))
                .stream().anyMatch(text -> !text.isBlank()) || value.getRating() != null
                || (value.getTags() != null && java.util.Arrays.stream(value.getTags())
                        .anyMatch(tag -> tag != null && !tag.isBlank())));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record TrialLog(Instant time, String message) {}
    public record TrialSnapshot(String status, boolean finished, long elapsedMs,
            List<TrialLog> logs, MetadataScraper.BookMetadata metadata,
            ScrapeDiagnostics.Failure failure, List<ScrapeDiagnostics.Failure> diagnostics,
            List<DoubanScraper.Candidate> candidates, String selectedCandidateId,
            boolean applied, List<String> updatedFields) {}

    private static final class Trial {
        private final Long userId;
        private final Long bookId;
        private final MetadataScraper scraper;
        private final boolean manual;
        private List<DoubanScraper.Candidate> candidates = List.of();
        private String selectedCandidateId;
        private boolean applied;
        private List<String> updatedFields = List.of();
        private final long started = System.currentTimeMillis();
        private final List<TrialLog> logs = new ArrayList<>();
        private String status = "RUNNING";
        private long ended;
        private MetadataScraper.BookMetadata metadata;
        private ScrapeDiagnostics.Failure failure;
        private List<ScrapeDiagnostics.Failure> diagnostics = List.of();

        private Trial(Long userId, Long bookId, MetadataScraper scraper, boolean manual) {
            this.userId = userId;
            this.bookId = bookId;
            this.scraper = scraper;
            this.manual = manual;
        }

        synchronized void log(String message) {
            if (logs.size() == 500) logs.remove(0);
            logs.add(new TrialLog(Instant.now(), message));
        }

        synchronized void finish(String status, String message, MetadataScraper.BookMetadata metadata,
                ScrapeDiagnostics context) {
            failure = List.of("MATCHED", "AWAITING_SELECTION", "REVIEW_REQUIRED").contains(status)
                    ? null : context.primaryFailure();
            List<ScrapeDiagnostics.Failure> combined = new ArrayList<>(diagnostics);
            combined.addAll(context.failures());
            diagnostics = combined.stream().limit(100).toList();
            log(failure == null ? message : message + "：" + failure.label() + "；"
                    + failure.detail() + " " + failure.suggestion());
            this.metadata = metadata;
            this.status = status;
            this.ended = System.currentTimeMillis();
        }

        synchronized TrialSnapshot snapshot() {
            return new TrialSnapshot(status, ended != 0,
                    (ended == 0 ? System.currentTimeMillis() : ended) - started,
                    List.copyOf(logs), metadata, failure, diagnostics, candidates, selectedCandidateId,
                    applied, updatedFields);
        }
    }
}
