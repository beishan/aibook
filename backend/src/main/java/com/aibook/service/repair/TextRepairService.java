package com.aibook.service.repair;

import com.aibook.dto.*;
import com.aibook.model.entity.*;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.TextRepairIssueRepository;
import com.aibook.repository.TextRepairRuleRepository;
import com.aibook.repository.TextRepairTaskRepository;
import com.aibook.repository.TextRepairTemplateRepository;
import com.aibook.service.BookService;
import com.aibook.service.BookVersionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * TXT 内容修复任务编排服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextRepairService {

    private final TextRepairTaskRepository taskRepository;
    private final TextRepairIssueRepository issueRepository;
    private final TextRepairRuleRepository ruleRepository;
    private final TextRepairTemplateRepository templateRepository;
    private final BookVersionRepository bookVersionRepository;
    private final BookService bookService;
    private final BookVersionService bookVersionService;
    private final ObjectMapper objectMapper;

    private final EncodingDetectService encodingDetectService;
    private final AdDetectService adDetectService;
    private final ChapterDetectService chapterDetectService;
    private final ChapterNormalizeService chapterNormalizeService;
    private final ParagraphFixService paragraphFixService;
    private final PunctuationFixService punctuationFixService;
    private final DuplicateDetectService duplicateDetectService;

    // ==================== 任务管理 ====================

    /**
     * 创建修复任务并扫描内容
     */
    @Transactional
    public RepairTaskDTO createTask(CreateRepairTaskRequest request, Long userId) {
        Book book = getOwnedBook(request.getBookId(), userId);

        // 确定书籍版本
        BookVersion version;
        if (request.getVersionId() != null) {
            version = bookVersionService.resolveVersion(book, request.getVersionId());
        } else {
            version = bookVersionService.ensurePrimaryVersion(book);
        }

        // 只支持 TXT/MD 格式
        String format = version.getFormat().toLowerCase();
        if (!"txt".equals(format) && !"md".equals(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "内容修复仅支持 TXT/MD 格式书籍");
        }

        // 创建任务
        TextRepairTask task = TextRepairTask.builder()
                .bookId(book.getId())
                .versionId(version.getId())
                .templateId(request.getTemplateId())
                .repairMode(request.getRepairMode())
                .status("SCANNING")
                .originalContentVersion(version.getFileHash())
                .userId(userId)
                .build();

        // 应用模板配置
        if (request.getTemplateId() != null) {
            TextRepairTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "修复模板不存在"));
            if (template.getUserId() != null && !template.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权使用此修复模板");
            }
            task.setOptionsJson(buildOptionsFromTemplate(template));
        } else if (request.getOptionsJson() != null) {
            task.setOptionsJson(request.getOptionsJson());
        } else {
            task.setOptionsJson(buildDefaultOptions(request.getRepairMode()));
        }

        String originalText = readBookContent(version, extractStringOption(
                task.getOptionsJson(), "preferredEncoding", "AUTO"));

        task = taskRepository.save(task);

        // 扫描内容
        try {
            scanContent(task, book, version, originalText);
            task.setStatus("SCANNED");
            task = taskRepository.save(task);
        } catch (Exception e) {
            log.error("扫描内容失败", e);
            task.setStatus("FAILED");
            task = taskRepository.save(task);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "扫描内容失败: " + e.getMessage(), e);
        }

        return toTaskDTO(task, book);
    }

    /**
     * 获取任务详情
     */
    @Transactional
    public RepairTaskDTO getTask(Long taskId, Long userId) {
        TextRepairTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修复任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问此任务");
        }
        Book book = getOwnedBook(task.getBookId(), userId);
        return toTaskDTO(task, book);
    }

    /**
     * 获取用户的修复任务列表
     */
    @Transactional
    public org.springframework.data.domain.Page<RepairTaskDTO> getTasks(
            Long userId, org.springframework.data.domain.Pageable pageable) {
        User user = User.builder().id(userId).build();
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(task -> {
                    Book book = bookService.getBookEntity(task.getBookId(), user);
                    return toTaskDTO(task, book);
                });
    }

    /**
     * 获取已经完成扫描、可继续处理的检测记录。
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<RepairTaskDTO> getDetectionRecords(
            Long userId, org.springframework.data.domain.Pageable pageable) {
        User user = User.builder().id(userId).build();
        return taskRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
                        userId, List.of("SCANNED", "COMPLETED"), pageable)
                .map(task -> {
                    Book book = bookService.getBookEntity(task.getBookId(), user);
                    return toTaskDTO(task, book);
                });
    }

    /**
     * 使用原检测记录的书籍、版本和配置重新扫描。旧记录会保留，便于追溯。
     */
    @Transactional
    public RepairTaskDTO rescanTask(Long taskId, Long userId) {
        TextRepairTask sourceTask = getOwnedTask(taskId, userId);
        CreateRepairTaskRequest request = new CreateRepairTaskRequest();
        request.setBookId(sourceTask.getBookId());
        request.setVersionId(sourceTask.getVersionId());
        request.setRepairMode(sourceTask.getRepairMode());
        request.setTemplateId(sourceTask.getTemplateId());
        request.setOptionsJson(sourceTask.getOptionsJson());
        return createTask(request, userId);
    }

    /**
     * 获取书籍的修复任务列表
     */
    @Transactional
    public List<RepairTaskDTO> getTasksByBookId(Long bookId, Long userId) {
        User user = User.builder().id(userId).build();
        return taskRepository.findByBookIdOrderByCreatedAtDesc(bookId).stream()
                .filter(task -> task.getUserId().equals(userId))
                .map(task -> {
                    Book book = bookService.getBookEntity(bookId, user);
                    return toTaskDTO(task, book);
                })
                .toList();
    }

    /**
     * 删除修复任务
     */
    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        TextRepairTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修复任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除此任务");
        }
        issueRepository.deleteAll(
                issueRepository.findByTaskIdOrderByChapterIndexAscStartOffsetAsc(taskId));
        taskRepository.delete(task);
    }

    // ==================== 问题管理 ====================

    /**
     * 获取任务的所有问题（分页）
     */
    @Transactional
    public org.springframework.data.domain.Page<RepairIssueDTO> getIssues(
            Long taskId, Long userId, RepairIssueType type, RepairIssueStatus status,
            org.springframework.data.domain.Pageable pageable) {
        getOwnedTask(taskId, userId);
        if (type != null && status != null) {
            // 同时过滤类型和状态
            List<TextRepairIssue> allIssues = issueRepository
                    .findByTaskIdOrderByChapterIndexAscStartOffsetAsc(taskId);
            List<TextRepairIssue> filtered = allIssues.stream()
                    .filter(i -> i.getType() == type && i.getStatus() == status)
                    .toList();
            return toIssueDTOPage(filtered, pageable);
        } else if (type != null) {
            List<TextRepairIssue> issues = issueRepository.findByTaskIdAndType(taskId, type);
            return toIssueDTOPage(issues, pageable);
        } else if (status != null) {
            List<TextRepairIssue> issues = issueRepository.findByTaskIdAndStatus(taskId, status);
            return toIssueDTOPage(issues, pageable);
        } else {
            return issueRepository
                    .findByTaskIdOrderByChapterIndexAscStartOffsetAsc(taskId, pageable)
                    .map(this::toIssueDTO);
        }
    }

    /**
     * 更新单个问题状态
     */
    @Transactional
    public RepairIssueDTO updateIssue(Long issueId, UpdateIssueRequest request, Long userId) {
        TextRepairIssue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修复问题不存在"));
        TextRepairTask task = taskRepository.findById(issue.getTaskId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修复任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作");
        }

        issue.setStatus(request.getStatus());
        if (request.getSource() != null) {
            issue.setSource(request.getSource());
        }
        if (request.getManualText() != null) {
            issue.setSuggestedText(request.getManualText());
            issue.setSource(RepairSource.MANUAL);
        }

        issue = issueRepository.save(issue);

        // 批量应用
        if (Boolean.TRUE.equals(request.getApplyToAll())) {
            String targetRuleId = issue.getRuleId();
            String targetOriginalText = issue.getOriginalText();
            List<TextRepairIssue> similarIssues = issueRepository
                    .findByTaskIdAndType(task.getId(), issue.getType()).stream()
                    .filter(i -> i.getStatus() == RepairIssueStatus.PENDING)
                    .filter(i -> targetRuleId != null
                            ? targetRuleId.equals(i.getRuleId())
                            : Objects.equals(targetOriginalText, i.getOriginalText()))
                    .toList();
            for (TextRepairIssue similar : similarIssues) {
                similar.setStatus(request.getStatus());
                if (request.getManualText() != null) {
                    similar.setSuggestedText(request.getManualText());
                    similar.setSource(RepairSource.MANUAL);
                }
                if (request.getSource() != null) {
                    similar.setSource(request.getSource());
                }
            }
            issueRepository.saveAll(similarIssues);
        }

        updateTaskCounts(task.getId());
        return toIssueDTO(issue);
    }

    /**
     * 批量更新问题状态
     */
    @Transactional
    public void batchUpdateIssues(Long taskId, BatchUpdateIssuesRequest request, Long userId) {
        getOwnedTask(taskId, userId);
        List<TextRepairIssue> issues = issueRepository.findAllById(request.getIssueIds());
        if (issues.size() != request.getIssueIds().size()
                || issues.stream().anyMatch(issue -> !issue.getTaskId().equals(taskId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "批量操作包含不属于当前任务的问题");
        }
        for (TextRepairIssue issue : issues) {
            issue.setStatus(request.getStatus());
            issue.setSource(RepairSource.BATCH);
        }
        issueRepository.saveAll(issues);
        updateTaskCounts(taskId);
    }

    /**
     * 批量接受高置信度问题
     */
    @Transactional
    public int acceptHighConfidenceIssues(Long taskId, double threshold, Long userId) {
        getOwnedTask(taskId, userId);
        if (threshold < 0.0 || threshold > 1.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "置信度阈值必须在 0 到 1 之间");
        }
        List<TextRepairIssue> issues = issueRepository
                .findByTaskIdAndStatus(taskId, RepairIssueStatus.PENDING);
        int count = 0;
        for (TextRepairIssue issue : issues) {
            if (issue.getConfidence() != null && issue.getConfidence() >= threshold
                    && issue.getRiskLevel() != RiskLevel.HIGH
                    && issue.getSuggestedText() != null) {
                issue.setStatus(RepairIssueStatus.ACCEPTED);
                issue.setSource(RepairSource.BATCH);
                count++;
            }
        }
        issueRepository.saveAll(issues);
        updateTaskCounts(taskId);
        return count;
    }

    /**
     * 撤销全部修改
     */
    @Transactional
    public void revertAllIssues(Long taskId, Long userId) {
        TextRepairTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修复任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作");
        }
        List<TextRepairIssue> issues = issueRepository
                .findByTaskIdOrderByChapterIndexAscStartOffsetAsc(taskId);
        for (TextRepairIssue issue : issues) {
            if (issue.getStatus() != RepairIssueStatus.PENDING) {
                issue.setStatus(RepairIssueStatus.PENDING);
            }
        }
        issueRepository.saveAll(issues);
        updateTaskCounts(taskId);
    }

    /**
     * Restore a completed repair task by removing only the version created by it.
     * The primary/original version is never modified by the repair pipeline.
     */
    @Transactional
    public void restoreOriginalVersion(Long taskId, Long userId) {
        TextRepairTask task = getOwnedTask(taskId, userId);
        if (!"COMPLETED".equals(task.getStatus()) || task.getRepairedContentVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务没有可恢复的修复版本");
        }
        Book book = getOwnedBook(task.getBookId(), userId);
        BookVersion repairedVersion = bookVersionRepository
                .findByFileHash(task.getRepairedContentVersion())
                .filter(version -> version.getBook().getId().equals(book.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "修复版本不存在或已经恢复"));
        if (Boolean.TRUE.equals(repairedVersion.getPrimaryVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "修复版本已被设为主版本，不能自动删除");
        }

        Path repairedPath = Paths.get(repairedVersion.getFilePath());
        bookVersionService.deleteVersion(book, repairedVersion.getId());
        try {
            Files.deleteIfExists(repairedPath);
        } catch (IOException e) {
            log.warn("修复版本记录已删除，但文件清理失败: {}", repairedPath, e);
        }

        List<TextRepairIssue> issues = issueRepository
                .findByTaskIdOrderByChapterIndexAscStartOffsetAsc(taskId);
        for (TextRepairIssue issue : issues) {
            if (issue.getStatus() == RepairIssueStatus.APPLIED) {
                issue.setStatus(RepairIssueStatus.REVERTED);
            }
        }
        issueRepository.saveAll(issues);
        task.setStatus("REVERTED");
        task.setRepairedContentVersion(null);
        task.setReportJson(null);
        taskRepository.save(task);
        updateTaskCounts(taskId);
    }

    // ==================== 编码切换预览 ====================

    /**
     * 检测编码
     */
    @Transactional
    public EncodingDetectResult detectEncoding(Long bookId, Long versionId, Long userId) {
        Book book = getOwnedBook(bookId, userId);
        BookVersion version = bookVersionService.resolveVersion(book, versionId);
        try {
            Path file = Paths.get(version.getFilePath());
            return encodingDetectService.detectEncoding(file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "编码检测失败: " + e.getMessage(), e);
        }
    }

    /**
     * 切换编码预览
     */
    @Transactional
    public String switchEncodingPreview(Long bookId, Long versionId,
                                         String encoding, Long userId) {
        Book book = getOwnedBook(bookId, userId);
        BookVersion version = bookVersionService.resolveVersion(book, versionId);
        try {
            Path file = Paths.get(version.getFilePath());
            String fullText = encodingDetectService.decodeWithEncoding(file, encoding);
            // 返回前 2000 字预览
            return fullText != null && fullText.length() > 2000
                    ? fullText.substring(0, 2000) : fullText;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "编码切换失败: " + e.getMessage(), e);
        }
    }

    // ==================== 修复预览 ====================

    /**
     * 生成修复预览（原文 vs 修复后对照）
     */
    @Transactional
    public RepairPreviewResponse previewRepair(Long taskId, Long userId) {
        TextRepairTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修复任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作");
        }

        Book book = getOwnedBook(task.getBookId(), userId);
        BookVersion version = bookVersionService.resolveVersion(book, task.getVersionId());
        String originalText = readBookContent(version, extractStringOption(
                task.getOptionsJson(), "preferredEncoding", "AUTO"));

        // 获取已接受的问题
        List<TextRepairIssue> acceptedIssues = issueRepository
                .findByTaskIdAndStatus(taskId, RepairIssueStatus.ACCEPTED);

        // 生成修复后文本
        String repairedText = applyIssues(originalText, acceptedIssues, task);

        // 生成差异行
        List<RepairPreviewResponse.DiffLine> diffLines = generateDiffLines(
                originalText, repairedText, acceptedIssues);

        return RepairPreviewResponse.builder()
                .originalText(originalText.length() > 5000
                        ? originalText.substring(0, 5000) : originalText)
                .repairedText(repairedText.length() > 5000
                        ? repairedText.substring(0, 5000) : repairedText)
                .diffLines(diffLines)
                .issueIds(acceptedIssues.stream().map(TextRepairIssue::getId).toList())
                .build();
    }

    /**
     * 执行修复，保存为新版本
     */
    @Transactional
    public BookVersionDTO applyRepair(ApplyRepairRequest request, Long userId) {
        TextRepairTask task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修复任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作");
        }

        task.setStatus("REPAIRING");
        taskRepository.save(task);

        try {
            Book book = getOwnedBook(task.getBookId(), userId);
            BookVersion version = bookVersionService.resolveVersion(book, task.getVersionId());
            String originalText = readBookContent(version, extractStringOption(
                    task.getOptionsJson(), "preferredEncoding", "AUTO"));

            // 获取要应用的问题
            List<TextRepairIssue> issuesToApply;
            // Pending issues, especially low-confidence and high-risk suggestions, are
            // never applied implicitly.
            issuesToApply = issueRepository
                    .findByTaskIdAndStatus(task.getId(), RepairIssueStatus.ACCEPTED);

            // 生成修复后文本
            String repairedText = applyIssues(originalText, issuesToApply, task);

            // 保存为新的 TXT 文件
            Path repairedFile = saveRepairedFile(book, repairedText);

            // 创建新版本
            String hash = calculateHash(repairedFile);
            BookVersion newVersion = BookVersion.builder()
                    .book(book)
                    .displayName("修复版本_" + java.time.LocalDateTime.now())
                    .format("txt")
                    .filePath(repairedFile.toString())
                    .fileSize(Files.size(repairedFile))
                    .fileHash(hash)
                    .primaryVersion(false)
                    .chapterInfo(null)
                    .chapterCount(null)
                    .build();
            newVersion = bookVersionRepository.save(newVersion);

            // 更新问题状态为已应用
            for (TextRepairIssue issue : issuesToApply) {
                issue.setStatus(RepairIssueStatus.APPLIED);
            }
            issueRepository.saveAll(issuesToApply);

            // Persist result and refresh counters before generating the report.
            task.setRepairedContentVersion(hash);
            task.setStatus("COMPLETED");
            taskRepository.save(task);
            updateTaskCounts(task.getId());
            task = taskRepository.findById(task.getId()).orElseThrow();
            task.setReportJson(generateReport(task, issuesToApply));
            taskRepository.save(task);

            return bookVersionService.toDTO(newVersion);
        } catch (Exception e) {
            log.error("执行修复失败", e);
            task.setStatus("FAILED");
            taskRepository.save(task);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "修复失败: " + e.getMessage(), e);
        }
    }

    // ==================== 扫描内容 ====================

    private void scanContent(TextRepairTask task, Book book,
                              BookVersion version, String text) {
        List<TextRepairIssue> allIssues = new ArrayList<>();
        String[] lines = text.split("\n", -1);

        // 1. 编码检测
        if (extractBooleanOption(task.getOptionsJson(), "encodingRepair", true)) {
            List<TextRepairIssue> encodingIssues = encodingDetectService
                    .scanForIssues(text, task.getId());
            if ("IGNORE".equals(extractStringOption(task.getOptionsJson(),
                    "unrecoverableEncodingAction", "MARK"))) {
                encodingIssues = encodingIssues.stream()
                        .filter(issue -> issue.getRiskLevel() != RiskLevel.HIGH)
                        .toList();
            }
            allIssues.addAll(encodingIssues);
        }

        // 2. 不可见字符及可选标点清理
        List<TextRepairIssue> punctIssues = punctuationFixService
                .scanForIssues(text, lines, task.getId());
        boolean punctuationEnabled = extractBooleanOption(
                task.getOptionsJson(), "punctuationNormalize", false);
        boolean invisibleCharCleanup = extractBooleanOption(
                task.getOptionsJson(), "invisibleCharCleanup", true);
        allIssues.addAll(punctIssues.stream()
                .filter(i -> (i.getType() == RepairIssueType.INVISIBLE_CHAR
                                && invisibleCharCleanup)
                        || (task.getRepairMode() != RepairMode.SAFE && punctuationEnabled))
                .toList());

        // 3. 段落格式
        List<TextRepairIssue> paragraphIssues = paragraphFixService
                .scanForIssues(text, lines, task.getId());
        boolean lineEndingNormalize = extractBooleanOption(
                task.getOptionsJson(), "lineEndingNormalize", true);
        boolean blankLineCleanup = extractBooleanOption(
                task.getOptionsJson(), "blankLineCleanup", true);
        boolean brokenLineMerge = extractBooleanOption(
                task.getOptionsJson(), "brokenLineMerge",
                task.getRepairMode() != RepairMode.SAFE);
        boolean indentNormalize = extractBooleanOption(
                task.getOptionsJson(), "indentNormalize",
                task.getRepairMode() != RepairMode.SAFE)
                && !"KEEP".equals(extractStringOption(
                        task.getOptionsJson(), "indentStyle", "FULL_WIDTH_SPACE"));
        int configuredBlankLines = Math.max(0, extractIntOption(
                task.getOptionsJson(), "blankLineCount", 1));
        paragraphIssues.stream()
                .filter(issue -> Objects.toString(issue.getReason(), "")
                        .contains("多余空行"))
                .forEach(issue -> issue.setSuggestedText(
                        "\n".repeat(configuredBlankLines)));
        allIssues.addAll(paragraphIssues.stream()
                .filter(i -> paragraphOptionEnabled(i, lineEndingNormalize,
                        blankLineCleanup, brokenLineMerge, indentNormalize))
                .toList());

        // 4. 章节识别
        List<DetectedChapterDTO> chapters = chapterDetectService.detectChapters(text);
        task.setDetectedChapterCount(chapters.size());
        int minChapterWords = extractIntOption(task.getOptionsJson(), "minChapterWords", 100);
        int maxChapterWords = extractIntOption(task.getOptionsJson(), "maxChapterWords", 30000);
        boolean chapterDetection = extractBooleanOption(
                task.getOptionsJson(), "chapterDetection", true);
        boolean chapterNumberCheck = extractBooleanOption(
                task.getOptionsJson(), "chapterNumberCheck",
                task.getRepairMode() != RepairMode.SAFE);
        boolean chapterAdhesionDetection = extractBooleanOption(
                task.getOptionsJson(), "chapterAdhesionDetection",
                task.getRepairMode() == RepairMode.DEEP);
        if (chapterDetection) {
            allIssues.addAll(chapterDetectService.scanForIssues(
                    chapters, task.getId(), minChapterWords, maxChapterWords,
                    chapterNumberCheck, chapterAdhesionDetection));
        }

        // 5. 章节标题规范化（标准及以上模式）
        if (chapterDetection && extractBooleanOption(
                task.getOptionsJson(), "chapterNormalize",
                task.getRepairMode() != RepairMode.SAFE)) {
            String chapterFormat = extractChapterFormat(task.getOptionsJson());
            List<TextRepairIssue> normalizeIssues = chapterNormalizeService
                    .scanForIssues(chapters, chapterFormat, task.getId());
            allIssues.addAll(normalizeIssues);
        }

        // 6. 广告检测
        if (extractBooleanOption(task.getOptionsJson(), "adDetection", true)) {
            List<TextRepairRule> adRules = ruleRepository.findEnabledRules(task.getUserId())
                    .stream()
                    .filter(rule -> ruleAppliesToTask(rule, task))
                    .toList();
            List<TextRepairIssue> adIssues = adDetectService.scanForIssues(
                    text, lines, convertChapters(chapters), task.getId(), adRules);
            if (task.getRepairMode() == RepairMode.SAFE) {
                allIssues.addAll(adIssues.stream()
                        .filter(i -> i.getRiskLevel() == RiskLevel.LOW
                                && i.getConfidence() != null && i.getConfidence() >= 0.7)
                        .toList());
            } else {
                allIssues.addAll(adIssues);
            }
        }

        // 7. 重复内容检测
        boolean duplicateChapterDetection = extractBooleanOption(
                task.getOptionsJson(), "duplicateChapterDetection",
                task.getRepairMode() != RepairMode.SAFE);
        boolean similarChapterDetection = extractBooleanOption(
                task.getOptionsJson(), "similarChapterDetection",
                task.getRepairMode() == RepairMode.DEEP);
        boolean duplicateParagraphDetection = extractBooleanOption(
                task.getOptionsJson(), "duplicateParagraphDetection",
                task.getRepairMode() == RepairMode.DEEP);
        if (duplicateChapterDetection || similarChapterDetection
                || duplicateParagraphDetection) {
            List<TextRepairIssue> dupIssues = duplicateDetectService
                    .scanForIssues(text, chapters, task.getId());
            allIssues.addAll(dupIssues.stream()
                    .filter(i -> duplicateOptionEnabled(i,
                            duplicateChapterDetection, similarChapterDetection,
                            duplicateParagraphDetection))
                    .toList());
        }

        // 保存所有问题
        issueRepository.saveAll(allIssues);

        // 更新任务计数
        task.setTotalIssueCount(allIssues.size());
        task.setPendingIssueCount(allIssues.size());
        taskRepository.save(task);
    }

    // ==================== 修复执行 ====================

    private String applyIssues(String text, List<TextRepairIssue> issues,
                                TextRepairTask task) {
        String result = text;

        // Apply concrete replacements from the end of the original text. Larger ranges
        // win when two accepted suggestions overlap (for example deleting a duplicate
        // chapter and normalizing its title).
        List<TextRepairIssue> sortedIssues = issues.stream()
                .filter(i -> i.getStartOffset() != null && i.getEndOffset() != null)
                .sorted(Comparator.comparingInt(
                                (TextRepairIssue i) -> -i.getStartOffset())
                        .thenComparingInt(i -> -(i.getEndOffset() - i.getStartOffset())))
                .toList();

        int nextAppliedStart = text.length() + 1;
        for (TextRepairIssue issue : sortedIssues) {
            if (issue.getEndOffset() > nextAppliedStart) {
                log.warn("跳过重叠修复项: issueId={}, range=[{}, {})",
                        issue.getId(), issue.getStartOffset(), issue.getEndOffset());
                continue;
            }
            result = applySingleIssue(result, issue);
            nextAppliedStart = issue.getStartOffset();
        }

        // Whole-document cleanup issues intentionally have no offsets. Run them after
        // positional edits so they cannot invalidate the original offsets.
        for (TextRepairIssue issue : issues) {
            if (issue.getStartOffset() == null || issue.getEndOffset() == null) {
                result = applyWholeDocumentIssue(result, issue, task);
            }
        }

        return result;
    }

    private String applyWholeDocumentIssue(String text, TextRepairIssue issue,
                                           TextRepairTask task) {
        String reason = issue.getReason() == null ? "" : issue.getReason();
        if (issue.getType() == RepairIssueType.INVISIBLE_CHAR) {
            return punctuationFixService.cleanInvisibleChars(text);
        }
        if (issue.getType() == RepairIssueType.PUNCTUATION) {
            if (reason.contains("行尾")) return punctuationFixService.trimTrailingSpaces(text);
            if (reason.contains("标点前")) return punctuationFixService.removeSpaceBeforePunct(text);
            if (reason.contains("英文标点")) return punctuationFixService.normalizePunctuation(text);
            if (reason.contains("重复标点")) return punctuationFixService.cleanRepeatedPunctuation(text);
        }
        if (issue.getType() == RepairIssueType.PARAGRAPH) {
            if (reason.contains("换行符")) return paragraphFixService.normalizeLineEndings(text);
            if (reason.contains("段首缩进")) {
                return paragraphFixService.normalizeIndent(text,
                        extractStringOption(task.getOptionsJson(), "indentStyle", "FULL_WIDTH_SPACE"));
            }
        }
        if (issue.getType() == RepairIssueType.ENCODING
                && issue.getSuggestedText() != null) {
            Map<String, Object> metadata = parseIssueMetadata(issue.getMetadataJson());
            Object garbledPattern = metadata == null ? null : metadata.get("garbledPattern");
            if (garbledPattern instanceof String pattern && !pattern.isEmpty()) {
                return text.replace(pattern, issue.getSuggestedText());
            }
        }
        // Remaining anomaly and chapter warnings without concrete replacements are
        // advisory only and must never mutate the source text.
        return text;
    }

    private String applySingleIssue(String text, TextRepairIssue issue) {
        if (issue.getStartOffset() == null || issue.getEndOffset() == null) {
            return text;
        }
        int start = Math.min(issue.getStartOffset(), text.length());
        int end = Math.min(issue.getEndOffset(), text.length());
        if (start > end) return text;

        String suggested = issue.getSuggestedText();
        if (suggested == null) return text;

        if ("[已删除]".equals(suggested) || "[已清理]".equals(suggested)) {
            // 删除匹配内容
            return text.substring(0, start) + text.substring(end);
        } else {
            // 替换内容
            return text.substring(0, start) + suggested + text.substring(end);
        }
    }

    // ==================== 工具方法 ====================

    private String readBookContent(BookVersion version) {
        return readBookContent(version, "AUTO");
    }

    private String readBookContent(BookVersion version, String encoding) {
        try {
            Path file = Paths.get(version.getFilePath());
            if (!Files.isRegularFile(file)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍文件不存在");
            }
            byte[] bytes = Files.readAllBytes(file);
            return encodingDetectService.decodeWithEncoding(bytes, encoding);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "读取书籍内容失败: " + e.getMessage(), e);
        }
    }

    private Book getOwnedBook(Long bookId, Long userId) {
        User user = User.builder().id(userId).build();
        return bookService.getBookEntity(bookId, user);
    }

    private Path saveRepairedFile(Book book, String content) throws IOException {
        String uploadPath = System.getProperty("upload.path", "./uploads");
        Path dir = Paths.get(uploadPath, "repaired");
        Files.createDirectories(dir);
        String fileName = "repaired_" + book.getId() + "_" + System.currentTimeMillis() + ".txt";
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private String calculateHash(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private String buildDefaultOptions(RepairMode mode) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("mode", mode.name());
        options.put("chapterFormat", ChapterNormalizeService.DEFAULT_FORMAT);
        options.put("indentStyle", "FULL_WIDTH_SPACE");
        options.put("blankLineCount", 1);
        options.put("punctuationNormalize", mode != RepairMode.SAFE);
        options.put("traditionalSimplified", "NONE");
        options.put("minChapterWords", 100);
        options.put("maxChapterWords", 30000);
        options.put("autoApplyThreshold", 0.8);
        options.put("encodingRepair", true);
        options.put("preferredEncoding", "AUTO");
        options.put("unrecoverableEncodingAction", "MARK");
        options.put("invisibleCharCleanup", true);
        options.put("adDetection", true);
        options.put("chapterDetection", true);
        options.put("chapterNormalize", mode != RepairMode.SAFE);
        options.put("chapterNumberCheck", mode != RepairMode.SAFE);
        options.put("chapterAdhesionDetection", mode == RepairMode.DEEP);
        options.put("lineEndingNormalize", true);
        options.put("blankLineCleanup", true);
        options.put("brokenLineMerge", mode != RepairMode.SAFE);
        options.put("indentNormalize", mode != RepairMode.SAFE);
        options.put("duplicateChapterDetection", mode != RepairMode.SAFE);
        options.put("similarChapterDetection", mode == RepairMode.DEEP);
        options.put("duplicateParagraphDetection", mode == RepairMode.DEEP);
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildOptionsFromTemplate(TextRepairTemplate template) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("mode", template.getRepairMode().name());
        options.put("chapterFormat", template.getChapterFormat());
        options.put("indentStyle", template.getIndentStyle());
        options.put("blankLineCount", template.getBlankLineCount());
        options.put("punctuationNormalize", template.getPunctuationNormalize());
        options.put("traditionalSimplified", template.getTraditionalSimplified());
        options.put("minChapterWords", template.getMinChapterWords());
        options.put("maxChapterWords", template.getMaxChapterWords());
        options.put("autoApplyThreshold", template.getAutoApplyThreshold());
        mergeJsonOptions(options, template.getEnabledItemsJson());
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractChapterFormat(String optionsJson) {
        if (optionsJson == null || optionsJson.isEmpty()) {
            return ChapterNormalizeService.DEFAULT_FORMAT;
        }
        try {
            Map<String, Object> options = objectMapper.readValue(optionsJson,
                    new TypeReference<Map<String, Object>>() {});
            Object format = options.get("chapterFormat");
            return format != null ? format.toString() : ChapterNormalizeService.DEFAULT_FORMAT;
        } catch (Exception e) {
            return ChapterNormalizeService.DEFAULT_FORMAT;
        }
    }

    private TextRepairTask getOwnedTask(Long taskId, Long userId) {
        TextRepairTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "修复任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问此修复任务");
        }
        return task;
    }

    private String extractStringOption(String optionsJson, String key, String defaultValue) {
        if (optionsJson == null || optionsJson.isEmpty()) return defaultValue;
        try {
            Map<String, Object> options = objectMapper.readValue(optionsJson,
                    new TypeReference<Map<String, Object>>() {});
            Object value = options.get(key);
            return value != null ? value.toString() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int extractIntOption(String optionsJson, String key, int defaultValue) {
        if (optionsJson == null || optionsJson.isEmpty()) return defaultValue;
        try {
            Map<String, Object> options = objectMapper.readValue(optionsJson,
                    new TypeReference<Map<String, Object>>() {});
            Object value = options.get(key);
            return value instanceof Number number ? number.intValue() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean extractBooleanOption(String optionsJson, String key, boolean defaultValue) {
        if (optionsJson == null || optionsJson.isEmpty()) return defaultValue;
        try {
            Map<String, Object> options = objectMapper.readValue(optionsJson,
                    new TypeReference<Map<String, Object>>() {});
            Object value = options.get(key);
            return value instanceof Boolean bool ? bool : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void mergeJsonOptions(Map<String, Object> target, String json) {
        if (json == null || json.isBlank()) return;
        try {
            target.putAll(objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.warn("忽略无效的修复模板功能配置", e);
        }
    }

    private boolean paragraphOptionEnabled(TextRepairIssue issue,
            boolean lineEnding, boolean blankLines, boolean brokenLines,
            boolean indent) {
        String reason = Objects.toString(issue.getReason(), "");
        if (reason.contains("换行符")) return lineEnding;
        if (reason.contains("多余空行")) return blankLines;
        if (reason.contains("拆成多行")) return brokenLines;
        if (reason.contains("缩进")) return indent;
        return false;
    }

    private boolean duplicateOptionEnabled(TextRepairIssue issue,
            boolean exactChapter, boolean similarChapter,
            boolean duplicateParagraph) {
        String reason = Objects.toString(issue.getReason(), "");
        if (reason.startsWith("完全重复章节")) return exactChapter;
        if (reason.contains("疑似重复章节") || reason.contains("可能重复章节")) {
            return similarChapter;
        }
        if (reason.startsWith("重复段落")) return duplicateParagraph;
        return false;
    }

    private List<AdDetectService.ChapterInfo> convertChapters(List<DetectedChapterDTO> chapters) {
        return chapters.stream()
                .map(c -> new AdDetectService.ChapterInfo(
                        c.getStartOffset() != null ? c.getStartOffset() : 0,
                        c.getEndOffset() != null ? c.getEndOffset() : 0,
                        c.getOriginalTitle() != null ? c.getOriginalTitle() : ""))
                .toList();
    }

    private void updateTaskCounts(Long taskId) {
        List<TextRepairIssue> allIssues = issueRepository
                .findByTaskIdOrderByChapterIndexAscStartOffsetAsc(taskId);
        int total = allIssues.size();
        int pending = 0, accepted = 0, rejected = 0, ignored = 0, applied = 0;
        for (TextRepairIssue issue : allIssues) {
            switch (issue.getStatus()) {
                case PENDING -> pending++;
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
                case IGNORED -> ignored++;
                case APPLIED -> applied++;
                default -> {}
            }
        }
        TextRepairTask task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setTotalIssueCount(total);
            task.setPendingIssueCount(pending);
            task.setAcceptedIssueCount(accepted);
            task.setRejectedIssueCount(rejected);
            task.setIgnoredIssueCount(ignored);
            task.setAppliedIssueCount(applied);
            taskRepository.save(task);
        }
    }

    private String generateReport(TextRepairTask task, List<TextRepairIssue> appliedIssues) {
        RepairReportDTO report = RepairReportDTO.builder()
                .detectedChapters(task.getDetectedChapterCount())
                .fixedEncoding((int) appliedIssues.stream()
                        .filter(i -> i.getType() == RepairIssueType.ENCODING).count())
                .removedAds((int) appliedIssues.stream()
                        .filter(i -> i.getType() == RepairIssueType.AD).count())
                .normalizedChapters((int) appliedIssues.stream()
                        .filter(i -> i.getType() == RepairIssueType.CHAPTER).count())
                .fixedLineBreaks((int) appliedIssues.stream()
                        .filter(i -> i.getType() == RepairIssueType.PARAGRAPH).count())
                .removedDuplicates((int) appliedIssues.stream()
                        .filter(i -> i.getType() == RepairIssueType.DUPLICATE).count())
                .cleanedInvisibleChars((int) appliedIssues.stream()
                        .filter(i -> i.getType() == RepairIssueType.INVISIBLE_CHAR).count())
                .normalizedPunctuation((int) appliedIssues.stream()
                        .filter(i -> i.getType() == RepairIssueType.PUNCTUATION).count())
                .unconfirmedCount(task.getPendingIssueCount())
                .anomalies(issueRepository
                        .findByTaskIdAndType(task.getId(), RepairIssueType.CHAPTER_ANOMALY)
                        .stream()
                        .filter(i -> i.getType() == RepairIssueType.CHAPTER_ANOMALY)
                        .map(i -> RepairReportDTO.AnomalyItem.builder()
                                .type("CHAPTER_ANOMALY")
                                .description(i.getOriginalText())
                                .count(1)
                                .build())
                        .toList())
                .build();
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<RepairPreviewResponse.DiffLine> generateDiffLines(
            String original, String repaired, List<TextRepairIssue> issues) {
        List<RepairPreviewResponse.DiffLine> lines = new ArrayList<>();
        String[] origLines = original.split("\n");
        String[] repLines = repaired.split("\n");

        int maxLines = Math.min(Math.max(origLines.length, repLines.length), 50);
        for (int i = 0; i < maxLines; i++) {
            String origLine = i < origLines.length ? origLines[i] : "";
            String repLine = i < repLines.length ? repLines[i] : "";

            if (origLine.equals(repLine)) {
                lines.add(RepairPreviewResponse.DiffLine.builder()
                        .type("UNCHANGED")
                        .originalLine(origLine)
                        .repairedLine(repLine)
                        .build());
            } else if (origLine.isEmpty() && !repLine.isEmpty()) {
                lines.add(RepairPreviewResponse.DiffLine.builder()
                        .type("ADDED")
                        .originalLine("")
                        .repairedLine(repLine)
                        .build());
            } else if (!origLine.isEmpty() && repLine.isEmpty()) {
                lines.add(RepairPreviewResponse.DiffLine.builder()
                        .type("REMOVED")
                        .originalLine(origLine)
                        .repairedLine("[已删除]")
                        .build());
            } else {
                lines.add(RepairPreviewResponse.DiffLine.builder()
                        .type("MODIFIED")
                        .originalLine(origLine)
                        .repairedLine(repLine)
                        .build());
            }
        }
        return lines;
    }

    private void updateTaskStatus(Long taskId, String status) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            taskRepository.save(task);
        });
    }

    // ==================== DTO 转换 ====================

    private RepairTaskDTO toTaskDTO(TextRepairTask task, Book book) {
        // 统计问题类型和状态
        List<TextRepairIssue> issues = issueRepository
                .findByTaskIdOrderByChapterIndexAscStartOffsetAsc(task.getId());
        Map<RepairIssueType, Integer> typeCounts = new EnumMap<>(RepairIssueType.class);
        Map<RepairIssueStatus, Integer> statusCounts = new EnumMap<>(RepairIssueStatus.class);
        for (TextRepairIssue issue : issues) {
            typeCounts.merge(issue.getType(), 1, Integer::sum);
            statusCounts.merge(issue.getStatus(), 1, Integer::sum);
        }

        return RepairTaskDTO.builder()
                .id(task.getId())
                .bookId(task.getBookId())
                .bookTitle(book.getTitle())
                .versionId(task.getVersionId())
                .templateId(task.getTemplateId())
                .repairMode(task.getRepairMode())
                .status(task.getStatus())
                .originalContentVersion(task.getOriginalContentVersion())
                .repairedContentVersion(task.getRepairedContentVersion())
                .optionsJson(task.getOptionsJson())
                .reportJson(task.getReportJson())
                .totalIssueCount(task.getTotalIssueCount())
                .detectedChapterCount(task.getDetectedChapterCount())
                .pendingIssueCount(task.getPendingIssueCount())
                .acceptedIssueCount(task.getAcceptedIssueCount())
                .rejectedIssueCount(task.getRejectedIssueCount())
                .ignoredIssueCount(task.getIgnoredIssueCount())
                .appliedIssueCount(task.getAppliedIssueCount())
                .userId(task.getUserId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .issueTypeCounts(typeCounts)
                .issueStatusCounts(statusCounts)
                .build();
    }

    private RepairIssueDTO toIssueDTO(TextRepairIssue issue) {
        Map<String, Object> metadata = parseIssueMetadata(issue.getMetadataJson());
        List<String> candidates = null;
        if (metadata != null && metadata.get("candidates") instanceof List<?> values) {
            candidates = values.stream().map(String::valueOf).toList();
        } else if (issue.getMetadataJson() != null && issue.getMetadataJson().startsWith("[")) {
            try {
                candidates = objectMapper.readValue(issue.getMetadataJson(),
                        new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // Keep malformed detector metadata from breaking the issue list.
            }
        }
        return RepairIssueDTO.builder()
                .id(issue.getId())
                .taskId(issue.getTaskId())
                .chapterIndex(issue.getChapterIndex())
                .type(issue.getType())
                .startOffset(issue.getStartOffset())
                .endOffset(issue.getEndOffset())
                .originalText(issue.getOriginalText())
                .suggestedText(issue.getSuggestedText())
                .reason(issue.getReason())
                .ruleId(issue.getRuleId())
                .confidence(issue.getConfidence())
                .status(issue.getStatus())
                .source(issue.getSource())
                .riskLevel(issue.getRiskLevel())
                .metadata(metadata)
                .candidates(candidates)
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .build();
    }

    private boolean ruleAppliesToTask(TextRepairRule rule, TextRepairTask task) {
        String scope = rule.getScope() == null ? "ALL_BOOKS" : rule.getScope();
        return switch (scope) {
            case "CURRENT_BOOK" -> rule.getBookId() != null
                    && rule.getBookId().equals(task.getBookId());
            case "TEMPLATE" -> rule.getTemplateId() != null
                    && rule.getTemplateId().equals(task.getTemplateId());
            default -> true;
        };
    }

    private Map<String, Object> parseIssueMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank() || metadataJson.startsWith("[")) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataJson,
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private org.springframework.data.domain.Page<RepairIssueDTO> toIssueDTOPage(
            List<TextRepairIssue> issues,
            org.springframework.data.domain.Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), issues.size());
        List<RepairIssueDTO> content = issues.subList(
                Math.min(start, issues.size()), end).stream()
                .map(this::toIssueDTO).toList();
        return new org.springframework.data.domain.PageImpl<>(
                content, pageable, issues.size());
    }
}
