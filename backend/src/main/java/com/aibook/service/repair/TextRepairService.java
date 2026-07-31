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
import java.util.stream.Collectors;

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

        // 读取原始内容
        String originalText = readBookContent(version);

        // 创建任务
        TextRepairTask task = TextRepairTask.builder()
                .bookId(book.getId())
                .versionId(version.getId())
                .repairMode(request.getRepairMode())
                .status("SCANNING")
                .originalContentVersion(version.getFileHash())
                .userId(userId)
                .build();

        // 应用模板配置
        if (request.getTemplateId() != null) {
            TextRepairTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElse(null);
            if (template != null) {
                task.setOptionsJson(buildOptionsFromTemplate(template));
            }
        } else if (request.getOptionsJson() != null) {
            task.setOptionsJson(request.getOptionsJson());
        } else {
            task.setOptionsJson(buildDefaultOptions(request.getRepairMode()));
        }

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
            Long taskId, RepairIssueType type, RepairIssueStatus status,
            org.springframework.data.domain.Pageable pageable) {
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
        if (request.getManualText() != null && !request.getManualText().isEmpty()) {
            issue.setSuggestedText(request.getManualText());
        }

        issue = issueRepository.save(issue);

        // 批量应用
        if (Boolean.TRUE.equals(request.getApplyToAll())) {
            List<TextRepairIssue> similarIssues = issueRepository
                    .findByTaskIdAndType(task.getId(), issue.getType()).stream()
                    .filter(i -> i.getStatus() == RepairIssueStatus.PENDING)
                    .toList();
            for (TextRepairIssue similar : similarIssues) {
                similar.setStatus(request.getStatus());
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
    public void batchUpdateIssues(BatchUpdateIssuesRequest request, Long userId) {
        List<TextRepairIssue> issues = issueRepository.findAllById(request.getIssueIds());
        for (TextRepairIssue issue : issues) {
            TextRepairTask task = taskRepository.findById(issue.getTaskId()).orElse(null);
            if (task == null || !task.getUserId().equals(userId)) continue;
            issue.setStatus(request.getStatus());
            issue.setSource(RepairSource.BATCH);
        }
        issueRepository.saveAll(issues);
        // 更新各任务计数
        Set<Long> taskIds = issues.stream().map(TextRepairIssue::getTaskId).collect(Collectors.toSet());
        taskIds.forEach(this::updateTaskCounts);
    }

    /**
     * 批量接受高置信度问题
     */
    @Transactional
    public int acceptHighConfidenceIssues(Long taskId, double threshold) {
        List<TextRepairIssue> issues = issueRepository
                .findByTaskIdAndStatus(taskId, RepairIssueStatus.PENDING);
        int count = 0;
        for (TextRepairIssue issue : issues) {
            if (issue.getConfidence() != null && issue.getConfidence() >= threshold) {
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
        String originalText = readBookContent(version);

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
            String originalText = readBookContent(version);

            // 获取要应用的问题
            List<TextRepairIssue> issuesToApply;
            if (Boolean.TRUE.equals(request.getAcceptedOnly())) {
                issuesToApply = issueRepository
                        .findByTaskIdAndStatus(task.getId(), RepairIssueStatus.ACCEPTED);
            } else {
                issuesToApply = issueRepository
                        .findByTaskIdAndStatusIn(task.getId(),
                                List.of(RepairIssueStatus.ACCEPTED, RepairIssueStatus.PENDING));
            }

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

            // 生成修复报告
            task.setRepairedContentVersion(hash);
            task.setStatus("COMPLETED");
            task.setReportJson(generateReport(task, issuesToApply));
            taskRepository.save(task);

            updateTaskCounts(task.getId());

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
        List<TextRepairIssue> encodingIssues = encodingDetectService
                .scanForIssues(text, task.getId());
        allIssues.addAll(encodingIssues);

        // 2. 不可见字符
        List<TextRepairIssue> punctIssues = punctuationFixService
                .scanForIssues(text, lines, task.getId());
        allIssues.addAll(punctIssues);

        // 3. 段落格式
        List<TextRepairIssue> paragraphIssues = paragraphFixService
                .scanForIssues(text, lines, task.getId());
        allIssues.addAll(paragraphIssues);

        // 4. 章节识别
        List<DetectedChapterDTO> chapters = chapterDetectService.detectChapters(text);
        List<TextRepairIssue> chapterIssues = chapterDetectService
                .scanForIssues(chapters, task.getId());
        allIssues.addAll(chapterIssues);

        // 5. 章节标题规范化（标准及以上模式）
        if (task.getRepairMode() != RepairMode.SAFE) {
            String chapterFormat = extractChapterFormat(task.getOptionsJson());
            List<TextRepairIssue> normalizeIssues = chapterNormalizeService
                    .scanForIssues(chapters, chapterFormat, task.getId());
            allIssues.addAll(normalizeIssues);
        }

        // 6. 广告检测
        List<TextRepairRule> adRules = ruleRepository
                .findByUserIdOrUserIdIsNullAndEnabledTrue(task.getUserId());
        List<TextRepairIssue> adIssues = adDetectService.scanForIssues(
                text, lines, convertChapters(chapters), task.getId(), adRules);
        allIssues.addAll(adIssues);

        // 7. 重复内容检测
        List<TextRepairIssue> dupIssues = duplicateDetectService
                .scanForIssues(text, chapters, task.getId());
        allIssues.addAll(dupIssues);

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

        // 按偏移量排序（从后往前应用，避免偏移量变化）
        List<TextRepairIssue> sortedIssues = issues.stream()
                .sorted(Comparator.comparingInt(
                        (TextRepairIssue i) -> i.getStartOffset() != null ? -i.getStartOffset() : 0))
                .toList();

        for (TextRepairIssue issue : sortedIssues) {
            result = applySingleIssue(result, issue);
        }

        return result;
    }

    private String applySingleIssue(String text, TextRepairIssue issue) {
        if (issue.getStartOffset() == null || issue.getEndOffset() == null) {
            return text;
        }
        int start = Math.min(issue.getStartOffset(), text.length());
        int end = Math.min(issue.getEndOffset(), text.length());
        if (start >= end) return text;

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
        try {
            Path file = Paths.get(version.getFilePath());
            if (!Files.isRegularFile(file)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍文件不存在");
            }
            byte[] bytes = Files.readAllBytes(file);
            return encodingDetectService.decodeWithEncoding(bytes, "AUTO");
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
                .detectedChapters(0)
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
                .anomalies(appliedIssues.stream()
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
                .repairMode(task.getRepairMode())
                .status(task.getStatus())
                .originalContentVersion(task.getOriginalContentVersion())
                .repairedContentVersion(task.getRepairedContentVersion())
                .optionsJson(task.getOptionsJson())
                .reportJson(task.getReportJson())
                .totalIssueCount(task.getTotalIssueCount())
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
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .build();
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
