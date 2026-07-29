package com.aibook.service;

import com.aibook.config.ScanSettings;
import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.ScanDirectoryRepository;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理目录扫描后台任务及其实时进度。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScanDirectoryTaskService {

    private static final AtomicInteger TASK_THREAD_SEQUENCE = new AtomicInteger();

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final FileScannerService fileScannerService;
    private final Map<ScanTaskKey, ScanTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(
            2,
            runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "directory-scan-task-" + TASK_THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 启动任务；同一用户的同一目录正在扫描时直接返回现有任务。
     */
    public synchronized Map<String, Object> startScan(Long directoryId, User user) {
        Long userId = user.getId();
        ScanTaskKey key = new ScanTaskKey(directoryId, userId);
        ScanTask existing = tasks.get(key);
        if (existing != null && existing.isActive()) {
            return existing.toMap();
        }

        ScanDirectory directory = scanDirectoryRepository.findByIdAndUser(directoryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", directoryId));
        Path path = Path.of(directory.getPath());
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("目录不存在: " + directory.getPath());
        }

        ScanTask task = new ScanTask(
                UUID.randomUUID().toString(),
                directoryId,
                userId,
                directory.getPath(),
                directory.defaultCategoryId(),
                ScanSettings.normalizeThreadCount(user.getScanThreadCount()));
        tasks.put(key, task);
        taskExecutor.submit(() -> executeTask(task));
        return task.toMap();
    }

    /**
     * 获取当前目录最近一次扫描任务的进度。
     */
    public Map<String, Object> getProgress(Long directoryId, User user) {
        scanDirectoryRepository.findByIdAndUser(directoryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", directoryId));
        ScanTask task = tasks.get(new ScanTaskKey(directoryId, user.getId()));
        if (task == null) {
            Map<String, Object> idle = new LinkedHashMap<>();
            idle.put("directoryId", directoryId);
            idle.put("status", ScanTaskStatus.IDLE.name());
            idle.put("progress", 0);
            idle.put("totalCount", 0);
            idle.put("scannedCount", 0);
            return idle;
        }
        return task.toMap();
    }

    private void executeTask(ScanTask task) {
        task.status = ScanTaskStatus.RUNNING;
        task.message = "正在扫描";
        task.startedAt = System.currentTimeMillis();
        try {
            fileScannerService.scanDirectory(
                    task.path,
                    task.userId,
                    task.threadCount,
                    task.defaultCategoryId,
                    task.result);

            if (task.result.getErrors().isEmpty()) {
                task.status = ScanTaskStatus.COMPLETED;
                task.message = "扫描完成";
                updateDirectory(task);
            } else {
                task.status = ScanTaskStatus.FAILED;
                task.message = task.result.getErrors().get(0).get("message");
            }
        } catch (Exception e) {
            task.status = ScanTaskStatus.FAILED;
            task.message = e.getMessage() == null ? "扫描失败" : e.getMessage();
            log.error("目录扫描任务失败: taskId={}, path={}", task.taskId, task.path, e);
        } finally {
            task.finishedAt = System.currentTimeMillis();
            log.info(
                    "目录扫描任务结束: taskId={}, status={}, scanned={}/{}",
                    task.taskId,
                    task.status,
                    task.result.getScannedCount(),
                    task.result.getTotalCount());
        }
    }

    private void updateDirectory(ScanTask task) {
        ScanDirectory directory = scanDirectoryRepository
                .findByIdAndUserId(task.directoryId, task.userId)
                .orElse(null);
        if (directory == null) {
            return;
        }
        directory.setLastScanTime(LocalDateTime.now());
        directory.setBookCount(
                task.result.getNewCount() + task.result.getSkippedCount());
        scanDirectoryRepository.save(directory);
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
    }

    private record ScanTaskKey(Long directoryId, Long userId) {
    }

    private enum ScanTaskStatus {
        IDLE,
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private static final class ScanTask {

        private final String taskId;
        private final Long directoryId;
        private final Long userId;
        private final String path;
        private final Long defaultCategoryId;
        private final int threadCount;
        private final FileScannerService.ScanResult result =
                new FileScannerService.ScanResult();
        private volatile ScanTaskStatus status = ScanTaskStatus.PENDING;
        private volatile String message = "等待扫描";
        private volatile long startedAt;
        private volatile long finishedAt;

        private ScanTask(
                String taskId,
                Long directoryId,
                Long userId,
                String path,
                Long defaultCategoryId,
                int threadCount) {
            this.taskId = taskId;
            this.directoryId = directoryId;
            this.userId = userId;
            this.path = path;
            this.defaultCategoryId = defaultCategoryId;
            this.threadCount = threadCount;
        }

        private boolean isActive() {
            return status == ScanTaskStatus.PENDING
                    || status == ScanTaskStatus.RUNNING;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("taskId", taskId);
            progress.put("directoryId", directoryId);
            progress.put("status", status.name());
            progress.put("message", message);
            progress.put("progress", result.getProgressPercent());
            progress.put("totalCount", result.getTotalCount());
            progress.put("scannedCount", result.getScannedCount());
            progress.put("newBooks", result.getNewCount());
            progress.put("skippedBooks", result.getSkippedCount());
            progress.put("failedBooks", result.getFailedCount());
            progress.put("currentFile", result.getCurrentFile());
            progress.put("threadCount", threadCount);
            progress.put("startedAt", startedAt);
            progress.put("finishedAt", finishedAt);
            return progress;
        }
    }
}
