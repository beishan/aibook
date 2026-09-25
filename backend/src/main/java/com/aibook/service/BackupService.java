package com.aibook.service;

import com.aibook.dto.backup.BackupExecutionView;
import com.aibook.dto.backup.BackupTaskRequest;
import com.aibook.dto.backup.BackupTaskView;
import com.aibook.model.entity.BackupExecution;
import com.aibook.model.entity.BackupTask;
import com.aibook.repository.BackupExecutionRepository;
import com.aibook.repository.BackupTaskRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BackupService {

    private static final DateTimeFormatter RUN_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupTaskRepository taskRepository;
    private final BackupExecutionRepository executionRepository;
    @Qualifier("backupExecutor")
    private final Executor backupExecutor;

    public BackupService(
            BackupTaskRepository taskRepository,
            BackupExecutionRepository executionRepository,
            @Qualifier("backupExecutor") Executor backupExecutor) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.backupExecutor = backupExecutor;
    }

    @Value("${backup.path:/app/backups}")
    private String backupPath;

    @Value("${backup.host-path:${backup.path:/app/backups}}")
    private String backupHostPath;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String databaseUser;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    @Value("${upload.path:/app/uploads}")
    private String uploadPath;

    @Value("${crawler.storage-path:/app/crawler-data}")
    private String crawlerPath;

    @Value("${scanning.directories:/scanfolder}")
    private String booksPath;

    public List<BackupTaskView> tasks() {
        return taskRepository.findAll().stream().map(BackupTaskView::from).toList();
    }

    public List<BackupExecutionView> executions() {
        return executionRepository.findTop100ByOrderByStartedAtDesc().stream()
                .map(BackupExecutionView::from).toList();
    }

    public BackupPathView path() {
        Path root = Path.of(backupPath);
        return new BackupPathView(backupHostPath, Files.isDirectory(root), Files.isWritable(root));
    }

    @Transactional
    public BackupTaskView createTask(BackupTaskRequest request) {
        BackupTask task = new BackupTask();
        apply(task, request);
        return BackupTaskView.from(taskRepository.save(task));
    }

    @Transactional
    public BackupTaskView updateTask(long id, BackupTaskRequest request) {
        BackupTask task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "备份任务不存在"));
        apply(task, request);
        return BackupTaskView.from(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(long id) {
        if (!taskRepository.existsById(id)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "备份任务不存在");
        }
        taskRepository.deleteById(id);
    }

    public BackupExecutionView runTask(long id) {
        BackupTask task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "备份任务不存在"));
        if (!hasContent(task)) {
            throw new IllegalArgumentException("至少选择一种备份内容");
        }
        BackupExecution execution = newExecution(task);
        executionRepository.saveAndFlush(execution);
        submitExecution(execution, snapshot(task));
        return BackupExecutionView.from(execution);
    }

    @Scheduled(fixedDelayString = "${backup.scheduler-delay-ms:30000}", initialDelay = 30000)
    public synchronized void runDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        for (BackupTask task : taskRepository.findByEnabledTrueAndScheduleEnabledTrue()) {
            if (task.getNextRunAt() == null || task.getNextRunAt().isAfter(now)) continue;
            if (!hasContent(task)) continue;
            try {
                task.setNextRunAt(nextRun(task.getCronExpression(), now));
                taskRepository.save(task);
                BackupExecution execution = executionRepository.saveAndFlush(newExecution(task));
                submitExecution(execution, snapshot(task));
            } catch (RuntimeException exception) {
                log.error("无法启动定时备份 taskId={}", task.getId(), exception);
            }
        }
    }

    public BackupExecutionView runImmediately(BackupTaskRequest request) {
        if (request == null || !hasContent(request)) {
            throw new IllegalArgumentException("至少选择一种备份内容");
        }
        BackupExecution execution = new BackupExecution();
        execution.setTaskName("立即备份");
        execution.setStatus(BackupExecution.Status.QUEUED);
        execution.setContents(contentNames(request));
        execution.setDetails("备份任务已排队");
        executionRepository.saveAndFlush(execution);
        submitExecution(execution, request);
        return BackupExecutionView.from(execution);
    }

    private void submitExecution(BackupExecution execution, BackupTaskRequest config) {
        try {
            backupExecutor.execute(() -> perform(execution.getId(), config));
        } catch (RuntimeException exception) {
            updateExecution(execution.getId(), BackupExecution.Status.FAILED,
                    "备份队列已满，无法启动任务", null,
                    exception.getMessage(), 0, 0);
        }
    }

    private void apply(BackupTask task, BackupTaskRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("请填写备份任务名称");
        }
        if (!hasContent(request)) {
            throw new IllegalArgumentException("至少选择一种备份内容");
        }
        String cron = request.cronExpression() == null || request.cronExpression().isBlank()
                ? "0 0 2 * * ?" : request.cronExpression().trim();
        CronExpression.parse(cron);
        task.setName(request.name().trim());
        task.setDatabaseEnabled(request.databaseEnabled());
        task.setBooksEnabled(request.booksEnabled());
        task.setUploadsEnabled(request.uploadsEnabled());
        task.setCrawlerDataEnabled(request.crawlerDataEnabled());
        task.setScheduleEnabled(request.scheduleEnabled());
        task.setEnabled(request.enabled());
        task.setCronExpression(cron);
        task.setNextRunAt(request.enabled() && request.scheduleEnabled()
                ? nextRun(cron, LocalDateTime.now()) : null);
    }

    private LocalDateTime nextRun(String cron, LocalDateTime from) {
        CronExpression parsed = CronExpression.parse(cron);
        return parsed.next(from);
    }

    private BackupExecution newExecution(BackupTask task) {
        BackupExecution execution = new BackupExecution();
        execution.setTaskId(task.getId());
        execution.setTaskName(task.getName());
        execution.setStatus(BackupExecution.Status.QUEUED);
        execution.setContents(contentNames(task));
        execution.setDetails("备份任务已排队");
        return execution;
    }

    private BackupTaskRequest snapshot(BackupTask task) {
        return new BackupTaskRequest(
                task.getName(), task.isDatabaseEnabled(), task.isBooksEnabled(),
                task.isUploadsEnabled(), task.isCrawlerDataEnabled(),
                task.isScheduleEnabled(), task.isEnabled(), task.getCronExpression());
    }

    private void perform(long executionId, BackupTaskRequest config) {
        Path output = null;
        List<String> details = new ArrayList<>();
        long[] fileCount = {0};
        long[] totalBytes = {0};
        try {
            updateExecution(executionId, BackupExecution.Status.RUNNING,
                    "备份正在执行", null, null, 0, 0);
            ensureBackupPath();
            String stamp = LocalDateTime.now().format(RUN_STAMP);
            output = Files.createDirectories(Path.of(backupPath))
                    .resolve("aibook-" + stamp + "-" + executionId);
            Files.createDirectory(output);
            if (config.databaseEnabled()) {
                Path dump = output.resolve("database.dump");
                dumpDatabase(dump);
                long size = Files.size(dump);
                fileCount[0]++;
                totalBytes[0] += size;
                details.add("PostgreSQL 数据库：database.dump，" + size + " 字节");
            }
            if (config.booksEnabled()) {
                long beforeFiles = fileCount[0];
                long beforeBytes = totalBytes[0];
                copyDirectory(Path.of(booksPath), output.resolve("books"), fileCount, totalBytes);
                details.add("书籍文件：" + (fileCount[0] - beforeFiles) + " 个文件，"
                        + (totalBytes[0] - beforeBytes) + " 字节");
            }
            if (config.uploadsEnabled()) {
                long beforeFiles = fileCount[0];
                long beforeBytes = totalBytes[0];
                copyDirectory(Path.of(uploadPath), output.resolve("uploads"), fileCount, totalBytes);
                details.add("用户上传文件：" + (fileCount[0] - beforeFiles) + " 个文件，"
                        + (totalBytes[0] - beforeBytes) + " 字节");
            }
            if (config.crawlerDataEnabled()) {
                long beforeFiles = fileCount[0];
                long beforeBytes = totalBytes[0];
                copyDirectory(Path.of(crawlerPath), output.resolve("crawler-data"), fileCount, totalBytes);
                details.add("采集数据文件：" + (fileCount[0] - beforeFiles) + " 个文件，"
                        + (totalBytes[0] - beforeBytes) + " 字节");
            }
            updateExecution(executionId, BackupExecution.Status.SUCCESS,
                    String.join("\n", details), displayPath(output), null, fileCount[0], totalBytes[0]);
            writeManifest(output, config, details, fileCount[0], totalBytes[0]);
        } catch (Exception exception) {
            log.error("备份任务执行失败 executionId={}", executionId, exception);
            String error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            details.add(error);
            updateExecution(executionId, BackupExecution.Status.FAILED,
                    String.join("\n", details),
                    output == null ? null : displayPath(output),
                    error,
                    fileCount[0], totalBytes[0]);
            if (output != null) writeManifest(output, config, details, fileCount[0], totalBytes[0]);
        }
    }

    private void dumpDatabase(Path destination) throws IOException, InterruptedException {
        DatabaseAddress address = parseJdbcAddress(jdbcUrl);
        ProcessBuilder builder = new ProcessBuilder(
                "pg_dump", "--format=custom", "--file=" + destination,
                "--host=" + address.host(), "--port=" + address.port(),
                "--username=" + databaseUser, address.database());
        builder.environment().put("PGPASSWORD", databasePassword);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 16_000) output.append(line).append('\n');
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("pg_dump 失败，退出码 " + exitCode + ": " + output.toString().trim());
        }
    }

    private DatabaseAddress parseJdbcAddress(String value) {
        String address = value.replaceFirst("^jdbc:", "");
        java.net.URI uri = java.net.URI.create(address);
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (uri.getHost() == null || database.isBlank()) {
            throw new IllegalArgumentException("数据库连接配置无效，无法运行 pg_dump");
        }
        return new DatabaseAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 5432, database);
    }

    private void copyDirectory(Path source, Path destination, long[] fileCount, long[] totalBytes)
            throws IOException {
        if (!Files.isDirectory(source) || !Files.isReadable(source)) {
            throw new IOException("源目录不存在或不可读：" + source);
        }
        Files.createDirectories(destination);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(directory).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                Path target = destination.resolve(source.relativize(file).toString());
                Files.copy(file, target);
                fileCount[0]++;
                totalBytes[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Transactional
    protected void updateExecution(
            long id, BackupExecution.Status status, String details, String output,
            String error, long fileCount, long totalBytes) {
        BackupExecution execution = executionRepository.findById(id).orElse(null);
        if (execution == null) return;
        execution.setStatus(status);
        execution.setDetails(details);
        execution.setOutputPath(output);
        execution.setErrorMessage(error);
        execution.setFileCount(fileCount);
        execution.setFileSizeBytes(totalBytes);
        if (status == BackupExecution.Status.RUNNING) {
            execution.setStartedAt(LocalDateTime.now());
        } else if (status == BackupExecution.Status.SUCCESS
                || status == BackupExecution.Status.FAILED) {
            execution.setFinishedAt(LocalDateTime.now());
        }
        executionRepository.save(execution);
    }

    private void writeManifest(Path output, BackupTaskRequest config, List<String> details,
            long fileCount, long totalBytes) {
        try {
            String manifest = "任务：" + config.name() + "\n时间：" + LocalDateTime.now()
                    + "\n内容：" + contentNames(config) + "\n文件数：" + fileCount
                    + "\n总字节数：" + totalBytes + "\n详情：\n" + String.join("\n", details) + "\n";
            Files.writeString(output.resolve("backup-info.txt"), manifest, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log.warn("写入备份信息文件失败 path={}", output, exception);
        }
    }

    private void ensureBackupPath() {
        Path path = Path.of(backupPath);
        if (!Files.isDirectory(path) || !Files.isWritable(path)) {
            throw new IllegalStateException("备份目录不可用或后端无写入权限：" + path);
        }
    }

    private String displayPath(Path output) {
        return Path.of(backupHostPath).resolve(output.getFileName()).toString();
    }

    private boolean hasContent(BackupTaskRequest request) {
        return request.databaseEnabled() || request.booksEnabled()
                || request.uploadsEnabled() || request.crawlerDataEnabled();
    }

    private boolean hasContent(BackupTask task) {
        return task.isDatabaseEnabled() || task.isBooksEnabled()
                || task.isUploadsEnabled() || task.isCrawlerDataEnabled();
    }

    private String contentNames(BackupTask task) {
        return String.join("、", names(task.isDatabaseEnabled(), task.isBooksEnabled(),
                task.isUploadsEnabled(), task.isCrawlerDataEnabled()));
    }

    private String contentNames(BackupTaskRequest request) {
        return String.join("、", names(request.databaseEnabled(), request.booksEnabled(),
                request.uploadsEnabled(), request.crawlerDataEnabled()));
    }

    private List<String> names(boolean database, boolean books, boolean uploads, boolean crawler) {
        List<String> names = new ArrayList<>();
        if (database) names.add("PostgreSQL 数据库");
        if (books) names.add("书籍文件");
        if (uploads) names.add("用户上传文件");
        if (crawler) names.add("采集数据文件");
        return names;
    }

    private record DatabaseAddress(String host, int port, String database) {}

    public record BackupPathView(String path, boolean exists, boolean writable) {}
}
