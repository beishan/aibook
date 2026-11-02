package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerBlacklistScreenDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.util.CrawlerAutoBlacklist;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerBlacklistScreenService implements AutoCloseable {
    private final CrawlerBlacklistScreenJobRepository jobs;
    private final CrawlerBlacklistScreenResultRepository results;
    private final CrawlerBookRepository books;
    private final CrawlerSiteRepository sites;
    private final PlatformTransactionManager transactionManager;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16));

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        jobs.interruptUnfinished();
    }

    /** Save before dispatch: workers must never race an uncommitted job. */
    public synchronized JobView submit(User user, Request request) {
        String scope = normalizeScope(request.scope());
        var latest = jobs.findFirstByUserOrderByCreatedAtDesc(user);
        if (latest.isPresent() && "RUNNING".equals(latest.get().getStatus())) {
            String activeScope = "DISCOVERED".equals(latest.get().getScope()) ? "发现书籍" : "采集书籍";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "已有" + activeScope + "筛选正在执行，请到对应页面查看进度");
        }
        List<CrawlerSite> owned = sites.findByUserOrderByCreatedAtDesc(user);
        if (request.siteId() != null && owned.stream().noneMatch(s -> s.getId().equals(request.siteId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "网站不存在");
        }
        Map<Long, CrawlerAutoBlacklist.Configuration> configurations = new HashMap<>();
        for (CrawlerSite site : owned) {
            if (request.siteId() != null && !request.siteId().equals(site.getId())) continue;
            var configuration = CrawlerAutoBlacklist.decode(site.getAutoBlacklistJson());
            if (configuration.enabled()
                    && (!configuration.keywords().isEmpty() || !configuration.tags().isEmpty())) {
                configurations.put(site.getId(), configuration);
            }
        }
        if (configurations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选范围没有已启用的非空自动黑名单规则，请先到采集网站配置");
        }
        long upperId = books.blacklistScreenUpperId(user);
        var job = CrawlerBlacklistScreenJob.builder().user(user).siteId(request.siteId()).scope(scope)
                .total(books.countBlacklistScreenCandidates(
                        user, configurations.keySet(), upperId, "DISCOVERED".equals(scope))).build();
        jobs.save(job);
        JobView initial = view(job);
        try {
            executor.execute(() -> process(job, configurations, upperId));
        } catch (RejectedExecutionException exception) {
            fail(job, "筛选队列已满，请稍后重新开始");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, job.getErrorMessage());
        }
        return initial;
    }

    @Transactional(readOnly = true)
    public JobView latest(User user, String scope) {
        return jobs.latestForScope(user, normalizeScope(scope), PageRequest.of(0, 1)).stream()
                .findFirst().map(this::view).orElse(null);
    }

    @Transactional(readOnly = true)
    public JobView progress(User user, String id) {
        return view(owned(user, id));
    }

    @Transactional(readOnly = true)
    public Page<Item> page(User user, String id, int page, int size) {
        owned(user, id);
        var rows = results.findByJobIdOrderByIdAsc(id, PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));
        Map<Long, CrawlerBook> current = books.findAllById(rows.stream()
                .map(CrawlerBlacklistScreenResult::getBookId).toList()).stream()
                .filter(book -> book.getSite().getUser().getId().equals(user.getId()))
                .collect(Collectors.toMap(CrawlerBook::getId, Function.identity()));
        return rows.map(row -> {
            CrawlerBook book = current.get(row.getBookId());
            return new Item(row.getBookId(), row.getBookName(), row.getAuthor(), row.getSiteName(),
                    row.getReason(), book != null
                            && book.getDiscoveryStatus() == CrawlerBook.DiscoveryStatus.BLACKLISTED, book != null);
        });
    }

    @Transactional
    public ApplyResult apply(User user, String id, ApplyRequest request) {
        var job = owned(user, id);
        if (!"COMPLETED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "筛选尚未成功完成，请完成后再加入黑名单");
        }
        if (request.all()) {
            return new ApplyResult(books.blacklistAllScreenResults(user, id, LocalDateTime.now()));
        }
        List<Long> ids = request.bookIds() == null ? List.of() : request.bookIds().stream().distinct().toList();
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先选择本页书籍");
        }
        List<Long> matches = results.matchedIds(id, ids);
        if (matches.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选书籍不属于本次筛选结果");
        }
        return new ApplyResult(books.updateDiscoveryStatus(matches, user,
                CrawlerBook.DiscoveryStatus.BLACKLISTED, true, LocalDateTime.now()));
    }

    void process(CrawlerBlacklistScreenJob job,
            Map<Long, CrawlerAutoBlacklist.Configuration> configurations, long upperId) {
        try {
            long afterId = 0;
            while (!Thread.currentThread().isInterrupted()) {
                List<CrawlerBook> batch = books.blacklistScreenCandidates(job.getUser(),
                        configurations.keySet(), afterId, upperId, "DISCOVERED".equals(job.getScope()), PageRequest.of(0, 200));
                if (batch.isEmpty()) {
                    job.setStatus("COMPLETED");
                    job.setFinishedAt(LocalDateTime.now());
                    jobs.save(job);
                    return;
                }
                List<CrawlerBlacklistScreenResult> matches = new ArrayList<>();
                for (CrawlerBook book : batch) {
                    afterId = book.getId();
                    String reason = CrawlerAutoBlacklist.reason(configurations.get(book.getSite().getId()), book);
                    if (reason != null) {
                        matches.add(CrawlerBlacklistScreenResult.builder().job(job).bookId(book.getId())
                                .bookName(book.getBookName()).author(book.getAuthor())
                                .siteName(book.getSite().getSiteName()).reason(reason).build());
                    }
                }
                long previousProcessed = job.getProcessed();
                long previousMatched = job.getMatched();
                job.setProcessed(previousProcessed + batch.size());
                job.setMatched(previousMatched + matches.size());
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        results.saveAll(matches);
                        jobs.save(job);
                    });
                } catch (RuntimeException exception) {
                    job.setProcessed(previousProcessed);
                    job.setMatched(previousMatched);
                    throw exception;
                }
            }
            fail(job, "筛选已中断，请重新开始");
        } catch (Exception exception) {
            log.warn("黑名单筛选失败: job={}", job.getId(), exception);
            fail(job, "筛选失败，请重新开始；详情见服务端日志");
        }
    }

    private void fail(CrawlerBlacklistScreenJob job, String message) {
        job.setStatus("FAILED");
        job.setErrorMessage(message);
        job.setFinishedAt(LocalDateTime.now());
        jobs.save(job);
    }

    private CrawlerBlacklistScreenJob owned(User user, String id) {
        return jobs.findByIdAndUser(id, user).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "筛选记录不存在"));
    }

    private JobView view(CrawlerBlacklistScreenJob job) {
        return new JobView(job.getId(), job.getSiteId(), job.getStatus(), job.getTotal(),
                job.getProcessed(), job.getMatched(), job.getErrorMessage(), job.getCreatedAt(),
                normalizeScope(job.getScope()));
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.equals("MANAGED")) return "MANAGED";
        if (scope.equals("DISCOVERED")) return "DISCOVERED";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的书籍筛选范围");
    }

    @PreDestroy
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
