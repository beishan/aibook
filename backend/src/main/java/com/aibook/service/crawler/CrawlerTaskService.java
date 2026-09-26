package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.TaskQueueSettingsView;
import com.aibook.dto.crawler.CrawlerDtos.TaskQueuePayload;
import com.aibook.dto.crawler.CrawlerDtos.TaskQueueView;
import com.aibook.dto.crawler.CrawlerDtos.TaskView;
import com.aibook.dto.crawler.CrawlerDtos.ContentMarkerPayload;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.OperationLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerTaskService {
    private static final ObjectMapper CONTENT_MARKER_MAPPER = new ObjectMapper();
    private final CrawlerSiteRepository siteRepository;
    private final CrawlerDiscoveryPageRepository discoveryPageRepository;
    private final CrawlerBookRepository bookRepository;
    private final CrawlerChapterRepository chapterRepository;
    private final CrawlerTaskRepository taskRepository;
    @Autowired
    private CrawlerTaskQueueRepository taskQueueRepository;
    private final CrawlerScanResultRepository scanResultRepository;
    private final CrawlerTaskLogRepository taskLogRepository;
    private final CrawlerManagementService managementService;
    private final OperationLogService operationLogService;
    private final CrawlerExportService exportService;
    private final CrawlerHttpClient httpClient;
    private final List<BookCrawlerParser> parsers;
    private final ApplicationContext applicationContext;
    private final CrawlerSettingsService crawlerSettingsService;
    private final AtomicLong jobSequence = new AtomicLong();
    private final AtomicLong queueOrderSequence = new AtomicLong();
    private final Object[] taskLocks = createTaskLocks();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(), r -> {
        Thread thread = new Thread(r, "crawler-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> activeTaskSites = new ConcurrentHashMap<>();
    private final Map<String, Thread> runningThreads = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;
    private static final List<CrawlerTask.TaskStatus> ACTIVE_STATUSES = List.of(
            CrawlerTask.TaskStatus.WAITING, CrawlerTask.TaskStatus.RUNNING, CrawlerTask.TaskStatus.PAUSED);

    public TaskQueueSettingsView queueSettings() {
        applyConcurrencyLimit(configuredQueueConcurrency());
        int activeCount = executor.getActiveCount();
        int limit = executor.getMaximumPoolSize();
        return new TaskQueueSettingsView(
                limit, Math.min(activeCount, limit),
                taskQueueRepository == null ? executor.getQueue().size()
                        : taskRepository.findByStatusOrderByQueueOrderAsc(CrawlerTask.TaskStatus.WAITING).size());
    }

    public synchronized TaskQueueSettingsView updateQueueSettings(Integer limit) {
        int normalized = crawlerSettingsService.updateMaxConcurrentTasks(limit);
        if (taskQueueRepository != null) {
            List<CrawlerTaskQueue> queues = taskQueueRepository.findAll();
            queues.stream()
                    .filter(queue -> value(queue.getMaxConcurrentTasks(), 1) > normalized)
                    .forEach(queue -> yieldRunningTasksForQueue(queue.getSite().getId(), normalized));
            queues.forEach(queue -> queue.setMaxConcurrentTasks(normalized));
            taskQueueRepository.saveAll(queues);
        } else {
            yieldRunningTasksAbove(normalized);
        }
        applyConcurrencyLimit(configuredQueueConcurrency());
        dispatchWaitingTasks();
        return queueSettings();
    }

    @Transactional(readOnly = true)
    public List<TaskQueueView> taskQueues(User user) {
        List<CrawlerTask> tasks = taskRepository.findByUserAndStatusInOrderByCreatedAtDesc(user, ACTIVE_STATUSES);
        return taskQueueRepository.findBySite_UserOrderBySite_SiteNameAsc(user).stream()
                .map(queue -> taskQueueView(queue, tasks.stream()
                        .filter(task -> Objects.equals(task.getSite().getId(), queue.getSite().getId()))
                        .toList()))
                .toList();
    }

    @Transactional
    public TaskQueueView createTaskQueue(User user, Long siteId) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        if (taskQueueRepository.findBySite(site).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该网站已存在任务队列");
        }
        CrawlerTaskQueue queue = taskQueueRepository.save(CrawlerTaskQueue.builder().site(site)
                .maxConcurrentTasks(crawlerSettingsService.maxConcurrentTasks()).build());
        applyConcurrencyLimit(configuredQueueConcurrency());
        return taskQueueView(queue, List.of());
    }

    @Transactional
    public TaskQueueView updateTaskQueue(User user, Long siteId, TaskQueuePayload payload) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        CrawlerTaskQueue queue = taskQueueRepository.findBySite(site)
                .orElseGet(() -> taskQueueRepository.save(CrawlerTaskQueue.builder().site(site)
                        .maxConcurrentTasks(crawlerSettingsService.maxConcurrentTasks()).build()));
        int previousLimit = queue.getMaxConcurrentTasks();
        queue.setMaxConcurrentTasks(payload.maxConcurrentTasks());
        queue.setTaskIntervalSeconds(payload.taskIntervalSeconds());
        taskQueueRepository.save(queue);
        if (payload.maxConcurrentTasks() < previousLimit) {
            yieldRunningTasksForQueue(siteId, payload.maxConcurrentTasks());
        }
        applyConcurrencyLimit(configuredQueueConcurrency());
        dispatchWaitingTasks();
        List<CrawlerTask> tasks = taskRepository.findByUserAndStatusInOrderByCreatedAtDesc(user, ACTIVE_STATUSES)
                .stream().filter(task -> Objects.equals(task.getSite().getId(), siteId)).toList();
        return taskQueueView(queue, tasks);
    }

    private TaskQueueView taskQueueView(CrawlerTaskQueue queue, List<CrawlerTask> tasks) {
        int running = (int) tasks.stream().filter(task -> task.getStatus() == CrawlerTask.TaskStatus.RUNNING).count();
        int waiting = (int) tasks.stream().filter(task -> task.getStatus() == CrawlerTask.TaskStatus.WAITING).count();
        int paused = (int) tasks.stream().filter(task -> task.getStatus() == CrawlerTask.TaskStatus.PAUSED).count();
        int progress = tasks.isEmpty() ? 0 : (int) Math.round(tasks.stream()
                .mapToInt(this::progress).average().orElse(0));
        return new TaskQueueView(queue.getId(), queue.getSite().getId(), queue.getSite().getSiteName(),
                value(queue.getMaxConcurrentTasks(), 1), value(queue.getTaskIntervalSeconds(), 0),
                running, waiting, paused, tasks.size(), progress, queue.getLastTaskStartedAt());
    }

    private void yieldRunningTasksAbove(int limit) {
        List<CrawlerTask> runningTasks = new ArrayList<>(
                taskRepository.findByStatusIn(List.of(CrawlerTask.TaskStatus.RUNNING)));
        if (runningTasks.size() <= limit) return;
        runningTasks.sort(Comparator
                .comparingInt((CrawlerTask task) -> priorityRank(task.getPriority()))
                .thenComparing(CrawlerTask::getStartedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CrawlerTask::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        int yielded = 0;
        for (CrawlerTask candidate : runningTasks.subList(limit, runningTasks.size())) {
            synchronized (taskLock(candidate.getId())) {
                CrawlerTask task = taskRepository.findById(candidate.getId()).orElse(null);
                if (task == null || task.getStatus() != CrawlerTask.TaskStatus.RUNNING) continue;
                task.setStatus(CrawlerTask.TaskStatus.WAITING);
                task.setQueueOrder(nextQueueOrder());
                taskRepository.save(task);
                if (task.getCrawlerBook() != null) {
                    task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.WAITING);
                    bookRepository.save(task.getCrawlerBook());
                }
                recordCrawlerEvent(task, "并行上限调低，任务转回等待",
                        "新并行上限：" + limit + "；保留当前进度并等待重新调度");
                yielded++;
            }
        }
        log.info("[采集任务] 并行上限调低后收缩运行任务: limit={}, yielded={}", limit, yielded);
    }

    public List<TaskView> queuedTasks(User user) {
        return queuedTasks(user, null);
    }

    public List<TaskView> queuedTasks(User user, Long siteId) {
        if (taskQueueRepository == null) {
            return legacyQueuedTaskEntities(user, siteId).stream()
                    .map(managementService::taskView)
                    .toList();
        }
        return taskRepository.findByUserAndStatusInOrderByCreatedAtDesc(user,
                        List.of(CrawlerTask.TaskStatus.WAITING)).stream()
                .filter(task -> siteId == null || Objects.equals(task.getSite().getId(), siteId))
                .sorted(this::compareCurrentTasks)
                .map(managementService::taskView)
                .toList();
    }

    public List<TaskView> currentTasks(User user) {
        return currentTasks(user, null);
    }

    public List<TaskView> currentTasks(User user, Long siteId) {
        List<CrawlerTask> tasks = new ArrayList<>(
                taskRepository.findByUserAndStatusInOrderByCreatedAtDesc(user, ACTIVE_STATUSES));
        if (taskQueueRepository == null) {
            legacyQueuedTaskEntities(user, siteId).forEach(queued -> {
                if (tasks.stream().noneMatch(task -> task.getId().equals(queued.getId()))) {
                    tasks.add(queued);
                }
            });
        }
        return tasks.stream()
                .filter(task -> siteId == null || Objects.equals(task.getSite().getId(), siteId))
                .sorted(this::compareCurrentTasks)
                .map(managementService::taskView)
                .toList();
    }

    private List<CrawlerTask> legacyQueuedTaskEntities(User user, Long siteId) {
        Map<String, CrawlerTask> queuedTasks = new LinkedHashMap<>();
        taskRepository.findByUserAndStatusInOrderByCreatedAtDesc(user,
                        List.of(CrawlerTask.TaskStatus.WAITING))
                .forEach(task -> queuedTasks.put(task.getId(), task));
        executor.getQueue().stream()
                .filter(CrawlerJob.class::isInstance)
                .map(CrawlerJob.class::cast)
                .map(job -> taskRepository.findById(job.taskId).orElse(null))
                .filter(Objects::nonNull)
                .forEach(task -> queuedTasks.put(task.getId(), task));
        return queuedTasks.values().stream()
                .filter(task -> Objects.equals(task.getUser().getId(), user.getId()))
                .filter(task -> task.getStatus() == CrawlerTask.TaskStatus.WAITING)
                .filter(task -> siteId == null || Objects.equals(task.getSite().getId(), siteId))
                .sorted(this::compareCurrentTasks)
                .toList();
    }

    private int compareCurrentTasks(CrawlerTask left, CrawlerTask right) {
        int statusComparison = Integer.compare(currentTaskStatusRank(left), currentTaskStatusRank(right));
        if (statusComparison != 0) return statusComparison;
        if (left.getStatus() == CrawlerTask.TaskStatus.WAITING) {
            int queueComparison = Comparator.nullsLast(Long::compareTo)
                    .compare(left.getQueueOrder(), right.getQueueOrder());
            if (queueComparison != 0) return queueComparison;
        }
        return Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())
                .compare(left.getCreatedAt(), right.getCreatedAt());
    }

    private int currentTaskStatusRank(CrawlerTask task) {
        return switch (task.getStatus()) {
        case RUNNING -> 0;
        case WAITING -> 1;
        case PAUSED -> 2;
        default -> 3;
        };
    }

    public synchronized List<TaskView> reorderQueuedTasks(User user, List<String> taskIds) {
        return reorderQueuedTasks(user, null, taskIds);
    }

    @Transactional
    public synchronized List<TaskView> reorderQueuedTasks(User user, Long siteId, List<String> taskIds) {
        if (taskQueueRepository == null) {
            return reorderLegacyQueuedTasks(user, siteId, taskIds);
        }
        LinkedHashSet<String> requestedIds = new LinkedHashSet<>(taskIds);
        if (requestedIds.size() != taskIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务排序参数包含重复项");
        }
        List<CrawlerTask> waiting = taskRepository.findByUserAndStatusInOrderByCreatedAtDesc(
                        user, List.of(CrawlerTask.TaskStatus.WAITING)).stream()
                .filter(task -> siteId == null || Objects.equals(task.getSite().getId(), siteId))
                .sorted(this::compareCurrentTasks).toList();
        Set<String> queuedIds = waiting.stream().map(CrawlerTask::getId).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!queuedIds.equals(requestedIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "等待队列已变化，请刷新后重试");
        }

        Map<String, CrawlerTask> queuedTasksById = waiting.stream()
                .collect(java.util.stream.Collectors.toMap(CrawlerTask::getId, task -> task));
        int previousPriorityRank = -1;
        for (String taskId : taskIds) {
            CrawlerTask task = queuedTasksById.get(taskId);
            if (task.getStatus() != CrawlerTask.TaskStatus.WAITING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "等待队列已变化，请刷新后重试");
            }
            int currentPriorityRank = priorityRank(task.getPriority());
            if (currentPriorityRank < previousPriorityRank) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "仅支持调整同一优先级内的任务顺序");
            }
            previousPriorityRank = currentPriorityRank;
        }

        for (int index = 0; index < taskIds.size(); index++) {
            String taskId = taskIds.get(index);
            CrawlerTask task = queuedTasksById.get(taskId);
            long queueOrder = nextQueueOrder();
            task.setQueueOrder(queueOrder);
            taskRepository.save(task);
            reprioritizeWaitingTask(task);
        }
        log.info("[采集任务] 等待队列顺序已调整: userId={}, taskIds={}", user.getId(), taskIds);
        dispatchWaitingTasks();
        return queuedTasks(user, siteId);
    }

    private List<TaskView> reorderLegacyQueuedTasks(User user, Long siteId, List<String> taskIds) {
        LinkedHashSet<String> requestedIds = new LinkedHashSet<>(taskIds);
        if (requestedIds.size() != taskIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务排序参数包含重复项");
        }
        List<CrawlerTask> waiting = legacyQueuedTaskEntities(user, siteId);
        Set<String> queuedIds = waiting.stream().map(CrawlerTask::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!queuedIds.equals(requestedIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "等待队列已变化，请刷新后重试");
        }
        Map<String, CrawlerTask> queuedTasksById = waiting.stream()
                .collect(java.util.stream.Collectors.toMap(CrawlerTask::getId, task -> task));
        int previousPriorityRank = -1;
        for (String taskId : taskIds) {
            CrawlerTask task = queuedTasksById.get(taskId);
            int currentPriorityRank = priorityRank(task.getPriority());
            if (currentPriorityRank < previousPriorityRank) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "仅支持调整同一优先级内的任务顺序");
            }
            previousPriorityRank = currentPriorityRank;
        }
        for (String taskId : taskIds) {
            CrawlerTask task = queuedTasksById.get(taskId);
            task.setQueueOrder(nextQueueOrder());
            taskRepository.save(task);
            reprioritizeWaitingTask(task);
        }
        return queuedTasks(user, siteId);
    }

    @Transactional
    public synchronized List<TaskView> prioritizeQueuedTask(User user, String taskId) {
        CrawlerTask task = managementService.ownedTask(user, taskId);
        if (task.getStatus() != CrawlerTask.TaskStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有等待中的任务可以立刻优先");
        }
        CrawlerTask.Priority previousPriority = task.getPriority();
        long firstQueueOrder = taskRepository.findByUserAndStatusInOrderByCreatedAtDesc(
                        user, List.of(CrawlerTask.TaskStatus.WAITING)).stream()
                .filter(waiting -> Objects.equals(waiting.getSite().getId(), task.getSite().getId()))
                .map(CrawlerTask::getQueueOrder).filter(Objects::nonNull).mapToLong(Long::longValue)
                .min()
                .orElseGet(() -> taskQueueRepository == null
                        ? legacyQueuedTaskEntities(user, task.getSite().getId()).stream()
                                .map(CrawlerTask::getQueueOrder).filter(Objects::nonNull)
                                .mapToLong(Long::longValue).min().orElseGet(this::nextQueueOrder)
                        : nextQueueOrder()) - 1L;
        task.setPriority(CrawlerTask.Priority.HIGH);
        task.setQueueOrder(firstQueueOrder);
        try {
            taskRepository.save(task);
            reprioritizeWaitingTask(task);
        } catch (RuntimeException exception) {
            task.setPriority(previousPriority);
            throw exception;
        }
        log.info("[采集任务] 任务已立刻优先: userId={}, taskId={}, previousPriority={}",
                user.getId(), taskId, previousPriority);
        recordCrawlerEvent(task, "任务已立刻优先", "原优先级：" + previousPriority + "；已移动到等待队列首位");
        dispatchWaitingTasks();
        return queuedTasks(user, task.getSite().getId());
    }

    public TaskView start(User user, Long siteId, String url) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        if (!Boolean.TRUE.equals(site.getEnabled())) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先启用该采集网站");
        requireRule(site);
        URI validated = httpClient.validateSiteUrl(site, url);
        String externalId = externalId(validated.toString());
        CrawlerBook book = bookRepository.findBySiteAndExternalBookId(site, externalId).orElseGet(() ->
                bookRepository.save(CrawlerBook.builder().site(site).externalBookId(externalId)
                        .bookUrl(validated.toString()).bookName("待解析书籍").discoverTime(LocalDateTime.now()).build()));
        book.setBookUrl(validated.toString());
        return managementService.taskView(createBookTask(user, book, CrawlerTask.TaskType.BOOK_FULL_CRAWL));
    }

    public TaskView continueBook(User user, Long bookId) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        return managementService.taskView(createBookTask(user, book, CrawlerTask.TaskType.BOOK_FULL_CRAWL));
    }

    public TaskView retryFailures(User user, Long bookId) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(book).stream()
                .filter(ch -> ch.getCrawlStatus() == CrawlerChapter.CrawlStatus.FAILED || ch.getCrawlStatus() == CrawlerChapter.CrawlStatus.CONTENT_SUSPECTED)
                .forEach(ch -> { ch.setCrawlStatus(CrawlerChapter.CrawlStatus.NOT_CRAWLED); ch.setErrorMessage(null); chapterRepository.save(ch); });
        return managementService.taskView(createBookTask(user, book, CrawlerTask.TaskType.BOOK_CONTENT));
    }

    public TaskView scanSite(User user, Long siteId) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        requireEnabled(site);
        requireRule(site);
        if (site.getRule().getDiscoveryItemSelector() == null || site.getRule().getDiscoveryItemSelector().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先配置书籍发现 Selector");
        if (taskRepository.existsBySiteAndTypeAndStatusIn(site, CrawlerTask.TaskType.SITE_SCAN, ACTIVE_STATUSES))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该网站已有扫描任务");
        return managementService.taskView(createAndSubmit(user, site, null, CrawlerTask.TaskType.SITE_SCAN));
    }

    public TaskView scanDiscoveryPage(User user, CrawlerDiscoveryPage page, boolean automated) {
        if (page == null || page.getSite() == null || page.getSite().getUser() == null
                || !Objects.equals(page.getSite().getUser().getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "发现页不存在");
        }
        CrawlerSite site = page.getSite();
        requireEnabled(site);
        requireRule(site);
        if (site.getRule().getDiscoveryItemSelector() == null || site.getRule().getDiscoveryItemSelector().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先配置书籍发现 Selector");
        }
        if (taskRepository.existsByDiscoveryPageIdAndStatusIn(page.getId(), ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该发现页已有扫描任务");
        }
        CrawlerTask task = CrawlerTask.builder().user(user).site(site)
                .type(CrawlerTask.TaskType.SITE_SCAN)
                .priority(automated ? CrawlerTask.Priority.LOW : CrawlerTask.Priority.HIGH)
                .discoveryPageId(page.getId()).discoveryPageName(page.getPageName())
                .scanStartUrl(page.getPageUrl()).scanMaxPages(page.getMaxPages()).build();
        task = taskRepository.save(task);
        page.setLastScanAt(LocalDateTime.now());
        discoveryPageRepository.save(page);
        log.info("[采集任务] 已创建发现页扫描: taskId={}, trigger={}, page={}, url={}",
                task.getId(), automated ? "自动" : "人工", page.getPageName(), page.getPageUrl());
        recordCrawlerEvent(task, "发现页扫描任务已创建", "触发方式：" + (automated ? "自动调度" : "人工操作")
                + "；发现页：" + page.getPageName() + "；优先级：" + task.getPriority());
        submitAfterCommit(task.getId());
        return managementService.taskView(task);
    }

    public TaskView checkUpdates(User user, Long bookId) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        requireEnabled(book.getSite());
        return managementService.taskView(createBookTask(user, book, CrawlerTask.TaskType.BOOK_UPDATE_CHECK));
    }

    public TaskView refreshMetadata(User user, Long bookId) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        return managementService.taskView(createBookTask(user, book, CrawlerTask.TaskType.BOOK_METADATA));
    }

    public List<TaskView> batchCrawl(User user, List<Long> bookIds) {
        List<CrawlerBook> books = ownedBooks(user, bookIds).stream()
                .filter(book -> discoveryStatus(book) == CrawlerBook.DiscoveryStatus.ACTIVE).toList();
        books.forEach(this::ensureNoActiveTask);
        return books.stream()
                .map(book -> managementService.taskView(createBookTask(user, book, CrawlerTask.TaskType.BOOK_FULL_CRAWL))).toList();
    }

    public List<TaskView> batchRefreshMetadata(User user, List<Long> bookIds) {
        List<CrawlerBook> books = ownedBooks(user, bookIds);
        books.forEach(this::ensureNoActiveTask);
        return books.stream()
                .map(book -> managementService.taskView(
                        createBookTask(user, book, CrawlerTask.TaskType.BOOK_METADATA)))
                .toList();
    }

    public List<com.aibook.dto.crawler.CrawlerDtos.BookView> setDiscoveryStatus(
            User user, List<Long> bookIds, CrawlerBook.DiscoveryStatus status) {
        return ownedBooks(user, bookIds).stream().map(book -> {
            book.setDiscoveryStatus(status);
            return managementService.bookView(bookRepository.save(book));
        }).toList();
    }

    public com.aibook.dto.crawler.CrawlerDtos.BookView setBookStatus(
            User user, Long bookId, CrawlerBook.CrawlStatus status, Boolean autoUpdateEnabled) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        if (taskRepository.existsByCrawlerBookAndStatusIn(book, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该书籍仍有活动任务，请先暂停或取消任务");
        }
        return applyBookStatus(user, book, status);
    }

    @Transactional
    public List<com.aibook.dto.crawler.CrawlerDtos.BookView> setBookStatuses(
            User user, List<Long> bookIds, CrawlerBook.CrawlStatus status) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(bookIds);
        if (uniqueIds.size() != bookIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量状态修改包含重复书籍");
        }
        List<CrawlerBook> books = uniqueIds.stream()
                .map(id -> managementService.ownedBook(user, id))
                .toList();
        books.forEach(book -> {
            if (taskRepository.existsByCrawlerBookAndStatusIn(book, ACTIVE_STATUSES)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "书籍“" + bookName(book) + "”仍有活动任务，请先暂停或取消任务");
            }
        });
        return books.stream().map(book -> applyBookStatus(user, book, status)).toList();
    }

    private com.aibook.dto.crawler.CrawlerDtos.BookView applyBookStatus(
            User user, CrawlerBook book, CrawlerBook.CrawlStatus status) {
        CrawlerBook.CrawlStatus previous = book.getCrawlStatus();
        book.setCrawlStatus(status);
        book.setAutoUpdateEnabled(false);
        boolean hasContent = chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(book).stream()
                .anyMatch(this::hasParsedContent);
        if (book.getLibraryBook() == null) {
            book.setImportStatus(hasContent ? CrawlerBook.ImportStatus.READY : CrawlerBook.ImportStatus.NOT_IMPORTED);
        }
        if (status == CrawlerBook.CrawlStatus.COMPLETED) book.setLastCrawlTime(LocalDateTime.now());
        bookRepository.save(book);
        try {
            operationLogService.recordEntry(user, OperationLog.Action.CRAWLER_TASK,
                    book.getLibraryBook() == null ? null : book.getLibraryBook().getId(), bookName(book),
                    "人工修改采集状态：" + bookName(book),
                    "书籍ID：" + book.getId() + "；网站：" + book.getSite().getSiteName()
                            + "；原状态：" + previous + "；新状态：" + status
                            + "；任务模式：仅手动触发");
        } catch (Exception exception) {
            log.warn("[采集任务] 写入人工状态修改日志失败: bookId={}", book.getId(), exception);
        }
        log.info("[采集任务] 人工修改书籍状态: bookId={}, book={}, previous={}, current={}",
                book.getId(), bookName(book), previous, status);
        return managementService.bookView(book);
    }

    @Transactional
    public com.aibook.dto.crawler.CrawlerDtos.BookView setLibrarySync(
            User user, Long bookId, boolean enabled) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        if (book.getLibraryBook() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "书籍尚未加入书库，不能设置自动同步");
        }
        book.setAutoSyncLibrary(enabled);
        bookRepository.save(book);
        return managementService.bookView(book);
    }

    public TaskView command(User user, String taskId, String command) {
        CrawlerTask task;
        synchronized (taskLock(taskId)) {
            task = managementService.ownedTask(user, taskId);
            switch (command) {
            case "pause" -> {
                if (task.getStatus() == CrawlerTask.TaskStatus.RUNNING
                        || task.getStatus() == CrawlerTask.TaskStatus.WAITING) {
                    task.setStatus(CrawlerTask.TaskStatus.PAUSED);
                    taskRepository.save(task);
                    removeQueuedTask(taskId);
                    interruptRunningTask(taskId);
                }
            }
            case "cancel" -> {
                task.setStatus(CrawlerTask.TaskStatus.CANCELLED);
                taskRepository.save(task);
                removeQueuedTask(taskId);
                interruptRunningTask(taskId);
            }
            case "resume" -> {
                if (task.getStatus() != CrawlerTask.TaskStatus.PAUSED
                        && task.getStatus() != CrawlerTask.TaskStatus.FAILED)
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "只有暂停或失败任务可以继续");
                task.setStatus(CrawlerTask.TaskStatus.WAITING);
                task.setErrorMessage(null);
                task.setFinishedAt(null);
                taskRepository.save(task);
                // 若旧线程仍在退出，active 会阻止重复执行，随后由 CrawlerJob.finally 自动重新入队。
                submitAfterCommit(task.getId());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的任务操作");
            }
        }
        if (task.getCrawlerBook() != null && task.getType() != CrawlerTask.TaskType.BOOK_METADATA
                && (task.getStatus() == CrawlerTask.TaskStatus.PAUSED
                || task.getStatus() == CrawlerTask.TaskStatus.CANCELLED)) {
            task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.PAUSED);
            bookRepository.save(task.getCrawlerBook());
        } else if (task.getCrawlerBook() != null
                && task.getType() != CrawlerTask.TaskType.BOOK_METADATA && "resume".equals(command)) {
            task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.WAITING);
            bookRepository.save(task.getCrawlerBook());
        }
        log.info("[采集任务] 收到控制指令: taskId={}, command={}, status={}, book={}",
                taskId, command, task.getStatus(), bookName(task.getCrawlerBook()));
        recordCrawlerEvent(task, "任务控制", "指令：" + command + "；状态：" + task.getStatus());
        return managementService.taskView(task);
    }

    @Transactional
    public synchronized int batchManageTasks(User user, List<String> taskIds, String action,
            CrawlerTask.Priority priority) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(taskIds);
        if (uniqueIds.size() != taskIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量操作包含重复任务");
        }
        List<CrawlerTask> selectedTasks = uniqueIds.stream()
                .map(id -> managementService.ownedTask(user, id))
                .toList();
        validateBatchAction(selectedTasks, action, priority);

        for (CrawlerTask task : selectedTasks) {
            switch (action) {
            case "pause", "resume", "cancel" -> command(user, task.getId(), action);
            case "priority" -> updateTask(user, task.getId(), priority);
            case "delete" -> deleteTask(user, task.getId());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的批量任务操作");
            }
        }
        log.info("[采集任务] 批量操作完成: userId={}, action={}, affectedCount={}",
                user.getId(), action, selectedTasks.size());
        return selectedTasks.size();
    }

    private void validateBatchAction(List<CrawlerTask> selectedTasks, String action,
            CrawlerTask.Priority priority) {
        if ("priority".equals(action) && priority == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "设置优先级时必须提供优先级");
        }
        for (CrawlerTask task : selectedTasks) {
            CrawlerTask.TaskStatus status = task.getStatus();
            boolean allowed = switch (action) {
            case "pause" -> status == CrawlerTask.TaskStatus.RUNNING
                    || status == CrawlerTask.TaskStatus.WAITING;
            case "resume" -> (status == CrawlerTask.TaskStatus.PAUSED
                    || status == CrawlerTask.TaskStatus.FAILED);
            case "cancel" -> status == CrawlerTask.TaskStatus.RUNNING
                    || status == CrawlerTask.TaskStatus.WAITING
                    || status == CrawlerTask.TaskStatus.PAUSED;
            case "delete" -> status != CrawlerTask.TaskStatus.RUNNING
                    && (status == CrawlerTask.TaskStatus.WAITING || !active.contains(task.getId()));
            case "priority" -> status == CrawlerTask.TaskStatus.WAITING
                    || status == CrawlerTask.TaskStatus.PAUSED
                    || status == CrawlerTask.TaskStatus.FAILED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的批量任务操作");
            };
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "任务“" + batchTaskName(task) + "”当前状态不支持该批量操作");
            }
        }
    }

    private String batchTaskName(CrawlerTask task) {
        if (task.getCrawlerBook() != null && task.getCrawlerBook().getBookName() != null) {
            return task.getCrawlerBook().getBookName();
        }
        if (task.getDiscoveryPageName() != null && !task.getDiscoveryPageName().isBlank()) {
            return task.getDiscoveryPageName();
        }
        return task.getId();
    }

    @Transactional
    public TaskView updateTask(User user, String taskId, CrawlerTask.Priority priority) {
        CrawlerTask task = managementService.ownedTask(user, taskId);
        if (task.getStatus() != CrawlerTask.TaskStatus.WAITING
                && task.getStatus() != CrawlerTask.TaskStatus.PAUSED
                && task.getStatus() != CrawlerTask.TaskStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有等待中、已暂停或失败的任务可以修改优先级");
        }
        CrawlerTask.Priority previous = task.getPriority();
        task.setPriority(priority);
        task.setQueueOrder(nextQueueOrder());
        taskRepository.save(task);
        reprioritizeWaitingTask(task);
        log.info("[采集任务] 修改任务优先级: taskId={}, previous={}, current={}", taskId, previous, priority);
        recordCrawlerEvent(task, "任务配置已修改", "原优先级：" + previous + "；新优先级：" + priority);
        return managementService.taskView(task);
    }

    @Transactional
    public void deleteTask(User user, String taskId) {
        synchronized (taskLock(taskId)) {
            CrawlerTask task = managementService.ownedTask(user, taskId);
            if (task.getStatus() == CrawlerTask.TaskStatus.WAITING
                    || task.getStatus() == CrawlerTask.TaskStatus.PAUSED
                    || task.getStatus() == CrawlerTask.TaskStatus.CANCELLED) {
                removeQueuedTask(taskId);
            }
            if (active.contains(taskId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务正在停止，请稍后再删除");
            }
            if (task.getStatus() == CrawlerTask.TaskStatus.RUNNING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "运行中的任务需先暂停或取消后才能删除");
            }
            log.info("[采集任务] 删除任务记录: taskId={}, status={}, type={}, book={}",
                    taskId, task.getStatus(), task.getType(), bookName(task.getCrawlerBook()));
            recordCrawlerEvent(task, "任务记录已删除", "状态：" + task.getStatus());
            taskRepository.delete(task);
        }
    }

    private CrawlerTask createAndSubmit(
            User user, CrawlerSite site, CrawlerBook book, CrawlerTask.TaskType type) {
        CrawlerTask.CrawlerTaskBuilder builder = CrawlerTask.builder().user(user).site(site).crawlerBook(book)
                .type(type).priority(CrawlerTask.Priority.HIGH);
        if (type == CrawlerTask.TaskType.SITE_SCAN) {
            builder.scanStartUrl(site.getHomeUrl() == null || site.getHomeUrl().isBlank()
                            ? site.getBaseUrl() : site.getHomeUrl())
                    .scanMaxPages(value(site.getMaxDiscoveryPages(), 3));
        }
        CrawlerTask task = taskRepository.save(builder.build());
        log.info("[采集任务] 已创建: taskId={}, type={}, priority={}, site={}, book={}",
                task.getId(), type, CrawlerTask.Priority.HIGH, site.getSiteName(), bookName(book));
        recordCrawlerEvent(task, "任务已创建", "触发方式：人工操作；优先级：" + CrawlerTask.Priority.HIGH);
        submitAfterCommit(task.getId());
        return task;
    }

    private CrawlerTask createBookTask(User user, CrawlerBook book, CrawlerTask.TaskType type) {
        requireEnabled(book.getSite());
        requireRule(book.getSite());
        ensureNoActiveTask(book);
        if (type != CrawlerTask.TaskType.BOOK_METADATA) {
            book.setCrawlStatus(CrawlerTask.TaskType.BOOK_UPDATE_CHECK == type
                    ? CrawlerBook.CrawlStatus.UPDATING : CrawlerBook.CrawlStatus.WAITING);
            bookRepository.save(book);
        }
        return createAndSubmit(user, book.getSite(), book, type);
    }

    private void submitAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(taskId);
            }
        });
    }

    private void submit(String id) {
        CrawlerTask task = taskRepository.findById(id).orElse(null);
        if (task == null) return;
        long queueOrder = task.getQueueOrder() == null ? nextQueueOrder() : task.getQueueOrder();
        if (task.getQueueOrder() == null) {
            task.setQueueOrder(queueOrder);
            taskRepository.save(task);
        } else {
            queueOrderSequence.updateAndGet(current -> Math.max(current, queueOrder));
        }
        if (taskQueueRepository == null) {
            submitLegacy(task, queueOrder);
            return;
        }
        dispatchWaitingTasks();
    }

    private void submitLegacy(CrawlerTask task, long queueOrder) {
        if (task.getStatus() != CrawlerTask.TaskStatus.WAITING || !active.add(task.getId())) return;
        applyConcurrencyLimit(configuredConcurrency());
        activeTaskSites.put(task.getId(), task.getSite().getId());
        try {
            executor.execute(new CrawlerJob(task.getId(), task.getPriority(), queueOrder,
                    jobSequence.incrementAndGet()));
        } catch (RejectedExecutionException exception) {
            active.remove(task.getId());
            activeTaskSites.remove(task.getId());
            log.error("[采集任务] 任务加入全局等待队列失败: taskId={}", task.getId(), exception);
        }
    }

    private long nextQueueOrder() {
        long timeBaseline = System.currentTimeMillis() * 1000L;
        return queueOrderSequence.updateAndGet(current -> Math.max(current + 1L, timeBaseline));
    }

    private int configuredConcurrency() {
        try { return crawlerSettingsService.maxConcurrentTasks(); }
        catch (Exception exception) {
            log.warn("读取采集任务并行配置失败，使用当前值", exception);
            return executor.getMaximumPoolSize();
        }
    }

    private synchronized void applyConcurrencyLimit(int requested) {
        int limit = Math.max(1, requested);
        if (executor.getCorePoolSize() == limit && executor.getMaximumPoolSize() == limit) return;
        if (limit < executor.getCorePoolSize()) {
            executor.setCorePoolSize(limit);
            executor.setMaximumPoolSize(limit);
        } else {
            executor.setMaximumPoolSize(limit);
            executor.setCorePoolSize(limit);
        }
        log.info("[采集任务] 并行上限已更新: limit={}, running={}, queued={}",
                limit, executor.getActiveCount(), executor.getQueue().size());
    }

    private int configuredQueueConcurrency() {
        if (taskQueueRepository == null) {
            return configuredConcurrency();
        }
        List<CrawlerTaskQueue> queues = taskQueueRepository.findAll();
        if (queues.isEmpty()) return configuredConcurrency();
        long total = queues.stream().mapToLong(queue -> value(queue.getMaxConcurrentTasks(), 1)).sum();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, total));
    }

    @Scheduled(fixedDelayString = "${crawler.task-queue-dispatch-delay-ms:500}", initialDelay = 1000)
    @Transactional
    public synchronized void dispatchWaitingTasks() {
        if (shuttingDown) return;
        if (taskQueueRepository == null) return;
        List<CrawlerTaskQueue> queues = taskQueueRepository.findAll();
        if (queues.isEmpty()) return;
        applyConcurrencyLimit(configuredQueueConcurrency());
        Map<Long, CrawlerTaskQueue> queuesBySite = new HashMap<>();
        for (CrawlerTaskQueue queue : queues) queuesBySite.put(queue.getSite().getId(), queue);

        Map<Long, Integer> scheduledBySite = new HashMap<>();
        activeTaskSites.values().forEach(siteId -> scheduledBySite.merge(siteId, 1, Integer::sum));
        List<CrawlerTask> waitingTasks = new ArrayList<>(
                taskRepository.findByStatusOrderByQueueOrderAsc(CrawlerTask.TaskStatus.WAITING));
        waitingTasks.sort(Comparator.comparingInt((CrawlerTask task) -> priorityRank(task.getPriority()))
                .thenComparing(CrawlerTask::getQueueOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CrawlerTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        LocalDateTime now = LocalDateTime.now();

        for (CrawlerTask task : waitingTasks) {
            CrawlerTaskQueue queue = queuesBySite.get(task.getSite().getId());
            if (queue == null) continue;
            int siteActive = scheduledBySite.getOrDefault(task.getSite().getId(), 0);
            if (siteActive >= value(queue.getMaxConcurrentTasks(), 1)) continue;
            int intervalSeconds = value(queue.getTaskIntervalSeconds(), 0);
            LocalDateTime lastStarted = queue.getLastTaskStartedAt();
            if (intervalSeconds > 0 && lastStarted != null
                    && lastStarted.plusSeconds(intervalSeconds).isAfter(now)) continue;
            if (!active.add(task.getId())) continue;

            long queueOrder = task.getQueueOrder() == null ? nextQueueOrder() : task.getQueueOrder();
            task.setQueueOrder(queueOrder);
            taskRepository.save(task);
            queue.setLastTaskStartedAt(now);
            taskQueueRepository.save(queue);
            activeTaskSites.put(task.getId(), task.getSite().getId());
            scheduledBySite.put(task.getSite().getId(), siteActive + 1);
            try {
                executor.execute(new CrawlerJob(task.getId(), task.getPriority(), queueOrder,
                        jobSequence.incrementAndGet()));
            } catch (RejectedExecutionException exception) {
                active.remove(task.getId());
                activeTaskSites.remove(task.getId());
                log.error("[采集任务] 任务队列派发失败: taskId={}", task.getId(), exception);
                break;
            }
            if (intervalSeconds > 0) {
                queue.setLastTaskStartedAt(LocalDateTime.now());
                now = queue.getLastTaskStartedAt();
            }
        }
    }

    private void yieldRunningTasksForQueue(Long siteId, int limit) {
        List<CrawlerTask> runningTasks = taskRepository.findByStatusIn(
                        List.of(CrawlerTask.TaskStatus.RUNNING)).stream()
                .filter(task -> Objects.equals(task.getSite().getId(), siteId))
                .sorted(Comparator.comparingInt((CrawlerTask task) -> priorityRank(task.getPriority()))
                        .thenComparing(CrawlerTask::getStartedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CrawlerTask::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (runningTasks.size() <= limit) return;
        for (CrawlerTask candidate : runningTasks.subList(limit, runningTasks.size())) {
            synchronized (taskLock(candidate.getId())) {
                CrawlerTask task = taskRepository.findById(candidate.getId()).orElse(null);
                if (task == null || task.getStatus() != CrawlerTask.TaskStatus.RUNNING) continue;
                task.setStatus(CrawlerTask.TaskStatus.WAITING);
                task.setQueueOrder(nextQueueOrder());
                taskRepository.save(task);
                if (task.getCrawlerBook() != null) {
                    task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.WAITING);
                    bookRepository.save(task.getCrawlerBook());
                }
                recordCrawlerEvent(task, "队列并发数调低，任务转回等待",
                        "新并发数：" + limit + "；保留当前进度并等待重新调度");
            }
        }
    }

    private void reprioritizeWaitingTask(CrawlerTask task) {
        if (task.getStatus() != CrawlerTask.TaskStatus.WAITING) return;
        for (Runnable queued : executor.getQueue()) {
            if (queued instanceof CrawlerJob job && job.taskId.equals(task.getId()) && executor.remove(queued)) {
                long queueOrder = task.getQueueOrder() == null ? nextQueueOrder() : task.getQueueOrder();
                task.setQueueOrder(queueOrder);
                taskRepository.save(task);
                executor.getQueue().offer(new CrawlerJob(task.getId(), task.getPriority(), queueOrder,
                        jobSequence.incrementAndGet()));
                return;
            }
        }
    }

    private boolean removeQueuedTask(String taskId) {
        for (Runnable queued : executor.getQueue()) {
            if (queued instanceof CrawlerJob job && job.taskId.equals(taskId) && executor.remove(queued)) {
                active.remove(taskId);
                activeTaskSites.remove(taskId);
                return true;
            }
        }
        return false;
    }

    private void interruptRunningTask(String taskId) {
        Thread worker = runningThreads.get(taskId);
        if (worker != null) worker.interrupt();
    }

    private final class CrawlerJob implements Runnable, Comparable<CrawlerJob> {
        private final String taskId;
        private final CrawlerTask.Priority priority;
        private final long queueOrder;
        private final long sequence;
        private CrawlerJob(String taskId, CrawlerTask.Priority priority, long queueOrder, long sequence) {
            this.taskId = taskId; this.priority = priority; this.queueOrder = queueOrder; this.sequence = sequence;
        }
        @Override public void run() {
            Thread worker = Thread.currentThread();
            runningThreads.put(taskId, worker);
            try {
                CrawlerTaskService.this.run(taskId);
            } finally {
                runningThreads.remove(taskId, worker);
                active.remove(taskId);
                activeTaskSites.remove(taskId);
                resubmitAfterConcurrencyYield(taskId);
                dispatchWaitingTasks();
            }
        }
        @Override public int compareTo(CrawlerJob other) {
            int rank = Integer.compare(priorityRank(priority), priorityRank(other.priority));
            if (rank != 0) return rank;
            int queueRank = Long.compare(queueOrder, other.queueOrder);
            return queueRank == 0 ? Long.compare(sequence, other.sequence) : queueRank;
        }
    }

    private void resubmitAfterConcurrencyYield(String taskId) {
        if (shuttingDown) return;
        taskRepository.findById(taskId)
                .filter(task -> task.getStatus() == CrawlerTask.TaskStatus.WAITING)
                .ifPresent(task -> submit(taskId));
    }

    void run(String taskId) {
        CrawlerTask task;
        synchronized (taskLock(taskId)) {
            task = taskRepository.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != CrawlerTask.TaskStatus.WAITING) return;
            task.setStatus(CrawlerTask.TaskStatus.RUNNING);
            task.setQueueOrder(null);
            task.setStartedAt(task.getStartedAt() == null ? LocalDateTime.now() : task.getStartedAt());
            task.setErrorMessage(null);
            taskRepository.save(task);
            if (task.getCrawlerBook() != null && task.getType() != CrawlerTask.TaskType.BOOK_METADATA) {
                task.getCrawlerBook().setLastCrawlStartedAt(task.getStartedAt());
                bookRepository.save(task.getCrawlerBook());
            }
        }
        try {
            CrawlerBook book = task.getCrawlerBook();
            CrawlerSite site = task.getSite();
            CrawlerSiteRule rule = site.getRule();
            BookCrawlerParser parser = parser(site);
            log.info("[采集任务] 开始执行: taskId={}, type={}, site={}, book={}, url={}", taskId,
                    task.getType(), site.getSiteName(), bookName(book), book == null ? "-" : book.getBookUrl());
            recordCrawlerEvent(task, "任务开始执行", book == null ? null : "地址：" + book.getBookUrl());

            if (task.getType() == CrawlerTask.TaskType.SITE_SCAN) {
                runSiteScan(task, site, rule, parser);
                return;
            }

            boolean metadataOnly = task.getType() == CrawlerTask.TaskType.BOOK_METADATA;
            if (metadataOnly || task.getType() == CrawlerTask.TaskType.BOOK_FULL_CRAWL
                    || task.getType() == CrawlerTask.TaskType.BOOK_UPDATE_CHECK) {
                if (!metadataOnly) {
                    book.setCrawlStatus(CrawlerBook.CrawlStatus.CRAWLING_METADATA);
                    bookRepository.save(book);
                }
                log.info("[采集任务] 开始解析书籍信息: taskId={}, book={}, url={}",
                        taskId, bookName(book), book.getBookUrl());
                CrawlerHttpClient.FetchResult detailResponse = httpClient.get(site, book.getBookUrl());
                task = runningTask(taskId);
                if (task == null) return;
                BookCrawlerParser.ParsedBook metadata = parser.parseBookDetail(detailResponse.html(), book.getBookUrl(), rule);
                applyMetadata(book, metadata);
                log.info("[采集任务] 书籍信息解析完毕: taskId={}, book={}, author={}, status={}, chapterListUrl={}",
                        taskId, bookName(book), metadata.author(), metadata.status(), metadata.chapterListUrl());
                recordCrawlerEvent(task, "书籍信息解析完毕", "作者：" + metadata.author()
                        + "；状态：" + metadata.status() + "；目录地址：" + metadata.chapterListUrl());
                if (metadataOnly) {
                    bookRepository.save(book);
                    task.setStatus(CrawlerTask.TaskStatus.SUCCESS);
                    task.setTotalCount(1);
                    task.setSuccessCount(1);
                    task.setWaitingCount(0);
                    task.setFinishedAt(LocalDateTime.now());
                    task.setCurrentChapter(null);
                    task = finishIfRunning(task);
                    if (task != null) {
                        log.info("[采集任务] 书籍元数据刷新完成: taskId={}, book={}, category={}, tags={}",
                                taskId, bookName(book), book.getCategory(), book.getTags());
                        recordCrawlerEvent(task, "书籍元数据刷新完成", "分类：" + book.getCategory()
                                + "；标签：" + book.getTags());
                    }
                    return;
                }
                book.setCrawlStatus(CrawlerBook.CrawlStatus.CRAWLING_CHAPTER_LIST); bookRepository.save(book);
                log.info("[采集任务] 开始解析章节目录: taskId={}, book={}, url={}",
                        taskId, bookName(book), metadata.chapterListUrl());
                CrawlerHttpClient.FetchResult listResponse = metadata.chapterListUrl().equals(book.getBookUrl()) ? detailResponse : httpClient.get(site, metadata.chapterListUrl());
                task = runningTask(taskId);
                if (task == null) return;
                List<BookCrawlerParser.ParsedChapter> parsedChapters = parser.parseChapterList(
                        listResponse.html(), metadata.chapterListUrl(), rule);
                mergeChapters(book, parsedChapters);
                log.info("[采集任务] 章节目录解析完毕: taskId={}, book={}, parsedChapters={}, totalChapters={}",
                        taskId, bookName(book), parsedChapters.size(), book.getChapterCount());
                recordCrawlerEvent(task, "章节目录解析完毕", "本次解析：" + parsedChapters.size()
                        + "；章节总数：" + book.getChapterCount());
                book.setLastUpdateCheckTime(LocalDateTime.now()); bookRepository.save(book);
            }

            task = runningTask(taskId);
            if (task == null) return;
            crawlContents(task, book, site, rule, parser, task.getType() == CrawlerTask.TaskType.BOOK_UPDATE_CHECK);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!shuttingDown && !isStopRequested(taskId)) fail(taskId, "任务被中断");
        } catch (Exception exception) {
            if (shuttingDown || isStopRequested(taskId)) return;
            log.warn("采集任务 {} 失败", taskId, exception);
            fail(taskId, userMessage(exception));
        }
    }

    private void crawlContents(CrawlerTask task, CrawlerBook book, CrawlerSite site, CrawlerSiteRule rule,
            BookCrawlerParser parser, boolean recheckCompleted) throws Exception {
        task = runningTask(task.getId());
        if (task == null) return;
        List<CrawlerChapter> pending = chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(book).stream()
                .filter(ch -> ch.getCrawlStatus() != CrawlerChapter.CrawlStatus.IGNORED
                        && (recheckCompleted || ch.getCrawlStatus() != CrawlerChapter.CrawlStatus.COMPLETED)).toList();
        task.setTotalCount((int) chapterRepository.countByCrawlerBook(book));
        task.setSuccessCount(recheckCompleted ? 0 : (int) chapterRepository.countByCrawlerBookAndCrawlStatus(
                book, CrawlerChapter.CrawlStatus.COMPLETED));
        task.setFailedCount(0);
        task.setWaitingCount(pending.size());
        task = saveProgressIfRunning(task);
        if (task == null) return;
        book.setCrawlStatus(CrawlerBook.CrawlStatus.CRAWLING_CONTENT); bookRepository.save(book);
        log.info("[采集任务] 开始采集章节: taskId={}, book={}, total={}, completed={}, pending={}, updateCheck={}",
                task.getId(), bookName(book), task.getTotalCount(), task.getSuccessCount(), pending.size(), recheckCompleted);
        recordCrawlerEvent(task, "开始采集章节", "总数：" + task.getTotalCount() + "；已完成："
                + task.getSuccessCount() + "；待处理：" + pending.size() + "；更新检查：" + recheckCompleted);
        long durationTotal = 0; int requests = 0;
        int updateSuccess = 0; int updateFailed = 0;
        RequestFailureGuard requestFailureGuard = new RequestFailureGuard(
                httpClient.maxConsecutiveFailures());
        for (CrawlerChapter chapter : pending) {
            CrawlerTask fresh = taskRepository.findById(task.getId()).orElseThrow();
            if (fresh.getStatus() == CrawlerTask.TaskStatus.PAUSED || fresh.getStatus() == CrawlerTask.TaskStatus.CANCELLED) {
                log.info("[采集任务] 章节采集已停止: taskId={}, book={}, status={}, progress={}/{} ({}%)",
                        task.getId(), bookName(book), fresh.getStatus(), finishedCount(fresh), fresh.getTotalCount(), progress(fresh));
                recordCrawlerEvent(fresh, "章节采集已停止", progressDetails(fresh)
                        + "；状态：" + fresh.getStatus());
                return;
            }
            task = fresh;
            task.setCurrentChapter(chapter.getChapterName());
            task = saveProgressIfRunning(task);
            if (task == null) return;
            boolean hadParsedContent = hasParsedContent(chapter);
            if (!hadParsedContent) {
                chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.CRAWLING);
                chapterRepository.save(chapter);
            }
            int current = Math.min(value(task.getTotalCount(), pending.size()), finishedCount(task) + 1);
            log.info("[采集任务] 正在采集章节: taskId={}, book={}, progress={}/{} ({}%), chapter={}, url={}",
                    task.getId(), bookName(book), current, task.getTotalCount(),
                    percentage(Math.max(0, current - 1), task.getTotalCount()), chapter.getChapterName(), chapter.getChapterUrl());
            recordCrawlerDetail(task, "正在采集章节", "进度：" + current + "/" + task.getTotalCount()
                    + "；章节：" + chapter.getChapterName() + "；地址：" + chapter.getChapterUrl());
            Exception requestFailure = null;
            boolean requestSucceeded = false;
            try {
                CrawlerHttpClient.FetchResult response = recheckCompleted
                        ? httpClient.get(site, chapter.getChapterUrl(), chapter.getSourceEtag(), chapter.getSourceLastModified())
                        : httpClient.get(site, chapter.getChapterUrl());
                requestSucceeded = true;
                requestFailureGuard.success();
                CrawlerTask afterFetch = runningTask(task.getId());
                if (afterFetch == null) {
                    if (!hadParsedContent) {
                        chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.NOT_CRAWLED);
                        chapterRepository.save(chapter);
                    }
                    return;
                }
                task = afterFetch;
                durationTotal += response.durationMillis(); requests++;
                if (response.statusCode() == 304) {
                    chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.COMPLETED);
                    chapter.setCrawlTime(LocalDateTime.now()); chapter.setErrorMessage(null); chapterRepository.save(chapter);
                    if (recheckCompleted) refreshUpdateCounts(book, task, ++updateSuccess, updateFailed, pending.size(), durationTotal, requests);
                    else refreshCounts(book, task, durationTotal, requests);
                    log.info("[采集任务] 章节未变化: taskId={}, book={}, progress={}/{} ({}%), chapter={}, httpStatus=304",
                            task.getId(), bookName(book), finishedCount(task), task.getTotalCount(), progress(task), chapter.getChapterName());
                    recordCrawlerDetail(task, "章节未变化", progressDetails(task)
                            + "；章节：" + chapter.getChapterName() + "；HTTP：304");
                    continue;
                }
                BookCrawlerParser.ParsedContent parsed = parser.parseChapter(response.html(), chapter.getChapterUrl(), rule);
                ContentMarkerMatch contentMarker = matchedContentMarker(site, parsed.content());
                if (contentMarker != null && contentMarker.status() == ContentMarkerStatus.PENDING_RELEASE) {
                    if (parsed.title() != null && !parsed.title().isBlank()) {
                        chapter.setChapterName(parsed.title());
                    }
                    chapter.setAccessStatus(CrawlerChapter.AccessStatus.LOCKED);
                    chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.PENDING_RELEASE);
                    chapter.setCrawlTime(LocalDateTime.now());
                    chapter.setErrorMessage("正文待开放，命中特征：" + shortText(contentMarker.marker(), 100));
                    chapter.setSourceEtag(null); chapter.setSourceLastModified(null);
                    if (!hadParsedContent) {
                        chapter.setContent(null); chapter.setContentHash(null);
                        chapter.setOriginalHtml(null); chapter.setWordCount(0);
                    }
                    chapterRepository.save(chapter);
                    log.info("[采集任务] 章节正文待开放: taskId={}, book={}, chapter={}, marker={}",
                            task.getId(), bookName(book), chapter.getChapterName(), contentMarker.marker());
                    recordCrawlerDetail(task, "章节正文待开放", "进度：" + current + "/"
                            + task.getTotalCount() + "；章节：" + chapter.getChapterName()
                            + "；命中特征：" + shortText(contentMarker.marker(), 100));
                    if (recheckCompleted) {
                        refreshUpdateCounts(book, task, ++updateSuccess, updateFailed,
                                pending.size(), durationTotal, requests);
                    } else {
                        refreshCounts(book, task, durationTotal, requests);
                    }
                    continue;
                }
                if (contentMarker != null) {
                    chapter.setAccessStatus(CrawlerChapter.AccessStatus.LOCKED);
                    throw new IllegalStateException("正文命中失败特征："
                            + shortText(contentMarker.marker(), 100));
                }
                chapter.setAccessStatus(CrawlerChapter.AccessStatus.FREE);
                if (parsed.title() != null && !parsed.title().isBlank()) chapter.setChapterName(parsed.title());
                chapter.setContent(parsed.content()); chapter.setContentHash(sha256(parsed.content()));
                chapter.setOriginalHtml(Boolean.TRUE.equals(rule.getSaveOriginalHtml()) ? parsed.originalHtml() : null);
                chapter.setSourceEtag(response.etag()); chapter.setSourceLastModified(response.lastModified());
                chapter.setWordCount(parsed.content().replaceAll("\\s+", "").length()); chapter.setCrawlTime(LocalDateTime.now()); chapter.setErrorMessage(null);
                boolean suspected = chapter.getWordCount() < value(rule.getMinChapterLength(), 100);
                chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.COMPLETED);
                if (suspected) {
                    log.warn("[采集任务] 章节内容疑似异常: taskId={}, book={}, progress={}/{} ({}%), chapter={}, chars={}, durationMs={}, preview=\"{}\"",
                            task.getId(), bookName(book), current, task.getTotalCount(), percentage(current, task.getTotalCount()),
                            chapter.getChapterName(), chapter.getWordCount(), response.durationMillis(), contentPreview(parsed.content()));
                    recordCrawlerDetail(task, "章节解析成功（内容较短）", "进度：" + current + "/" + task.getTotalCount()
                            + "；章节：" + chapter.getChapterName() + "；字数：" + chapter.getWordCount()
                            + "；耗时：" + response.durationMillis() + "ms；预览：" + contentPreview(parsed.content()));
                } else {
                    log.info("[采集任务] 章节采集完毕: taskId={}, book={}, progress={}/{} ({}%), chapter={}, chars={}, durationMs={}, preview=\"{}\"",
                            task.getId(), bookName(book), current, task.getTotalCount(), percentage(current, task.getTotalCount()),
                            chapter.getChapterName(), chapter.getWordCount(), response.durationMillis(), contentPreview(parsed.content()));
                    recordCrawlerDetail(task, "章节采集完毕", "进度：" + current + "/" + task.getTotalCount()
                            + "；章节：" + chapter.getChapterName() + "；字数：" + chapter.getWordCount()
                            + "；耗时：" + response.durationMillis() + "ms；预览：" + contentPreview(parsed.content()));
                }
            } catch (Exception exception) {
                if (shuttingDown || isStopRequested(task.getId())) {
                    if (!hadParsedContent) {
                        chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.NOT_CRAWLED);
                        chapter.setErrorMessage(null);
                        chapterRepository.save(chapter);
                    }
                    return;
                }
                if (!requestSucceeded) requestFailure = exception;
                chapter.setRetryCount(value(chapter.getRetryCount(), 0) + 1); chapter.setErrorMessage(userMessage(exception));
                chapter.setCrawlStatus(hadParsedContent ? CrawlerChapter.CrawlStatus.COMPLETED : CrawlerChapter.CrawlStatus.FAILED);
                log.warn("[采集任务] 章节采集失败: taskId={}, book={}, progress={}/{} ({}%), chapter={}, reason={}",
                        task.getId(), bookName(book), current, task.getTotalCount(), percentage(current, task.getTotalCount()),
                        chapter.getChapterName(), chapter.getErrorMessage());
                recordCrawlerDetail(task, hadParsedContent ? "章节更新失败（已保留原内容）" : "章节采集失败",
                        "进度：" + current + "/" + task.getTotalCount() + "；章节：" + chapter.getChapterName()
                                + "；原因：" + chapter.getErrorMessage());
            }
            chapterRepository.save(chapter);
            if (recheckCompleted) {
                if (chapter.getErrorMessage() == null) updateSuccess++; else updateFailed++;
                refreshUpdateCounts(book, task, updateSuccess, updateFailed, pending.size(), durationTotal, requests);
            } else refreshCounts(book, task, durationTotal, requests);
            if (requestFailure != null) requestFailureGuard.failure(requestFailure);
        }
        if (!recheckCompleted) refreshCounts(book, task, durationTotal, requests);
        task = runningTask(task.getId());
        if (task == null) return;
        int contentFailed = book.getFailedChapterCount();
        int taskFailed = recheckCompleted ? task.getFailedCount() : contentFailed;
        task.setStatus(taskFailed == 0 ? CrawlerTask.TaskStatus.SUCCESS : CrawlerTask.TaskStatus.PARTIAL_SUCCESS);
        task.setFinishedAt(LocalDateTime.now()); task.setCurrentChapter(null);
        task = finishIfRunning(task);
        if (task == null) return;
        book.setCrawlStatus(contentFailed == 0 ? CrawlerBook.CrawlStatus.COMPLETED : CrawlerBook.CrawlStatus.PARTIAL_SUCCESS);
        book.setImportStatus(contentFailed != 0 ? CrawlerBook.ImportStatus.NOT_IMPORTED
                : book.getLibraryBook() == null ? CrawlerBook.ImportStatus.READY : CrawlerBook.ImportStatus.IMPORTED);
        book.setLastCrawlTime(LocalDateTime.now()); bookRepository.save(book);
        if (contentFailed == 0 && book.getLibraryBook() != null && !Boolean.FALSE.equals(book.getAutoSyncLibrary())) {
            try {
                int publishedVersions = exportService.syncImportedBook(task.getUser(), book.getId());
                recordCrawlerEvent(task, publishedVersions == 0 ? "书库版本无需更新" : "书库版本同步完成",
                        publishedVersions == 0 ? "采集内容与当前书库版本一致"
                                : "已发布新版本：" + publishedVersions + " 个；书库ID：" + book.getLibraryBook().getId());
            } catch (Exception exception) {
                task.setStatus(CrawlerTask.TaskStatus.PARTIAL_SUCCESS);
                task.setErrorMessage("正文更新成功，但同步书库失败：" + userMessage(exception));
                taskRepository.save(task);
                recordCrawlerEvent(task, "书库版本同步失败", task.getErrorMessage());
                log.warn("[采集任务] 书库版本同步失败: taskId={}, bookId={}", task.getId(), book.getId(), exception);
            }
        }
        log.info("[采集任务] 采集完毕: taskId={}, book={}, status={}, progress={}/{} ({}%), failed={}, averageRequestMs={}",
                task.getId(), bookName(book), task.getStatus(), task.getSuccessCount(), task.getTotalCount(),
                progress(task), task.getFailedCount(), task.getAverageRequestMillis());
        recordCrawlerEvent(task, "采集任务完毕", progressDetails(task) + "；状态：" + task.getStatus()
                + "；失败：" + task.getFailedCount() + "；平均请求耗时：" + task.getAverageRequestMillis() + "ms");
    }

    private void mergeChapters(CrawlerBook book, List<BookCrawlerParser.ParsedChapter> parsed) {
        for (BookCrawlerParser.ParsedChapter value : parsed) {
            CrawlerChapter chapter = chapterRepository.findByCrawlerBookAndExternalChapterId(book, value.externalId())
                    .orElseGet(() -> CrawlerChapter.builder().crawlerBook(book).externalChapterId(value.externalId()).build());
            boolean changed = chapter.getId() != null && (!Objects.equals(chapter.getChapterUrl(), value.url())
                    || !Objects.equals(chapter.getChapterName(), value.title()));
            chapter.setChapterIndex(value.index()); chapter.setChapterName(value.title()); chapter.setChapterUrl(value.url());
            if (changed) chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.NOT_CRAWLED);
            chapterRepository.save(chapter);
        }
        book.setChapterCount((int) chapterRepository.countByCrawlerBook(book)); bookRepository.save(book);
    }

    private void applyMetadata(CrawlerBook book, BookCrawlerParser.ParsedBook m) {
        book.setBookName(m.title()); book.setAuthor(m.author()); book.setCoverUrl(m.coverUrl()); book.setDescription(m.description());
        book.setCategory(m.category()); book.setTags(String.join("\n", m.tags()));
        book.setBookStatus(m.status()); book.setLatestChapter(m.latestChapter());
    }

    private void runSiteScan(CrawlerTask task, CrawlerSite site, CrawlerSiteRule rule, BookCrawlerParser parser) throws Exception {
        String pageUrl = task.getScanStartUrl() == null || task.getScanStartUrl().isBlank()
                ? (site.getHomeUrl() == null || site.getHomeUrl().isBlank() ? site.getBaseUrl() : site.getHomeUrl())
                : task.getScanStartUrl();
        int maxPages = task.getScanMaxPages() == null
                ? value(site.getMaxDiscoveryPages(), 3) : Math.max(1, task.getScanMaxPages());
        Set<String> visitedPages = new HashSet<>();
        int succeeded = 0;
        int newBooks = 0;
        int duplicates = 0;
        int failed = 0;
        int scanned = 0;
        int pages = 0;
        scanResultRepository.deleteByTask(task);
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setNewBookCount(0);
        task.setDuplicateCount(0);
        task.setFailedCount(0);
        task.setScannedPageCount(0);
        task.setCurrentChapter("准备扫描");
        task = saveProgressIfRunning(task);
        if (task == null) return;
        log.info("[采集任务] 开始扫描网站: taskId={}, site={}, homeUrl={}, maxPages={}",
                task.getId(), site.getSiteName(), pageUrl, maxPages);
        recordCrawlerEvent(task, "开始扫描" + (task.getDiscoveryPageName() == null ? "网站" : "发现页"),
                "起始页：" + pageUrl + "；最大页数：" + maxPages);
        while (pageUrl != null && !pageUrl.isBlank() && pages < maxPages && visitedPages.add(pageUrl)) {
            task = runningTask(task.getId());
            if (task == null) return;
            task.setScannedPageCount(pages);
            task.setCurrentChapter("正在扫描第 " + (pages + 1) + " / " + maxPages + " 页");
            task = saveProgressIfRunning(task);
            if (task == null) return;
            pageUrl = httpClient.validateSiteUrl(site, pageUrl).toString();
            log.info("[采集任务] 正在扫描网站页面: taskId={}, site={}, page={}/{}, url={}",
                    task.getId(), site.getSiteName(), pages + 1, maxPages, pageUrl);
            recordCrawlerEvent(task, "正在扫描网站页面", "页码：" + (pages + 1) + "/"
                    + maxPages + "；地址：" + pageUrl);
            CrawlerHttpClient.FetchResult response = httpClient.get(site, pageUrl);
            task = runningTask(task.getId());
            if (task == null) return;
            List<BookCrawlerParser.ParsedDiscovery> items = parser.parseBookList(response.html(), pageUrl, rule);
            scanned += items.size();
            for (BookCrawlerParser.ParsedDiscovery item : items) {
                try {
                    String validatedUrl = httpClient.validateSiteUrl(site, item.url()).toString();
                    Optional<CrawlerBook> existing = bookRepository.findBySiteAndExternalBookId(site, item.externalId());
                    CrawlerBook book = existing.orElseGet(() -> CrawlerBook.builder().site(site)
                            .externalBookId(item.externalId()).bookUrl(validatedUrl)
                            .bookName(item.title()).discoverTime(LocalDateTime.now()).build());
                    if (discoveryStatus(book) == CrawlerBook.DiscoveryStatus.BLACKLISTED) {
                        failed++;
                        saveScanResult(task, book.getId(), item.title(), validatedUrl,
                                CrawlerScanResult.ResultStatus.BLACKLISTED, "书籍已在黑名单中");
                        continue;
                    }
                    book.setBookUrl(validatedUrl); book.setBookName(item.title()); book.setAuthor(item.author());
                    book.setCoverUrl(item.coverUrl()); book.setCategory(item.category()); book.setLatestChapter(item.latestChapter());
                    if ((book.getDiscoveryPageId() == null && (book.getDiscoveryPageName() == null
                            || book.getDiscoveryPageName().isBlank()))
                            && (task.getDiscoveryPageId() != null || task.getDiscoveryPageName() != null)) {
                        book.setDiscoveryPageId(task.getDiscoveryPageId());
                        book.setDiscoveryPageName(task.getDiscoveryPageName());
                    }
                    book = bookRepository.save(book);
                    CrawlerScanResult.ResultStatus resultStatus = existing.isPresent()
                            ? CrawlerScanResult.ResultStatus.DUPLICATE : CrawlerScanResult.ResultStatus.NEW;
                    if (existing.isPresent()) duplicates++; else newBooks++;
                    succeeded++;
                    saveScanResult(task, book.getId(), bookName(book), validatedUrl, resultStatus, null);
                    log.info("[采集任务] 发现书籍: taskId={}, site={}, page={}, book={}, url={}",
                            task.getId(), site.getSiteName(), pages + 1, bookName(book), validatedUrl);
                    recordCrawlerEvent(task, "发现书籍", "页码：" + (pages + 1) + "；书籍："
                            + bookName(book) + "；地址：" + validatedUrl);
                } catch (Exception exception) {
                    failed++;
                    saveScanResult(task, null, item.title(), item.url(),
                            CrawlerScanResult.ResultStatus.FAILED, userMessage(exception));
                    log.debug("忽略无效发现链接 {}", item.url(), exception);
                }
            }
            pages++;
            task = runningTask(task.getId());
            if (task == null) return;
            task.setTotalCount(scanned); task.setSuccessCount(succeeded); task.setNewBookCount(newBooks);
            task.setDuplicateCount(duplicates); task.setFailedCount(failed);
            task.setScannedPageCount(pages);
            task.setWaitingCount(0); task.setCurrentChapter("已扫描 " + pages + " / " + maxPages + " 页");
            task = saveProgressIfRunning(task);
            if (task == null) return;
            log.info("[采集任务] 网站页面扫描完毕: taskId={}, site={}, page={}, pageItems={}, discoveredTotal={}",
                    task.getId(), site.getSiteName(), pages, items.size(), succeeded);
            recordCrawlerEvent(task, "网站页面扫描完毕", "页码：" + pages + "；本页书籍："
                    + items.size() + "；成功：" + succeeded + "；新增：" + newBooks
                    + "；重复：" + duplicates + "；失败：" + failed);
            pageUrl = parser.parseNextBookListPage(response.html(), pageUrl, rule);
        }
        task = runningTask(task.getId());
        if (task == null) return;
        site.setLastScanAt(LocalDateTime.now()); siteRepository.save(site);
        if (task.getDiscoveryPageId() != null) {
            discoveryPageRepository.findById(task.getDiscoveryPageId()).ifPresent(discoveryPage -> {
                discoveryPage.setLastScanAt(LocalDateTime.now());
                discoveryPageRepository.save(discoveryPage);
            });
        }
        task.setStatus(failed == 0 ? CrawlerTask.TaskStatus.SUCCESS : CrawlerTask.TaskStatus.PARTIAL_SUCCESS);
        task.setFinishedAt(LocalDateTime.now()); task.setCurrentChapter(null);
        task = finishIfRunning(task);
        if (task == null) return;
        log.info("[采集任务] 网站扫描完毕: taskId={}, site={}, pages={}, succeeded={}, new={}, duplicate={}, failed={}",
                task.getId(), site.getSiteName(), pages, succeeded, newBooks, duplicates, failed);
        recordCrawlerEvent(task, task.getDiscoveryPageName() == null ? "网站扫描完毕" : "发现页扫描完毕",
                "扫描页数：" + pages + "；扫描到：" + task.getTotalCount() + "；成功：" + succeeded
                        + "；新增：" + newBooks + "；重复：" + duplicates + "；失败：" + failed
                + "；后续采集需人工触发");
    }

    private void saveScanResult(CrawlerTask task, Long bookId, String bookName, String bookUrl,
            CrawlerScanResult.ResultStatus status, String errorMessage) {
        scanResultRepository.save(CrawlerScanResult.builder().task(task).crawlerBookId(bookId)
                .bookName(shortText(Objects.toString(bookName, "未知书籍"), 500))
                .bookUrl(shortText(Objects.toString(bookUrl, ""), 1000))
                .resultStatus(status).errorMessage(errorMessage).build());
    }

    private void refreshCounts(CrawlerBook book, CrawlerTask task, long totalDuration, int requests) {
        refreshBookCounts(book);
        task.setTotalCount(book.getChapterCount()); task.setSuccessCount(book.getCrawledChapterCount()
                + value(book.getPendingReleaseChapterCount(), 0));
        task.setFailedCount(book.getFailedChapterCount());
        task.setWaitingCount(Math.max(0, book.getChapterCount() - book.getCrawledChapterCount()
                - value(book.getPendingReleaseChapterCount(), 0) - book.getFailedChapterCount()));
        task.setAverageRequestMillis(requests == 0 ? 0 : totalDuration / requests); saveProgressIfRunning(task);
    }

    private void refreshUpdateCounts(CrawlerBook book, CrawlerTask task, int success, int failed,
            int total, long totalDuration, int requests) {
        refreshBookCounts(book);
        task.setTotalCount(total); task.setSuccessCount(success); task.setFailedCount(failed);
        task.setWaitingCount(Math.max(0, total - success - failed));
        task.setAverageRequestMillis(requests == 0 ? 0 : totalDuration / requests); saveProgressIfRunning(task);
    }

    private void refreshBookCounts(CrawlerBook book) {
        int completed = (int) chapterRepository.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.COMPLETED);
        int pendingRelease = (int) chapterRepository.countByCrawlerBookAndCrawlStatus(
                book, CrawlerChapter.CrawlStatus.PENDING_RELEASE);
        int failed = (int) (chapterRepository.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.FAILED)
                + chapterRepository.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.CONTENT_SUSPECTED));
        int total = (int) chapterRepository.countByCrawlerBook(book);
        book.setChapterCount(total); book.setCrawledChapterCount(completed);
        book.setPendingReleaseChapterCount(pendingRelease);
        book.setFailedChapterCount(failed); bookRepository.save(book);
    }

    private BookCrawlerParser parser(CrawlerSite site) {
        if (site.getParserType() == CrawlerSite.ParserType.CUSTOM) {
            if (site.getParserBean() == null || site.getParserBean().isBlank()) throw new IllegalStateException("CUSTOM 网站未配置 parserBean");
            return applicationContext.getBean(site.getParserBean(), BookCrawlerParser.class);
        }
        return parsers.stream().filter(p -> p.supports(site)).findFirst().orElseThrow(() -> new IllegalStateException("没有可用的网站解析器"));
    }

    private void fail(String id, String message) {
        synchronized (taskLock(id)) {
            taskRepository.findById(id).filter(task -> task.getStatus() == CrawlerTask.TaskStatus.RUNNING
                    || task.getStatus() == CrawlerTask.TaskStatus.WAITING).ifPresent(task -> {
                task.setStatus(CrawlerTask.TaskStatus.FAILED); task.setErrorMessage(message);
                task.setFinishedAt(LocalDateTime.now()); taskRepository.save(task);
                if (task.getCrawlerBook() != null
                        && task.getType() != CrawlerTask.TaskType.BOOK_METADATA) {
                    task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.FAILED);
                    bookRepository.save(task.getCrawlerBook());
                }
                recordCrawlerEvent(task, "采集任务失败", "原因：" + message);
            });
        }
    }

    private CrawlerTask runningTask(String taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getStatus() == CrawlerTask.TaskStatus.RUNNING)
                .orElse(null);
    }

    private CrawlerTask saveProgressIfRunning(CrawlerTask source) {
        synchronized (taskLock(source.getId())) {
            CrawlerTask current = runningTask(source.getId());
            if (current == null) return null;
            current.setTotalCount(source.getTotalCount());
            current.setSuccessCount(source.getSuccessCount());
            current.setNewBookCount(source.getNewBookCount());
            current.setDuplicateCount(source.getDuplicateCount());
            current.setScannedPageCount(source.getScannedPageCount());
            current.setFailedCount(source.getFailedCount());
            current.setWaitingCount(source.getWaitingCount());
            current.setCurrentChapter(source.getCurrentChapter());
            current.setAverageRequestMillis(source.getAverageRequestMillis());
            return taskRepository.save(current);
        }
    }

    private CrawlerTask finishIfRunning(CrawlerTask source) {
        synchronized (taskLock(source.getId())) {
            CrawlerTask current = runningTask(source.getId());
            if (current == null) return null;
            current.setStatus(source.getStatus());
            current.setTotalCount(source.getTotalCount());
            current.setSuccessCount(source.getSuccessCount());
            current.setNewBookCount(source.getNewBookCount());
            current.setDuplicateCount(source.getDuplicateCount());
            current.setScannedPageCount(source.getScannedPageCount());
            current.setFailedCount(source.getFailedCount());
            current.setWaitingCount(source.getWaitingCount());
            current.setCurrentChapter(source.getCurrentChapter());
            current.setAverageRequestMillis(source.getAverageRequestMillis());
            current.setFinishedAt(source.getFinishedAt());
            current.setErrorMessage(source.getErrorMessage());
            return taskRepository.save(current);
        }
    }

    private boolean isStopRequested(String taskId) {
        return taskRepository.findById(taskId)
                .map(task -> task.getStatus() == CrawlerTask.TaskStatus.PAUSED
                        || task.getStatus() == CrawlerTask.TaskStatus.WAITING
                        || task.getStatus() == CrawlerTask.TaskStatus.CANCELLED)
                .orElse(true);
    }

    private Object taskLock(String taskId) {
        return taskLocks[Math.floorMod(taskId.hashCode(), taskLocks.length)];
    }

    private static Object[] createTaskLocks() {
        Object[] locks = new Object[64];
        Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        int defaultConcurrency = crawlerSettingsService.maxConcurrentTasks();
        for (CrawlerSite site : siteRepository.findAll()) {
            taskQueueRepository.findBySite(site).orElseGet(() -> taskQueueRepository.save(
                    CrawlerTaskQueue.builder().site(site).maxConcurrentTasks(defaultConcurrency).build()));
        }
        applyConcurrencyLimit(configuredQueueConcurrency());
        List<CrawlerTask> interruptedTasks = new ArrayList<>(taskRepository.findByStatusIn(
                List.of(CrawlerTask.TaskStatus.RUNNING, CrawlerTask.TaskStatus.WAITING)));
        interruptedTasks.sort(Comparator.comparingInt((CrawlerTask task) -> priorityRank(task.getPriority()))
                .thenComparingInt(task -> task.getStatus() == CrawlerTask.TaskStatus.RUNNING ? 0 : 1)
                .thenComparing(CrawlerTask::getQueueOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CrawlerTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        List<CrawlerTask> recoveredTasks = new ArrayList<>();
        for (CrawlerTask task : interruptedTasks) {
            CrawlerTask.TaskStatus interruptedStatus = task.getStatus();
            try {
                task.setStatus(CrawlerTask.TaskStatus.WAITING);
                task.setErrorMessage(null);
                task.setFinishedAt(null);
                task.setCurrentChapter(null);
                taskRepository.save(task);
                if (task.getCrawlerBook() != null
                        && task.getType() != CrawlerTask.TaskType.BOOK_METADATA) {
                    CrawlerBook book = task.getCrawlerBook();
                    List<CrawlerChapter> interruptedChapters = chapterRepository
                            .findByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.CRAWLING);
                    interruptedChapters.forEach(chapter -> chapter.setCrawlStatus(
                            CrawlerChapter.CrawlStatus.NOT_CRAWLED));
                    if (!interruptedChapters.isEmpty()) chapterRepository.saveAll(interruptedChapters);
                    book.setCrawlStatus(CrawlerBook.CrawlStatus.WAITING);
                    bookRepository.save(book);
                }
                recoveredTasks.add(task);
                log.info("[采集任务] 服务启动时恢复遗留任务: taskId={}, previousStatus={}, priority={}, book={}",
                        task.getId(), interruptedStatus, task.getPriority(), bookName(task.getCrawlerBook()));
                recordCrawlerEvent(task, "服务重启后任务已自动恢复",
                        "重启前状态：" + interruptedStatus + "；已重新进入等待队列");
            } catch (Exception exception) {
                log.error("[采集任务] 恢复遗留任务失败: taskId={}", task.getId(), exception);
                pauseAfterRecoveryFailure(task, "服务重启后恢复任务状态失败：" + userMessage(exception));
            }
        }
        recoveredTasks.forEach(task -> {
            try {
                submit(task.getId());
            } catch (Exception exception) {
                log.error("[采集任务] 恢复任务重新入队失败: taskId={}", task.getId(), exception);
                pauseAfterRecoveryFailure(task, "服务重启后重新入队失败：" + userMessage(exception));
            }
        });
        dispatchWaitingTasks();
    }

    private void pauseAfterRecoveryFailure(CrawlerTask task, String message) {
        try {
            task.setStatus(CrawlerTask.TaskStatus.PAUSED);
            task.setErrorMessage(message);
            taskRepository.save(task);
            if (task.getCrawlerBook() != null
                    && task.getType() != CrawlerTask.TaskType.BOOK_METADATA) {
                task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.PAUSED);
                bookRepository.save(task.getCrawlerBook());
            }
            recordCrawlerEvent(task, "服务重启后任务恢复失败", message);
        } catch (Exception exception) {
            log.error("[采集任务] 保存任务恢复失败状态异常: taskId={}", task.getId(), exception);
        }
    }

    @PreDestroy public void shutdown() {
        shuttingDown = true;
        executor.shutdownNow();
    }

    private String sha256(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    private String externalId(String url) { String path = URI.create(url).getPath().replaceAll("/+$", ""); String id = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", ""); return id.isBlank() ? Integer.toHexString(url.hashCode()) : id; }
    private List<CrawlerBook> ownedBooks(User user, List<Long> ids) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
        List<CrawlerBook> books = unique.stream().map(id -> managementService.ownedBook(user, id)).toList();
        if (books.size() != unique.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量书籍参数无效");
        return books;
    }
    private void requireEnabled(CrawlerSite site) { if (!Boolean.TRUE.equals(site.getEnabled())) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先启用该采集网站"); }
    private void requireRule(CrawlerSite site) { if (site.getRule() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先在规则管理中启用一条采集规则"); }
    private void ensureNoActiveTask(CrawlerBook book) { if (taskRepository.existsByCrawlerBookAndStatusIn(book, ACTIVE_STATUSES)) throw new ResponseStatusException(HttpStatus.CONFLICT, "该书已有运行中或暂停的采集任务"); }
    private boolean ownedBy(CrawlerTask task, User user) { return task.getUser() == user || (user.getId() != null && Objects.equals(task.getUser().getId(), user.getId())); }
    private int priorityRank(CrawlerTask.Priority priority) { return switch (priority) { case HIGH -> 0; case NORMAL -> 1; case LOW -> 2; }; }
    private CrawlerBook.DiscoveryStatus discoveryStatus(CrawlerBook book) { return book.getDiscoveryStatus() == null ? CrawlerBook.DiscoveryStatus.ACTIVE : book.getDiscoveryStatus(); }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }

    static final class RequestFailureGuard {
        private final int limit;
        private int consecutiveFailures;

        RequestFailureGuard(int limit) { this.limit = Math.max(1, limit); }

        void success() { consecutiveFailures = 0; }

        void failure(Exception exception) {
            consecutiveFailures++;
            if (consecutiveFailures >= limit) {
                throw new IllegalStateException("连续请求失败 " + consecutiveFailures
                        + " 次，已达到任务停止上限；最后错误：" + userMessage(exception), exception);
            }
        }

        int consecutiveFailures() { return consecutiveFailures; }
    }
    private boolean hasParsedContent(CrawlerChapter chapter) { return chapter.getContent() != null && !chapter.getContent().isBlank(); }
    private int finishedCount(CrawlerTask task) { return value(task.getSuccessCount(), 0) + value(task.getFailedCount(), 0); }
    private int progress(CrawlerTask task) { return percentage(finishedCount(task), value(task.getTotalCount(), 0)); }
    private String progressDetails(CrawlerTask task) { return "进度：" + finishedCount(task) + "/"
            + value(task.getTotalCount(), 0) + "（" + progress(task) + "%）"; }
    private int percentage(int finished, int total) { return total <= 0 ? 0 : Math.min(100, Math.max(0, (int) Math.round(finished * 100.0 / total))); }
    private String bookName(CrawlerBook book) { return book == null || book.getBookName() == null || book.getBookName().isBlank() ? "-" : book.getBookName(); }
    private void recordCrawlerEvent(CrawlerTask task, String event, String details) {
        recordCrawlerLog(task, event, details);
        try {
            CrawlerBook crawlerBook = task.getCrawlerBook();
            String subject = crawlerBook == null ? task.getSite().getSiteName() : bookName(crawlerBook);
            Long libraryBookId = crawlerBook == null || crawlerBook.getLibraryBook() == null
                    ? null : crawlerBook.getLibraryBook().getId();
            String common = "任务ID：" + task.getId() + "；任务类型：" + task.getType()
                    + "；网站：" + task.getSite().getSiteName();
            operationLogService.recordEntry(task.getUser(), OperationLog.Action.CRAWLER_TASK,
                    libraryBookId, crawlerBook == null ? null : bookName(crawlerBook),
                    shortText(event + "：" + subject, 500),
                    details == null || details.isBlank() ? common : common + "；" + details);
        } catch (Exception exception) {
            log.warn("[采集任务] 写入系统操作日志失败: taskId={}, event={}", task.getId(), event, exception);
        }
    }
    void recordCrawlerDetail(CrawlerTask task, String event, String details) {
        recordCrawlerLog(task, event, details);
    }
    private void recordCrawlerLog(CrawlerTask task, String event, String details) {
        try {
            CrawlerBook crawlerBook = task.getCrawlerBook();
            String subject = crawlerBook == null ? task.getSite().getSiteName() : bookName(crawlerBook);
            String common = "任务ID：" + task.getId() + "；任务类型：" + task.getType()
                    + "；网站：" + task.getSite().getSiteName();
            taskLogRepository.save(CrawlerTaskLog.builder()
                    .user(task.getUser())
                    .crawlerBookId(crawlerBook == null ? null : crawlerBook.getId())
                    .taskId(task.getId())
                    .description(shortText(event + "：" + subject, 500))
                    .details(details == null || details.isBlank() ? common : common + "；" + details)
                    .build());
        } catch (Exception exception) {
            log.warn("[采集任务] 写入爬虫专用日志失败: taskId={}, event={}", task.getId(), event, exception);
        }
    }
    private String shortText(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        return count <= maxCodePoints ? value : value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }
    static String contentPreview(String content) {
        if (content == null || content.isBlank()) return "(空)";
        String normalized = content.replaceAll("[\\p{Cc}\\p{Cf}\\s]+", " ").trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= 50) return normalized;
        return normalized.substring(0, normalized.offsetByCodePoints(0, 50)) + "…";
    }
    static ContentMarkerMatch matchedContentMarker(CrawlerSite site, String content) {
        if (site == null || site.getContentMarkersJson() == null || site.getContentMarkersJson().isBlank()
                || content == null || content.isBlank()) return null;
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        return configuredContentMarkers(site).stream()
                .filter(marker -> normalizedContent.contains(marker.marker().toLowerCase(Locale.ROOT)))
                .map(marker -> new ContentMarkerMatch(marker.marker(),
                        ContentMarkerStatus.valueOf(marker.status().toUpperCase(Locale.ROOT))))
                .findFirst().orElse(null);
    }
    private static List<ContentMarkerPayload> configuredContentMarkers(CrawlerSite site) {
        String stored = site.getContentMarkersJson().trim();
        if (!stored.startsWith("[")) {
            return stored.lines().map(String::trim).filter(marker -> !marker.isBlank())
                    .map(marker -> new ContentMarkerPayload(marker, "FAILED")).toList();
        }
        try {
            return CONTENT_MARKER_MAPPER.readValue(stored,
                    new TypeReference<List<ContentMarkerPayload>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("正文特征配置数据损坏", exception);
        }
    }
    enum ContentMarkerStatus { FAILED, PENDING_RELEASE }
    record ContentMarkerMatch(String marker, ContentMarkerStatus status) { }
    private static String userMessage(Exception e) { if (e instanceof ResponseStatusException r && r.getReason() != null) return r.getReason(); return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
