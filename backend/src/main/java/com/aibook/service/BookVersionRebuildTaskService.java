package com.aibook.service;

import com.aibook.dto.BookVersionRebuildTaskDTO;
import com.aibook.model.entity.User;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookVersionRebuildTaskService {

    private final BookVersionAggregationService aggregationService;
    private final Map<String, RebuildTask> tasks = new ConcurrentHashMap<>();
    private final Map<Long, String> activeTaskByUser = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "book-version-rebuild");
        thread.setDaemon(true);
        return thread;
    });

    public synchronized BookVersionRebuildTaskDTO start(User user) {
        String activeTaskId = activeTaskByUser.get(user.getId());
        if (activeTaskId != null) {
            RebuildTask activeTask = tasks.get(activeTaskId);
            if (activeTask != null && activeTask.isActive()) {
                return activeTask.toDTO();
            }
        }

        RebuildTask task = new RebuildTask(
                UUID.randomUUID().toString(), user.getId(), user);
        tasks.put(task.taskId, task);
        activeTaskByUser.put(user.getId(), task.taskId);
        executor.submit(() -> execute(task));
        return task.toDTO();
    }

    public BookVersionRebuildTaskDTO get(String taskId, User user) {
        RebuildTask task = tasks.get(taskId);
        if (task == null || !task.userId.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "重建任务不存在");
        }
        return task.toDTO();
    }

    private void execute(RebuildTask task) {
        task.status = "RUNNING";
        task.message = "正在读取书籍索引";
        task.startedAt = System.currentTimeMillis();
        try {
            BookVersionAggregationService.RebuildPlan plan =
                    aggregationService.buildPlan(task.userId);
            task.totalBooks = plan.totalBooks();
            task.matchedGroups = (int) plan.groups().stream()
                    .filter(group -> group.bookIds().size() > 1)
                    .count();
            task.message = "正在聚合书籍版本";

            for (BookVersionAggregationService.RebuildGroup group : plan.groups()) {
                List<Long> ids = group.bookIds();
                if (ids.isEmpty()) {
                    continue;
                }
                Long primaryId = ids.get(0);
                task.currentBookTitle = group.primaryTitle();
                try {
                    aggregationService.ensurePrimaryVersion(primaryId, task.user);
                } catch (Exception exception) {
                    task.failedBooks++;
                    task.addError(group.primaryTitle(), exception);
                }
                task.processedBooks++;

                if (ids.size() == 1) {
                    task.skippedBooks++;
                    continue;
                }
                for (int index = 1; index < ids.size(); index++) {
                    try {
                        int versions = aggregationService.aggregatePair(
                                primaryId, ids.get(index), task.user);
                        if (versions > 0) {
                            task.mergedBooks++;
                            task.aggregatedVersions += versions;
                        } else {
                            task.skippedBooks++;
                        }
                    } catch (Exception exception) {
                        task.failedBooks++;
                        task.addError(group.primaryTitle(), exception);
                        log.warn(
                                "聚合书籍版本失败: primaryId={}, duplicateId={}",
                                primaryId,
                                ids.get(index),
                                exception);
                    } finally {
                        task.processedBooks++;
                    }
                }
                task.completedGroups++;
            }
            task.status = task.failedBooks == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
            task.message = task.failedBooks == 0 ? "多版本重建完成" : "重建完成，部分书籍失败";
        } catch (Exception exception) {
            task.status = "FAILED";
            task.message = exception.getMessage() == null
                    ? "多版本重建失败"
                    : exception.getMessage();
            task.addError("任务", exception);
            log.error("多版本重建任务失败: taskId={}", task.taskId, exception);
        } finally {
            task.currentBookTitle = null;
            task.finishedAt = System.currentTimeMillis();
            activeTaskByUser.remove(task.userId, task.taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class RebuildTask {
        private final String taskId;
        private final Long userId;
        private final User user;
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private volatile String status = "PENDING";
        private volatile String message = "等待开始";
        private volatile int totalBooks;
        private volatile int processedBooks;
        private volatile int matchedGroups;
        private volatile int completedGroups;
        private volatile int mergedBooks;
        private volatile int aggregatedVersions;
        private volatile int skippedBooks;
        private volatile int failedBooks;
        private volatile String currentBookTitle;
        private volatile long startedAt;
        private volatile long finishedAt;

        private RebuildTask(String taskId, Long userId, User user) {
            this.taskId = taskId;
            this.userId = userId;
            this.user = user;
        }

        private boolean isActive() {
            return "PENDING".equals(status) || "RUNNING".equals(status);
        }

        private void addError(String title, Exception exception) {
            if (errors.size() >= 20) {
                return;
            }
            String reason = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            errors.add(title + "：" + reason);
        }

        private BookVersionRebuildTaskDTO toDTO() {
            long now = finishedAt > 0 ? finishedAt : System.currentTimeMillis();
            long elapsed = startedAt > 0 ? Math.max(0, now - startedAt) : 0;
            return BookVersionRebuildTaskDTO.builder()
                    .taskId(taskId)
                    .status(status)
                    .message(message)
                    .totalBooks(totalBooks)
                    .processedBooks(processedBooks)
                    .matchedGroups(matchedGroups)
                    .completedGroups(completedGroups)
                    .mergedBooks(mergedBooks)
                    .aggregatedVersions(aggregatedVersions)
                    .skippedBooks(skippedBooks)
                    .failedBooks(failedBooks)
                    .currentBookTitle(currentBookTitle)
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .elapsedMs(elapsed)
                    .errors(List.copyOf(errors))
                    .build();
        }
    }
}
