package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerBatchImportDtos.ItemResult;
import com.aibook.dto.crawler.CrawlerBatchImportDtos.JobView;
import com.aibook.model.entity.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 一次接收整批书籍；单本入库通过独立服务事务处理，失败不回滚其他书籍。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerBatchImportService {
    private final CrawlerExportService exportService;
    private final Cache<String, Job> jobs = Caffeine.newBuilder()
            .maximumSize(256).expireAfterAccess(Duration.ofDays(1)).build();
    private final ConcurrentHashMap<String, Job> activeJobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32));

    public JobView submit(User user, List<Long> bookIds, List<String> formats) {
        List<Long> ids = bookIds.stream().distinct().toList();
        List<String> requestedFormats = List.copyOf(formats);
        Job job = new Job(UUID.randomUUID().toString(), user.getId(), ids.size());
        activeJobs.put(job.id, job);
        try {
            executor.execute(() -> process(job, user, ids, requestedFormats));
        } catch (RejectedExecutionException exception) {
            activeJobs.remove(job.id);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "批量入库队列已满，请稍后重试");
        }
        return job.snapshot();
    }

    public JobView progress(User user, String id) {
        Job job = activeJobs.get(id);
        if (job == null) job = jobs.getIfPresent(id);
        if (job == null || !job.userId.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "批量入库记录不存在或已过期，请刷新书籍状态确认入库结果");
        }
        return job.snapshot();
    }

    private void process(Job job, User user, List<Long> ids, List<String> formats) {
        for (Long id : ids) {
            ItemResult result;
            try {
                Long libraryId = exportService.importLibrary(user, id, formats);
                result = new ItemResult(id, libraryId, true, null);
            } catch (Exception exception) {
                log.warn("批量入库失败: job={}, book={}", job.id, id, exception);
                String reason = exception instanceof ResponseStatusException status
                        && status.getReason() != null ? status.getReason() : "入库失败，请查看服务端日志";
                result = new ItemResult(id, null, false, reason);
            }
            synchronized (job) {
                job.results.add(result);
            }
        }
        jobs.put(job.id, job);
        activeJobs.remove(job.id);
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
    }

    private static final class Job {
        private final String id;
        private final Long userId;
        private final int total;
        private final List<ItemResult> results = new ArrayList<>();

        private Job(String id, Long userId, int total) {
            this.id = id;
            this.userId = userId;
            this.total = total;
        }

        private synchronized JobView snapshot() {
            return new JobView(id, results.size() == total ? "COMPLETED" : "RUNNING",
                    total, results.size(), List.copyOf(results));
        }
    }
}
