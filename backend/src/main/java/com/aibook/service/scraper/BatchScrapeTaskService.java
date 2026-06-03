package com.aibook.service.scraper;

import com.aibook.dto.ScrapeTaskDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 批量刮削任务服务
 * 管理异步任务执行、SSE连接和任务状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchScrapeTaskService {

    private final MetadataScrapingService metadataScrapingService;
    private final BookRepository bookRepository;

    /**
     * 随机数生成器，用于生成不定长延迟
     */
    private final Random random = new Random();

    /**
     * 刮削间隔配置（毫秒）
     * 最小间隔：3秒
     * 最大间隔：10秒
     * 豆瓣等国内源建议更长间隔
     */
    private static final int MIN_DELAY_MS = 3000;
    private static final int MAX_DELAY_MS = 10000;

    /**
     * 单线程执行器，防止并发任务冲击外部API
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "batch-scrape-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * 任务状态存储（内存）
     */
    private final Map<String, ScrapeTask> tasks = new ConcurrentHashMap<>();

    /**
     * SSE连接存储
     */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 当前正在运行的任务ID（只允许一个任务运行）
     */
    private volatile String currentTaskId = null;

    /**
     * 创建批量刮削任务（指定书籍ID）
     */
    public String createTask(List<Long> bookIds, User user, boolean forceUpdate) {
        // 检查是否有任务正在运行
        if (currentTaskId != null) {
            ScrapeTask existingTask = tasks.get(currentTaskId);
            if (existingTask != null && existingTask.getStatus() == TaskStatus.RUNNING) {
                throw new IllegalStateException("已有任务正在运行: " + currentTaskId);
            }
        }

        // 查询书籍
        List<Book> allBooks = bookRepository.findByIdInAndUser(bookIds, user);
        if (allBooks.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的书籍");
        }

        // 根据是否强制更新，决定要刮削的书籍
        List<Book> booksToScrape;
        if (forceUpdate) {
            // 强制更新：刮削所有选中的书籍
            booksToScrape = allBooks;
        } else {
            // 默认：只刮削缺少元数据的书籍（作者或描述为空）
            booksToScrape = allBooks.stream()
                    .filter(book -> book.getAuthor() == null || book.getDescription() == null)
                    .collect(Collectors.toList());

            if (booksToScrape.isEmpty()) {
                throw new IllegalArgumentException("选中的书籍都已有完整元数据，无需刮削");
            }
        }

        // 创建任务
        String taskId = UUID.randomUUID().toString();
        ScrapeTask task = ScrapeTask.builder()
                .taskId(taskId)
                .status(TaskStatus.PENDING)
                .totalBooks(booksToScrape.size())
                .books(new ArrayList<>(booksToScrape))
                .forceUpdate(forceUpdate)
                .startTime(System.currentTimeMillis())
                .results(new ArrayList<>())
                .build();

        tasks.put(taskId, task);
        currentTaskId = taskId;

        // 异步执行
        executor.submit(() -> executeTask(task));

        log.info("创建批量刮削任务: {}, 需刮削: {}/{} 本, 强制更新: {}",
                 taskId, booksToScrape.size(), allBooks.size(), forceUpdate);
        return taskId;
    }

    /**
     * 创建批量刮削任务（指定书籍ID，默认不强制更新）
     */
    public String createTask(List<Long> bookIds, User user) {
        return createTask(bookIds, user, false);
    }

    /**
     * 创建刮削所有不完整书籍的任务
     */
    public String createScrapeAllIncompleteTask(User user, boolean forceUpdate) {
        // 检查是否有任务正在运行
        if (currentTaskId != null) {
            ScrapeTask existingTask = tasks.get(currentTaskId);
            if (existingTask != null && existingTask.getStatus() == TaskStatus.RUNNING) {
                throw new IllegalStateException("已有任务正在运行: " + currentTaskId);
            }
        }

        // 查询不完整的书籍（作者或描述为空）
        List<Book> books = bookRepository.findByUserAndAuthorIsNullOrDescriptionIsNull(user);
        if (books.isEmpty()) {
            throw new IllegalArgumentException("所有书籍都已有完整元数据，无需刮削");
        }

        // 创建任务
        String taskId = UUID.randomUUID().toString();
        ScrapeTask task = ScrapeTask.builder()
                .taskId(taskId)
                .status(TaskStatus.PENDING)
                .totalBooks(books.size())
                .books(new ArrayList<>(books))
                .forceUpdate(forceUpdate)
                .startTime(System.currentTimeMillis())
                .results(new ArrayList<>())
                .build();

        tasks.put(taskId, task);
        currentTaskId = taskId;

        // 异步执行
        executor.submit(() -> executeTask(task));

        log.info("创建刮削所有不完整书籍任务: {}, 书籍数: {}, 强制更新: {}", taskId, books.size(), forceUpdate);
        return taskId;
    }

    /**
     * 创建刮削所有不完整书籍的任务（默认不强制更新）
     */
    public String createScrapeAllIncompleteTask(User user) {
        return createScrapeAllIncompleteTask(user, false);
    }

    /**
     * 获取任务状态
     */
    public ScrapeTaskDTO getTask(String taskId) {
        ScrapeTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        return convertToDTO(task);
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        ScrapeTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }

        if (task.getStatus() == TaskStatus.COMPLETED ||
            task.getStatus() == TaskStatus.FAILED ||
            task.getStatus() == TaskStatus.CANCELLED) {
            return false;
        }

        task.setCancelled(true);
        log.info("任务已取消: {}", taskId);
        return true;
    }

    /**
     * 注册SSE连接
     */
    public void addSseEmitter(String taskId, SseEmitter emitter) {
        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 连接关闭时移除
        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(e -> removeEmitter(taskId, emitter));
    }

    /**
     * 执行任务
     */
    private void executeTask(ScrapeTask task) {
        task.setStatus(TaskStatus.RUNNING);
        sendProgressUpdate(task.getTaskId());

        try {
            for (Book book : task.getBooks()) {
                // 检查是否已取消
                if (task.isCancelled()) {
                    task.setStatus(TaskStatus.CANCELLED);
                    break;
                }

                // 更新当前书名
                task.setCurrentBookTitle(book.getTitle());
                sendProgressUpdate(task.getTaskId());

                try {
                    // 执行单本刮削（每本书独立事务）
                    Book updated = metadataScrapingService.scrapeBook(book, task.isForceUpdate());

                    // 记录更新的字段
                    List<String> updatedFields = getUpdatedFields(book, updated);

                    ScrapeTaskDTO.BookScrapeResult result = ScrapeTaskDTO.BookScrapeResult.builder()
                            .bookId(book.getId())
                            .title(book.getTitle())
                            .success(true)
                            .updatedFields(updatedFields)
                            .build();

                    task.getResults().add(result);
                    task.setCompletedBooks(task.getCompletedBooks() + 1);

                    log.debug("刮削成功: {} ({}:{})", book.getTitle(),
                              task.getCompletedBooks(), task.getTotalBooks());

                } catch (Exception e) {
                    log.warn("刮削失败: {} - {} ({}:{})", book.getTitle(), e.getMessage(),
                             task.getCompletedBooks() + task.getFailedBooks(), task.getTotalBooks());

                    ScrapeTaskDTO.BookScrapeResult result = ScrapeTaskDTO.BookScrapeResult.builder()
                            .bookId(book.getId())
                            .title(book.getTitle())
                            .success(false)
                            .error(e.getMessage())
                            .build();

                    task.getResults().add(result);
                    task.setFailedBooks(task.getFailedBooks() + 1);
                }

                // 推送进度更新
                sendProgressUpdate(task.getTaskId());

                // 随机延迟，避免触发反爬机制
                // 每本书刮削完成后等待 3-10 秒
                int processed = task.getCompletedBooks() + task.getFailedBooks();
                if (processed < task.getTotalBooks()) {
                    int delayMs = MIN_DELAY_MS + random.nextInt(MAX_DELAY_MS - MIN_DELAY_MS + 1);
                    log.info("进度 {}/{}，等待 {}s 后继续刮削",
                             processed, task.getTotalBooks(), delayMs / 1000);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.debug("延迟被中断");
                    }
                }
            }

            // 设置最终状态（如果未被取消）
            if (task.getStatus() == TaskStatus.RUNNING) {
                task.setStatus(TaskStatus.COMPLETED);
            }

        } catch (Exception e) {
            log.error("任务执行异常: {}", e.getMessage(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
        } finally {
            task.setEndTime(System.currentTimeMillis());
            task.setCurrentBookTitle(null);
            sendProgressUpdate(task.getTaskId());

            // 清理当前任务ID
            if (currentTaskId != null && currentTaskId.equals(task.getTaskId())) {
                currentTaskId = null;
            }

            log.info("任务完成: {}, 状态: {}, 成功: {}, 失败: {}",
                     task.getTaskId(), task.getStatus(),
                     task.getCompletedBooks(), task.getFailedBooks());
        }
    }

    /**
     * 获取更新的字段列表
     */
    private List<String> getUpdatedFields(Book before, Book after) {
        List<String> updated = new ArrayList<>();

        if (!equals(before.getTitle(), after.getTitle())) updated.add("title");
        if (!equals(before.getAuthor(), after.getAuthor())) updated.add("author");
        if (!equals(before.getIsbn(), after.getIsbn())) updated.add("isbn");
        if (!equals(before.getPublisher(), after.getPublisher())) updated.add("publisher");
        if (!equals(before.getPublishDate(), after.getPublishDate())) updated.add("publishDate");
        if (!equals(before.getDescription(), after.getDescription())) updated.add("description");
        if (!equals(before.getCoverUrl(), after.getCoverUrl())) updated.add("coverUrl");
        if (!equals(before.getLanguage(), after.getLanguage())) updated.add("language");

        return updated;
    }

    private boolean equals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    /**
     * 发送进度更新给所有SSE客户端
     */
    private void sendProgressUpdate(String taskId) {
        ScrapeTask task = tasks.get(taskId);
        if (task == null) return;

        ScrapeTaskDTO dto = convertToDTO(task);
        List<SseEmitter> emitterList = emitters.get(taskId);

        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        List<SseEmitter> toRemove = new ArrayList<>();

        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(SseEmitter.event()
                        .name("scrape-progress")
                        .data(dto));
            } catch (IOException e) {
                log.debug("SSE连接已关闭: {}", taskId);
                toRemove.add(emitter);
            } catch (Exception e) {
                log.warn("发送SSE事件失败: {}", e.getMessage());
                toRemove.add(emitter);
            }
        }

        // 移除失效的连接
        emitterList.removeAll(toRemove);
    }

    /**
     * 移除SSE连接
     */
    private void removeEmitter(String taskId, SseEmitter emitter) {
        List<SseEmitter> emitterList = emitters.get(taskId);
        if (emitterList != null) {
            emitterList.remove(emitter);
            if (emitterList.isEmpty()) {
                emitters.remove(taskId);
            }
        }
    }

    /**
     * 转换为DTO
     */
    private ScrapeTaskDTO convertToDTO(ScrapeTask task) {
        return ScrapeTaskDTO.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus().name())
                .totalBooks(task.getTotalBooks())
                .completedBooks(task.getCompletedBooks())
                .failedBooks(task.getFailedBooks())
                .currentBookTitle(task.getCurrentBookTitle())
                .results(task.getResults())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .errorMessage(task.getErrorMessage())
                .build();
    }

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    /**
     * 内部任务对象
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ScrapeTask {
        private String taskId;
        private TaskStatus status;
        private int totalBooks;
        private int completedBooks;
        private int failedBooks;
        private String currentBookTitle;
        private List<Book> books;
        private List<ScrapeTaskDTO.BookScrapeResult> results;
        private long startTime;
        private long endTime;
        private String errorMessage;
        private volatile boolean cancelled;
        private boolean forceUpdate;

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean isForceUpdate() {
            return forceUpdate;
        }
    }
}
