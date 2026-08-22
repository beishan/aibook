package com.aibook.service;

import com.aibook.config.ScanSettings;
import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.ScanRecord;
import com.aibook.model.entity.User;
import com.aibook.repository.ScanDirectoryRepository;
import com.aibook.repository.ScanRecordRepository;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.dto.ScanRecordDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final BookScanSourceRepository bookScanSourceRepository;
    private final FileScannerService fileScannerService;
    private final ScanRecordRepository scanRecordRepository;
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
        if (!Boolean.TRUE.equals(directory.getEnabled())) {
            throw new IllegalArgumentException("扫描目录已禁用，请先启用后再扫描");
        }
        Path path = Path.of(directory.getPath());
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("目录不存在: " + directory.getPath());
        }

        ScanRecord record = ScanRecord.builder()
                .taskId(UUID.randomUUID().toString())
                .directoryId(directoryId)
                .directoryPath(directory.getPath())
                .user(user)
                .status(ScanRecord.Status.PENDING)
                .message("等待扫描")
                .totalCount(0)
                .scannedCount(0)
                .newBooks(0)
                .skippedBooks(0)
                .failedBooks(0)
                .threadCount(ScanSettings.normalizeThreadCount(user.getScanThreadCount()))
                .startedAt(LocalDateTime.now())
                .build();
        scanRecordRepository.save(record);

        ScanTask task = new ScanTask(
                record,
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
            idle.put("status", "IDLE");
            idle.put("progress", 0);
            idle.put("totalCount", 0);
            idle.put("scannedCount", 0);
            return idle;
        }
        if (task.isActive()) {
            persistRecord(task, false);
        }
        return task.toMap();
    }

    public Page<ScanRecordDTO> getHistory(
            User user,
            Long directoryId,
            String statusValue,
            Pageable pageable) {
        ScanRecord.Status status = null;
        if (statusValue != null && !statusValue.isBlank()) {
            try {
                status = ScanRecord.Status.valueOf(statusValue.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("不支持的扫描状态: " + statusValue);
            }
        }
        return scanRecordRepository.findHistory(user, directoryId, status, pageable)
                .map(this::toDTO);
    }

    @PostConstruct
    public void markInterruptedTasksAsFailed() {
        List<ScanRecord> interrupted = scanRecordRepository.findByStatusIn(
                List.of(ScanRecord.Status.PENDING, ScanRecord.Status.RUNNING));
        if (interrupted.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        interrupted.forEach(record -> {
            record.setStatus(ScanRecord.Status.FAILED);
            record.setMessage("服务重启，扫描任务已中断");
            record.setFinishedAt(now);
            if (record.getStartedAt() != null) {
                record.setDurationMs(
                        Duration.between(record.getStartedAt(), now).toMillis());
            }
        });
        scanRecordRepository.saveAll(interrupted);
    }

    private void executeTask(ScanTask task) {
        task.status = ScanRecord.Status.RUNNING;
        task.message = "正在扫描";
        task.startedAt = System.currentTimeMillis();
        task.record.setStartedAt(LocalDateTime.now());
        persistRecord(task, false);
        try {
            fileScannerService.scanDirectory(
                    task.path,
                    task.userId,
                    task.threadCount,
                    task.defaultCategoryId,
                    task.directoryId,
                    task.result);

            if (task.result.getErrors().isEmpty()) {
                task.status = ScanRecord.Status.COMPLETED;
                task.message = "扫描完成";
                updateDirectory(task);
            } else {
                task.status = ScanRecord.Status.FAILED;
                task.message = task.result.getErrors().get(0).get("message");
            }
        } catch (Exception e) {
            task.status = ScanRecord.Status.FAILED;
            task.message = e.getMessage() == null ? "扫描失败" : e.getMessage();
            log.error("目录扫描任务失败: taskId={}, path={}", task.taskId, task.path, e);
        } finally {
            task.finishedAt = System.currentTimeMillis();
            persistRecord(task, true);
            log.info(
                    "目录扫描任务结束: taskId={}, status={}, scanned={}/{}",
                    task.taskId,
                    task.status,
                    task.result.getScannedCount(),
                    task.result.getTotalCount());
        }
    }

    private void persistRecord(ScanTask task, boolean finished) {
        ScanRecord record = task.record;
        record.setStatus(task.status);
        record.setMessage(task.message);
        record.setTotalCount(task.result.getTotalCount());
        record.setScannedCount(task.result.getScannedCount());
        record.setNewBooks(task.result.getNewCount());
        record.setSkippedBooks(task.result.getSkippedCount());
        record.setFailedBooks(task.result.getFailedCount());
        if (finished) {
            record.setFinishedAt(LocalDateTime.now());
            record.setDurationMs(Math.max(0, task.finishedAt - task.startedAt));
            List<Map<String, String>> details = new ArrayList<>();
            details.addAll(task.result.getErrors());
            details.addAll(task.result.getFailedBooks());
            record.setErrorDetails(details.isEmpty() ? null : details.toString());
        }
        scanRecordRepository.save(record);
    }

    private ScanRecordDTO toDTO(ScanRecord record) {
        return ScanRecordDTO.builder()
                .id(record.getId())
                .taskId(record.getTaskId())
                .directoryId(record.getDirectoryId())
                .directoryPath(record.getDirectoryPath())
                .status(record.getStatus().name())
                .message(record.getMessage())
                .totalCount(record.getTotalCount())
                .scannedCount(record.getScannedCount())
                .newBooks(record.getNewBooks())
                .skippedBooks(record.getSkippedBooks())
                .failedBooks(record.getFailedBooks())
                .threadCount(record.getThreadCount())
                .durationMs(record.getDurationMs())
                .startedAt(record.getStartedAt())
                .finishedAt(record.getFinishedAt())
                .errorDetails(record.getErrorDetails())
                .build();
    }

    private void updateDirectory(ScanTask task) {
        ScanDirectory directory = scanDirectoryRepository
                .findByIdAndUserId(task.directoryId, task.userId)
                .orElse(null);
        if (directory == null) {
            return;
        }
        directory.setLastScanTime(LocalDateTime.now());
        long bookCount = bookScanSourceRepository
                .countDistinctActiveBooksByScanDirectoryAndUser(directory, directory.getUser());
        directory.setBookCount(Math.toIntExact(bookCount));
        task.bookCount = directory.getBookCount();
        scanDirectoryRepository.save(directory);
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
    }

    private record ScanTaskKey(Long directoryId, Long userId) {
    }

    private static final class ScanTask {

        private final ScanRecord record;
        private final String taskId;
        private final Long directoryId;
        private final Long userId;
        private final String path;
        private final Long defaultCategoryId;
        private final int threadCount;
        private final FileScannerService.ScanResult result =
                new FileScannerService.ScanResult();
        private volatile ScanRecord.Status status = ScanRecord.Status.PENDING;
        private volatile String message = "等待扫描";
        private volatile long startedAt;
        private volatile long finishedAt;
        private volatile Integer bookCount;

        private ScanTask(
                ScanRecord record,
                Long directoryId,
                Long userId,
                String path,
                Long defaultCategoryId,
                int threadCount) {
            this.record = record;
            this.taskId = record.getTaskId();
            this.directoryId = directoryId;
            this.userId = userId;
            this.path = path;
            this.defaultCategoryId = defaultCategoryId;
            this.threadCount = threadCount;
        }

        private boolean isActive() {
            return status == ScanRecord.Status.PENDING
                    || status == ScanRecord.Status.RUNNING;
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
            if (bookCount != null) {
                progress.put("bookCount", bookCount);
            }
            return progress;
        }
    }
}
