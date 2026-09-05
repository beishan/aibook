package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.TaskView;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerTaskService implements ApplicationListener<ContextRefreshedEvent> {
    private final CrawlerSiteRepository siteRepository;
    private final CrawlerBookRepository bookRepository;
    private final CrawlerChapterRepository chapterRepository;
    private final CrawlerTaskRepository taskRepository;
    private final CrawlerManagementService managementService;
    private final CrawlerHttpClient httpClient;
    private final List<BookCrawlerParser> parsers;
    private final ApplicationContext applicationContext;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "crawler-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public TaskView start(User user, Long siteId, String url) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        if (!Boolean.TRUE.equals(site.getEnabled())) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先启用该采集网站");
        URI validated = httpClient.validateSiteUrl(site, url);
        String externalId = externalId(validated.toString());
        CrawlerBook book = bookRepository.findBySiteAndExternalBookId(site, externalId).orElseGet(() ->
                bookRepository.save(CrawlerBook.builder().site(site).externalBookId(externalId)
                        .bookUrl(validated.toString()).bookName("待解析书籍").discoverTime(LocalDateTime.now()).build()));
        book.setBookUrl(validated.toString());
        book.setCrawlStatus(CrawlerBook.CrawlStatus.WAITING);
        bookRepository.save(book);
        return managementService.taskView(createAndSubmit(user, site, book, CrawlerTask.TaskType.BOOK_FULL_CRAWL));
    }

    public TaskView continueBook(User user, Long bookId) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        return managementService.taskView(createAndSubmit(user, book.getSite(), book, CrawlerTask.TaskType.BOOK_FULL_CRAWL));
    }

    public TaskView retryFailures(User user, Long bookId) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(book).stream()
                .filter(ch -> ch.getCrawlStatus() == CrawlerChapter.CrawlStatus.FAILED || ch.getCrawlStatus() == CrawlerChapter.CrawlStatus.CONTENT_SUSPECTED)
                .forEach(ch -> { ch.setCrawlStatus(CrawlerChapter.CrawlStatus.NOT_CRAWLED); ch.setErrorMessage(null); chapterRepository.save(ch); });
        return managementService.taskView(createAndSubmit(user, book.getSite(), book, CrawlerTask.TaskType.BOOK_CONTENT));
    }

    public TaskView command(User user, String taskId, String command) {
        CrawlerTask task = managementService.ownedTask(user, taskId);
        switch (command) {
            case "pause" -> { if (task.getStatus() == CrawlerTask.TaskStatus.RUNNING || task.getStatus() == CrawlerTask.TaskStatus.WAITING) task.setStatus(CrawlerTask.TaskStatus.PAUSED); }
            case "cancel" -> task.setStatus(CrawlerTask.TaskStatus.CANCELLED);
            case "resume" -> { if (task.getStatus() != CrawlerTask.TaskStatus.PAUSED) throw new ResponseStatusException(HttpStatus.CONFLICT, "只有暂停任务可以继续"); task.setStatus(CrawlerTask.TaskStatus.WAITING); taskRepository.save(task); submit(task.getId()); return managementService.taskView(task); }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的任务操作");
        }
        taskRepository.save(task);
        if (task.getCrawlerBook() != null && task.getStatus() == CrawlerTask.TaskStatus.PAUSED) {
            task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.PAUSED);
            bookRepository.save(task.getCrawlerBook());
        }
        return managementService.taskView(task);
    }

    private CrawlerTask createAndSubmit(User user, CrawlerSite site, CrawlerBook book, CrawlerTask.TaskType type) {
        CrawlerTask task = taskRepository.save(CrawlerTask.builder().user(user).site(site).crawlerBook(book).type(type).build());
        submit(task.getId());
        return task;
    }

    private void submit(String id) {
        if (active.add(id)) executor.submit(() -> { try { run(id); } finally { active.remove(id); } });
    }

    private void run(String taskId) {
        CrawlerTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() == CrawlerTask.TaskStatus.CANCELLED) return;
        try {
            task.setStatus(CrawlerTask.TaskStatus.RUNNING); task.setStartedAt(task.getStartedAt() == null ? LocalDateTime.now() : task.getStartedAt());
            task.setErrorMessage(null); taskRepository.save(task);
            CrawlerBook book = task.getCrawlerBook();
            CrawlerSite site = task.getSite();
            CrawlerSiteRule rule = site.getRule();
            BookCrawlerParser parser = parser(site);

            if (task.getType() == CrawlerTask.TaskType.BOOK_FULL_CRAWL) {
                book.setCrawlStatus(CrawlerBook.CrawlStatus.CRAWLING_METADATA); bookRepository.save(book);
                CrawlerHttpClient.FetchResult detailResponse = httpClient.get(site, book.getBookUrl());
                BookCrawlerParser.ParsedBook metadata = parser.parseBookDetail(detailResponse.html(), book.getBookUrl(), rule);
                applyMetadata(book, metadata);
                book.setCrawlStatus(CrawlerBook.CrawlStatus.CRAWLING_CHAPTER_LIST); bookRepository.save(book);
                CrawlerHttpClient.FetchResult listResponse = metadata.chapterListUrl().equals(book.getBookUrl()) ? detailResponse : httpClient.get(site, metadata.chapterListUrl());
                mergeChapters(book, parser.parseChapterList(listResponse.html(), metadata.chapterListUrl(), rule));
            }

            crawlContents(task, book, site, rule, parser);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail(taskId, "任务被中断");
        } catch (Exception exception) {
            log.warn("采集任务 {} 失败", taskId, exception);
            fail(taskId, userMessage(exception));
        }
    }

    private void crawlContents(CrawlerTask task, CrawlerBook book, CrawlerSite site, CrawlerSiteRule rule, BookCrawlerParser parser) throws Exception {
        List<CrawlerChapter> pending = chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(book).stream()
                .filter(ch -> ch.getCrawlStatus() != CrawlerChapter.CrawlStatus.COMPLETED && ch.getCrawlStatus() != CrawlerChapter.CrawlStatus.IGNORED).toList();
        task.setTotalCount((int) chapterRepository.countByCrawlerBook(book));
        task.setSuccessCount((int) chapterRepository.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.COMPLETED));
        task.setWaitingCount(pending.size()); taskRepository.save(task);
        book.setCrawlStatus(CrawlerBook.CrawlStatus.CRAWLING_CONTENT); bookRepository.save(book);
        long durationTotal = 0; int requests = 0;
        for (CrawlerChapter chapter : pending) {
            CrawlerTask fresh = taskRepository.findById(task.getId()).orElseThrow();
            if (fresh.getStatus() == CrawlerTask.TaskStatus.PAUSED || fresh.getStatus() == CrawlerTask.TaskStatus.CANCELLED) return;
            task = fresh;
            task.setCurrentChapter(chapter.getChapterName()); taskRepository.save(task);
            chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.CRAWLING); chapterRepository.save(chapter);
            try {
                CrawlerHttpClient.FetchResult response = httpClient.get(site, chapter.getChapterUrl());
                durationTotal += response.durationMillis(); requests++;
                BookCrawlerParser.ParsedContent parsed = parser.parseChapter(response.html(), chapter.getChapterUrl(), rule);
                if (parsed.title() != null && !parsed.title().isBlank()) chapter.setChapterName(parsed.title());
                chapter.setContent(parsed.content()); chapter.setContentHash(sha256(parsed.content()));
                chapter.setWordCount(parsed.content().replaceAll("\\s+", "").length()); chapter.setCrawlTime(LocalDateTime.now()); chapter.setErrorMessage(null);
                boolean suspected = chapter.getWordCount() < value(rule.getMinChapterLength(), 100);
                chapter.setCrawlStatus(suspected ? CrawlerChapter.CrawlStatus.CONTENT_SUSPECTED : CrawlerChapter.CrawlStatus.COMPLETED);
            } catch (Exception exception) {
                chapter.setRetryCount(value(chapter.getRetryCount(), 0) + 1); chapter.setErrorMessage(userMessage(exception));
                chapter.setCrawlStatus(CrawlerChapter.CrawlStatus.FAILED);
            }
            chapterRepository.save(chapter);
            refreshCounts(book, task, durationTotal, requests);
        }
        refreshCounts(book, task, durationTotal, requests);
        int failed = book.getFailedChapterCount();
        task.setStatus(failed == 0 ? CrawlerTask.TaskStatus.SUCCESS : CrawlerTask.TaskStatus.PARTIAL_SUCCESS);
        task.setFinishedAt(LocalDateTime.now()); task.setCurrentChapter(null); taskRepository.save(task);
        book.setCrawlStatus(failed == 0 ? CrawlerBook.CrawlStatus.COMPLETED : CrawlerBook.CrawlStatus.PARTIAL_SUCCESS);
        book.setImportStatus(failed == 0 ? CrawlerBook.ImportStatus.READY : CrawlerBook.ImportStatus.NOT_IMPORTED);
        book.setLastCrawlTime(LocalDateTime.now()); bookRepository.save(book);
    }

    private void mergeChapters(CrawlerBook book, List<BookCrawlerParser.ParsedChapter> parsed) {
        for (BookCrawlerParser.ParsedChapter value : parsed) {
            CrawlerChapter chapter = chapterRepository.findByCrawlerBookAndExternalChapterId(book, value.externalId())
                    .orElseGet(() -> CrawlerChapter.builder().crawlerBook(book).externalChapterId(value.externalId()).build());
            chapter.setChapterIndex(value.index()); chapter.setChapterName(value.title()); chapter.setChapterUrl(value.url());
            chapterRepository.save(chapter);
        }
        book.setChapterCount((int) chapterRepository.countByCrawlerBook(book)); bookRepository.save(book);
    }

    private void applyMetadata(CrawlerBook book, BookCrawlerParser.ParsedBook m) {
        book.setBookName(m.title()); book.setAuthor(m.author()); book.setCoverUrl(m.coverUrl()); book.setDescription(m.description());
        book.setCategory(m.category()); book.setBookStatus(m.status()); book.setLatestChapter(m.latestChapter());
    }

    private void refreshCounts(CrawlerBook book, CrawlerTask task, long totalDuration, int requests) {
        int completed = (int) chapterRepository.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.COMPLETED);
        int failed = (int) (chapterRepository.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.FAILED)
                + chapterRepository.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.CONTENT_SUSPECTED));
        int total = (int) chapterRepository.countByCrawlerBook(book);
        book.setChapterCount(total); book.setCrawledChapterCount(completed); book.setFailedChapterCount(failed); bookRepository.save(book);
        task.setTotalCount(total); task.setSuccessCount(completed); task.setFailedCount(failed); task.setWaitingCount(Math.max(0, total - completed - failed));
        task.setAverageRequestMillis(requests == 0 ? 0 : totalDuration / requests); taskRepository.save(task);
    }

    private BookCrawlerParser parser(CrawlerSite site) {
        if (site.getParserType() == CrawlerSite.ParserType.CUSTOM) {
            if (site.getParserBean() == null || site.getParserBean().isBlank()) throw new IllegalStateException("CUSTOM 网站未配置 parserBean");
            return applicationContext.getBean(site.getParserBean(), BookCrawlerParser.class);
        }
        return parsers.stream().filter(p -> p.supports(site)).findFirst().orElseThrow(() -> new IllegalStateException("没有可用的网站解析器"));
    }

    private void fail(String id, String message) {
        taskRepository.findById(id).ifPresent(task -> { task.setStatus(CrawlerTask.TaskStatus.FAILED); task.setErrorMessage(message); task.setFinishedAt(LocalDateTime.now()); taskRepository.save(task); if (task.getCrawlerBook() != null) { task.getCrawlerBook().setCrawlStatus(CrawlerBook.CrawlStatus.FAILED); bookRepository.save(task.getCrawlerBook()); } });
    }

    @Override public void onApplicationEvent(ContextRefreshedEvent event) {
        taskRepository.findByStatusIn(List.of(CrawlerTask.TaskStatus.RUNNING, CrawlerTask.TaskStatus.WAITING)).forEach(task -> { task.setStatus(CrawlerTask.TaskStatus.WAITING); taskRepository.save(task); submit(task.getId()); });
    }
    @PreDestroy public void shutdown() { executor.shutdownNow(); }

    private String sha256(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    private String externalId(String url) { String path = URI.create(url).getPath().replaceAll("/+$", ""); String id = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", ""); return id.isBlank() ? Integer.toHexString(url.hashCode()) : id; }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private String userMessage(Exception e) { if (e instanceof ResponseStatusException r && r.getReason() != null) return r.getReason(); return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
