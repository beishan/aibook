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

/** 独立来源试验：只查询远端，不使用元信息缓存，不修改或保存书籍。 */
@Service
@RequiredArgsConstructor
public class ScraperTrialService {
    private final List<MetadataScraper> scrapers;
    private final Cache<String, Trial> trials = Caffeine.newBuilder()
            .maximumSize(100).expireAfterWrite(Duration.ofMinutes(30)).build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "scraper-trial");
                thread.setDaemon(true);
                return thread;
            });

    public synchronized String start(String configKey, Book original, Long userId) {
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
        Trial trial = new Trial(userId);
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
        Trial trial = trials.getIfPresent(id);
        if (trial == null || !Objects.equals(userId, trial.userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "试刮削不存在或已过期");
        }
        return trial.snapshot();
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
            ScrapeDiagnostics.Failure failure, List<ScrapeDiagnostics.Failure> diagnostics) {}

    private static final class Trial {
        private final Long userId;
        private final long started = System.currentTimeMillis();
        private final List<TrialLog> logs = new ArrayList<>();
        private String status = "RUNNING";
        private long ended;
        private MetadataScraper.BookMetadata metadata;
        private ScrapeDiagnostics.Failure failure;
        private List<ScrapeDiagnostics.Failure> diagnostics = List.of();

        private Trial(Long userId) {
            this.userId = userId;
        }

        synchronized void log(String message) {
            logs.add(new TrialLog(Instant.now(), message));
        }

        synchronized void finish(String status, String message, MetadataScraper.BookMetadata metadata,
                ScrapeDiagnostics context) {
            failure = "MATCHED".equals(status) ? null : context.primaryFailure();
            diagnostics = context.failures();
            log(failure == null ? message : message + "：" + failure.label() + "；"
                    + failure.detail() + " " + failure.suggestion());
            this.metadata = metadata;
            this.status = status;
            this.ended = System.currentTimeMillis();
        }

        synchronized TrialSnapshot snapshot() {
            return new TrialSnapshot(status, ended != 0,
                    (ended == 0 ? System.currentTimeMillis() : ended) - started,
                    List.copyOf(logs), metadata, failure, diagnostics);
        }
    }
}
