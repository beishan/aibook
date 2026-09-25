package com.aibook.service;

import com.aibook.dto.backup.BackupExecutionView;
import com.aibook.dto.backup.BackupRetentionSettings;
import com.aibook.dto.backup.BackupTaskRequest;
import com.aibook.dto.backup.BackupTaskView;
import com.aibook.model.entity.BackupExecution;
import com.aibook.model.entity.BackupTask;
import com.aibook.repository.BackupExecutionRepository;
import com.aibook.repository.BackupTaskRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern BACKUP_DIRECTORY_PATTERN =
            Pattern.compile("^aibook-(\\d{8})-(\\d{6})-(\\d+)$");
    private static final DateTimeFormatter DIRECTORY_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String RETENTION_ENABLED_KEY = "backup.retention.enabled";
    private static final String RETENTION_RECENT_DAYS_KEY = "backup.retention.recent-days";
    private static final String RETENTION_MONTHLY_MONTHS_KEY = "backup.retention.monthly-months";
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupTaskRepository taskRepository;
    private final BackupExecutionRepository executionRepository;
    private final SystemConfigService systemConfigService;
    @Qualifier("backupExecutor")
    private final Executor backupExecutor;

    public BackupService(
            BackupTaskRepository taskRepository,
            BackupExecutionRepository executionRepository,
            SystemConfigService systemConfigService,
            @Qualifier("backupExecutor") Executor backupExecutor) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.systemConfigService = systemConfigService;
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

    public BackupRetentionSettings retentionSettings() {
        return new BackupRetentionSettings(
                systemConfigService.getBooleanConfig(RETENTION_ENABLED_KEY, false),
                boundedConfig(RETENTION_RECENT_DAYS_KEY, 7, 1, 3650),
                boundedConfig(RETENTION_MONTHLY_MONTHS_KEY, 12, 0, 120));
    }

    @Transactional
    public BackupRetentionSettings updateRetentionSettings(BackupRetentionSettings request) {
        if (request == null) throw new IllegalArgumentException("请填写备份保留设置");
        if (request.recentDays() < 1 || request.recentDays() > 3650) {
            throw new IllegalArgumentException("最近备份保留天数必须在 1 到 3650 天之间");
        }
        if (request.monthlyMonths() < 0 || request.monthlyMonths() > 120) {
            throw new IllegalArgumentException("月度备份保留月数必须在 0 到 120 个月之间");
        }
        systemConfigService.saveConfigs(Map.of(
                RETENTION_ENABLED_KEY, Boolean.toString(request.enabled()),
                RETENTION_RECENT_DAYS_KEY, Integer.toString(request.recentDays()),
                RETENTION_MONTHLY_MONTHS_KEY, Integer.toString(request.monthlyMonths())));
        return request;
    }

    @Scheduled(cron = "${backup.retention-cron:0 15 4 * * *}")
    public void cleanExpiredBackups() {
        BackupRetentionSettings settings = retentionSettings();
        if (!settings.enabled()) return;

        Path root = Path.of(backupPath);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.isWritable(root)) {
            log.warn("跳过备份保留清理，目录不可用或不可写：{}", root);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recentCutoff = now.minusDays(settings.recentDays());
        YearMonth currentMonth = YearMonth.from(now);
        YearMonth earliestMonthlyMonth = settings.monthlyMonths() == 0
                ? null : currentMonth.minusMonths(settings.monthlyMonths() - 1L);
        List<BackupFolder> folders = findBackupFolders(root);
        Map<YearMonth, BackupFolder> latestSuccessfulByMonth = new HashMap<>();

        for (BackupFolder folder : folders) {
            if (!folder.successful()) continue;
            YearMonth month = YearMonth.from(folder.createdAt());
            if (earliestMonthlyMonth == null || month.isBefore(earliestMonthlyMonth)) continue;
            latestSuccessfulByMonth.merge(month, folder,
                    (existing, candidate) -> candidate.createdAt().isAfter(existing.createdAt())
                            ? candidate : existing);
        }

        for (BackupFolder folder : folders) {
            if (!folder.createdAt().isBefore(recentCutoff)
                    || folder.inProgress()
                    || isMonthlyRestorePoint(folder, earliestMonthlyMonth, latestSuccessfulByMonth)) {
                continue;
            }
            deleteBackupFolder(folder);
        }
    }

    private int boundedConfig(String key, int defaultValue, int minimum, int maximum) {
        int value = systemConfigService.getIntConfig(key, defaultValue);
        return Math.max(minimum, Math.min(maximum, value));
    }

    private List<BackupFolder> findBackupFolders(Path root) {
        List<BackupDirectory> directories = new ArrayList<>();
        try (var entries = Files.list(root)) {
            entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(path -> parseBackupDirectory(path).ifPresent(directories::add));
        } catch (IOException exception) {
            log.warn("读取备份目录失败 path={}", root, exception);
        }

        Map<Long, BackupExecution> executionsById = new HashMap<>();
        List<Long> executionIds = directories.stream().map(BackupDirectory::executionId).toList();
        executionRepository.findAllById(executionIds)
                .forEach(execution -> executionsById.put(execution.getId(), execution));

        List<BackupFolder> folders = new ArrayList<>(directories.size());
        for (BackupDirectory directory : directories) {
            BackupExecution execution = executionsById.get(directory.executionId());
            if (execution == null || !isRecordedBackupPath(directory.path(), execution)) continue;
            boolean inProgress = execution.getStatus() == BackupExecution.Status.QUEUED
                    || execution.getStatus() == BackupExecution.Status.RUNNING;
            boolean successful = execution.getStatus() == BackupExecution.Status.SUCCESS;
            folders.add(new BackupFolder(
                    directory.path(), directory.createdAt(), execution, successful, inProgress));
        }
        return folders;
    }

    private boolean isRecordedBackupPath(Path directory, BackupExecution execution) {
        if (execution.getOutputPath() == null || execution.getOutputPath().isBlank()) return false;
        Path expectedPath = Path.of(backupHostPath)
                .resolve(directory.getFileName())
                .normalize();
        return expectedPath.equals(Path.of(execution.getOutputPath()).normalize());
    }

    private java.util.Optional<BackupDirectory> parseBackupDirectory(Path path) {
        Matcher matcher = BACKUP_DIRECTORY_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) return java.util.Optional.empty();
        try {
            LocalDateTime createdAt = LocalDateTime.parse(
                    matcher.group(1) + "-" + matcher.group(2), DIRECTORY_STAMP);
            long executionId = Long.parseLong(matcher.group(3));
            return java.util.Optional.of(new BackupDirectory(path, createdAt, executionId));
        } catch (RuntimeException exception) {
            log.warn("跳过无法识别的备份目录：{}", path, exception);
            return java.util.Optional.empty();
        }
    }

    private boolean isMonthlyRestorePoint(
            BackupFolder folder,
            YearMonth earliestMonthlyMonth,
            Map<YearMonth, BackupFolder> latestSuccessfulByMonth) {
        if (!folder.successful() || earliestMonthlyMonth == null) return false;
        YearMonth month = YearMonth.from(folder.createdAt());
        if (month.isBefore(earliestMonthlyMonth)) return false;
        BackupFolder latest = latestSuccessfulByMonth.get(month);
        return latest != null && latest.path().equals(folder.path());
    }

    private void deleteBackupFolder(BackupFolder folder) {
        try {
            Files.walkFileTree(folder.path(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                        throws IOException {
                    if (exception != null) throw exception;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
            if (folder.execution() != null) {
                BackupExecution execution = folder.execution();
                execution.setOutputPath(null);
                String previousDetails = execution.getDetails() == null ? "" : execution.getDetails();
                execution.setDetails((previousDetails.isBlank() ? "" : previousDetails + "\n")
                        + "备份文件已按保留策略清理");
                executionRepository.save(execution);
            }
            log.info("已按保留策略清理备份目录：{}", folder.path());
        } catch (IOException | RuntimeException exception) {
            log.warn("清理备份目录失败 path={}", folder.path(), exception);
        }
    }

    private record BackupFolder(
            Path path,
            LocalDateTime createdAt,
            BackupExecution execution,
            boolean successful,
            boolean inProgress) {}

    private record BackupDirectory(Path path, LocalDateTime createdAt, long executionId) {}

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
        execution.setCurrentStage("排队中");
        execution.setProgressPercent(0);
        execution.setProgressDetail("等待备份执行器");
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
        execution.setCurrentStage("排队中");
        execution.setProgressPercent(0);
        execution.setProgressDetail("等待备份执行器");
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
        int totalStages = selectedContentCount(config);
        int completedStages = 0;
        try {
            updateExecutionProgress(executionId, BackupExecution.Status.RUNNING,
                    "准备备份目录", "正在检查备份目录并创建执行目录", 0, 0, 0);
            ensureBackupPath();
            String stamp = LocalDateTime.now().format(RUN_STAMP);
            output = Files.createDirectories(Path.of(backupPath))
                    .resolve("aibook-" + stamp + "-" + executionId);
            Files.createDirectory(output);
            if (config.databaseEnabled()) {
                updateExecutionProgress(executionId, BackupExecution.Status.RUNNING,
                        "导出 PostgreSQL 数据库",
                        "pg_dump 正在导出；数据库导出期间无法准确估算百分比",
                        overallProgress(completedStages, 0, totalStages), fileCount[0], totalBytes[0]);
                Path dump = output.resolve("database.dump");
                dumpDatabase(dump);
                long size = Files.size(dump);
                fileCount[0]++;
                totalBytes[0] += size;
                details.add("PostgreSQL 数据库：database.dump，" + formatBytes(size));
                completedStages++;
                updateExecutionProgress(executionId, BackupExecution.Status.RUNNING,
                        "数据库导出完成", "PostgreSQL 数据库导出完成",
                        overallProgress(completedStages, 0, totalStages), fileCount[0], totalBytes[0]);
            }
            if (config.booksEnabled()) {
                long beforeFiles = fileCount[0];
                long beforeBytes = totalBytes[0];
                copyDirectory(executionId, "复制书籍文件", Path.of(booksPath),
                        output.resolve("books"), completedStages, totalStages, fileCount, totalBytes);
                details.add("书籍文件：" + (fileCount[0] - beforeFiles) + " 个文件，"
                        + formatBytes(totalBytes[0] - beforeBytes));
                completedStages++;
            }
            if (config.uploadsEnabled()) {
                long beforeFiles = fileCount[0];
                long beforeBytes = totalBytes[0];
                copyDirectory(executionId, "复制用户上传文件", Path.of(uploadPath),
                        output.resolve("uploads"), completedStages, totalStages, fileCount, totalBytes);
                details.add("用户上传文件：" + (fileCount[0] - beforeFiles) + " 个文件，"
                        + formatBytes(totalBytes[0] - beforeBytes));
                completedStages++;
            }
            if (config.crawlerDataEnabled()) {
                long beforeFiles = fileCount[0];
                long beforeBytes = totalBytes[0];
                copyDirectory(executionId, "复制采集数据文件", Path.of(crawlerPath),
                        output.resolve("crawler-data"), completedStages, totalStages, fileCount, totalBytes);
                details.add("采集数据文件：" + (fileCount[0] - beforeFiles) + " 个文件，"
                        + formatBytes(totalBytes[0] - beforeBytes));
                completedStages++;
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

    private int selectedContentCount(BackupTaskRequest config) {
        int count = 0;
        if (config.databaseEnabled()) count++;
        if (config.booksEnabled()) count++;
        if (config.uploadsEnabled()) count++;
        if (config.crawlerDataEnabled()) count++;
        return count;
    }

    private int overallProgress(int completedStages, int currentStagePercent, int totalStages) {
        if (totalStages <= 0) return 0;
        double completed = completedStages + Math.max(0, Math.min(100, currentStagePercent)) / 100.0;
        return Math.min(99, (int) Math.floor(completed * 100 / totalStages));
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

    private void copyDirectory(
            long executionId,
            String stage,
            Path source,
            Path destination,
            int completedStages,
            int totalStages,
            long[] fileCount,
            long[] totalBytes) throws IOException {
        if (!Files.isDirectory(source) || !Files.isReadable(source)) {
            throw new IOException("源目录不存在或不可读：" + source);
        }

        ProgressReporter reporter = new ProgressReporter(
                executionId, stage, completedStages, totalStages, fileCount, totalBytes);
        DirectoryStats stats = scanDirectory(source, reporter);
        Files.createDirectories(destination);
        long[] copiedFileCount = {0};
        long[] copiedBytes = {0};
        reporter.update(0, "扫描完成，共 " + stats.fileCount() + " 个文件，开始复制", true);

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
                try (InputStream input = Files.newInputStream(file);
                        OutputStream output = Files.newOutputStream(target,
                                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[1024 * 1024];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                        copiedBytes[0] += bytesRead;
                        totalBytes[0] += bytesRead;
                        reporter.update(copyProgress(stats, copiedFileCount[0], copiedBytes[0]),
                                copyProgressDetail(stats, copiedFileCount[0], copiedBytes[0]), false);
                    }
                }
                fileCount[0]++;
                copiedFileCount[0]++;
                reporter.update(copyProgress(stats, copiedFileCount[0], copiedBytes[0]),
                        copyProgressDetail(stats, copiedFileCount[0], copiedBytes[0]), false);
                return FileVisitResult.CONTINUE;
            }
        });
        reporter.update(100, "复制完成，共 " + copiedFileCount[0] + " 个文件", true);
    }

    private DirectoryStats scanDirectory(Path source, ProgressReporter reporter) throws IOException {
        long[] fileCount = {0};
        long[] totalBytes = {0};
        long[] lastReportAt = {System.currentTimeMillis()};
        reporter.update(0, "正在扫描源目录以估算复制进度", true);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    fileCount[0]++;
                    totalBytes[0] += attributes.size();
                    long now = System.currentTimeMillis();
                    if (now - lastReportAt[0] >= 1000) {
                        reporter.update(0, "扫描中，已发现 " + fileCount[0] + " 个文件", true);
                        lastReportAt[0] = now;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new DirectoryStats(fileCount[0], totalBytes[0]);
    }

    private int copyProgress(DirectoryStats stats, long copiedFiles, long copiedBytes) {
        if (stats.totalBytes() > 0) {
            return (int) Math.min(100, copiedBytes * 100 / stats.totalBytes());
        }
        if (stats.fileCount() > 0) {
            return (int) Math.min(100, copiedFiles * 100 / stats.fileCount());
        }
        return 100;
    }

    private String copyProgressDetail(DirectoryStats stats, long copiedFiles, long copiedBytes) {
        return "已复制 " + copiedFiles + "/" + stats.fileCount() + " 个文件，"
                + formatBytes(copiedBytes) + " / " + formatBytes(stats.totalBytes());
    }

    private String formatBytes(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = Math.max(0, bytes);
        int unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        String formattedSize = size >= 10 || unitIndex == 0
                ? String.format(java.util.Locale.ROOT, "%.0f", size)
                : String.format(java.util.Locale.ROOT, "%.1f", size);
        return formattedSize + " " + units[unitIndex];
    }

    private final class ProgressReporter {
        private final long executionId;
        private final String stage;
        private final int completedStages;
        private final int totalStages;
        private final long[] fileCount;
        private final long[] totalBytes;
        private int lastPercent = -1;
        private long lastUpdateAt;

        private ProgressReporter(
                long executionId,
                String stage,
                int completedStages,
                int totalStages,
                long[] fileCount,
                long[] totalBytes) {
            this.executionId = executionId;
            this.stage = stage;
            this.completedStages = completedStages;
            this.totalStages = totalStages;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
        }

        private void update(int stagePercent, String detail, boolean force) {
            int percent = overallProgress(completedStages, stagePercent, totalStages);
            long now = System.currentTimeMillis();
            if (!force && percent == lastPercent && now - lastUpdateAt < 1000) return;
            updateExecutionProgress(executionId, BackupExecution.Status.RUNNING,
                    stage, detail, percent, fileCount[0], totalBytes[0]);
            lastPercent = percent;
            lastUpdateAt = now;
        }
    }

    private record DirectoryStats(long fileCount, long totalBytes) {}

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
            String stage = execution.getCurrentStage();
            execution.setCurrentStage(status == BackupExecution.Status.SUCCESS
                    ? "备份完成"
                    : stage == null ? "备份失败" : "失败于：" + stage);
            execution.setProgressDetail(status == BackupExecution.Status.SUCCESS
                    ? "所有备份内容均已完成"
                    : (error == null ? "备份未能完成" : error));
            if (status == BackupExecution.Status.SUCCESS) execution.setProgressPercent(100);
        }
        executionRepository.save(execution);
    }

    private void updateExecutionProgress(
            long id,
            BackupExecution.Status status,
            String stage,
            String detail,
            int percent,
            long fileCount,
            long totalBytes) {
        BackupExecution execution = executionRepository.findById(id).orElse(null);
        if (execution == null) return;
        execution.setStatus(status);
        execution.setCurrentStage(stage);
        execution.setProgressDetail(detail);
        execution.setProgressPercent(Math.max(0, Math.min(100, percent)));
        execution.setFileCount(fileCount);
        execution.setFileSizeBytes(totalBytes);
        if (status == BackupExecution.Status.RUNNING && execution.getStartedAt() == null) {
            execution.setStartedAt(LocalDateTime.now());
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
