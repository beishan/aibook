package com.aibook.service;

import com.aibook.dto.BookSimilarityScanDTO;
import com.aibook.dto.BookSimilarityScanDTO.Group;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class BookSimilarityScanService {
    private final BookRepository bookRepository;
    private final Map<Long, Task> active = new ConcurrentHashMap<>();
    private final Cache<String, Task> completed = Caffeine.newBuilder()
            .maximumSize(20).expireAfterWrite(Duration.ofMinutes(30)).build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10), runnable -> {
                Thread thread = new Thread(runnable, "book-title-similarity-scan");
                thread.setDaemon(true);
                return thread;
            });

    public synchronized BookSimilarityScanDTO start(User user) {
        Task running = active.get(user.getId());
        if (running != null && ("PENDING".equals(running.status) || "RUNNING".equals(running.status))) {
            return snapshot(running, 0, 20);
        }
        Task task = new Task(user.getId());
        active.put(user.getId(), task);
        try {
            executor.execute(() -> run(task, user));
        } catch (RejectedExecutionException exception) {
            active.remove(user.getId(), task);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "扫描任务繁忙，请稍后重试");
        }
        return snapshot(task, 0, 20);
    }

    public BookSimilarityScanDTO get(String taskId, User user, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "扫描结果分页参数无效");
        }
        Task task = active.get(user.getId());
        if (task == null || !task.id.equals(taskId)) {
            task = completed.getIfPresent(taskId);
        }
        if (task == null || !task.userId.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "扫描任务不存在或已过期，请重新扫描");
        }
        return snapshot(task, page, size);
    }

    private void run(Task task, User user) {
        task.status = "RUNNING";
        task.message = "正在读取全部书籍名称";
        try {
            var books = bookRepository.findSimilarityScanBooks(user);
            task.totalBooks = books.size();
            task.message = "正在匹配相似书名";
            task.groups = BookSimilarityMatcher.scan(books, count -> task.processedBooks = count);
            task.matchedBooks = task.groups.stream().mapToInt(group -> group.books().size()).sum();
            task.message = "扫描完成，请核对候选书籍后合并";
            task.status = "COMPLETED";
        } catch (Exception exception) {
            task.message = "扫描失败，请稍后重新扫描";
            task.status = "FAILED";
            log.error("相似书名扫描失败: taskId={}", task.id, exception);
        } finally {
            completed.put(task.id, task);
            active.remove(user.getId(), task);
        }
    }

    private BookSimilarityScanDTO snapshot(Task task, int page, int size) {
        String status = task.status;
        List<Group> groups = "COMPLETED".equals(status) ? task.groups : List.of();
        int from = (int) Math.min((long) page * size, groups.size());
        int to = Math.min(from + size, groups.size());
        return new BookSimilarityScanDTO(
                task.id, status, task.message, task.totalBooks, task.processedBooks,
                task.matchedBooks, groups.size(), page, size, groups.subList(from, to));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class Task {
        private final String id = UUID.randomUUID().toString();
        private final Long userId;
        private volatile String status = "PENDING";
        private volatile String message = "等待开始扫描";
        private volatile int totalBooks;
        private volatile int processedBooks;
        private volatile int matchedBooks;
        private volatile List<Group> groups = List.of();

        private Task(Long userId) {
            this.userId = userId;
        }
    }
}
