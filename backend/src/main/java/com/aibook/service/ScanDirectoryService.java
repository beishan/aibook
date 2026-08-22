package com.aibook.service;

import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.ScanDirectoryBookCountProjection;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.ScanDirectoryRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 扫描目录管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScanDirectoryService {

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final FileScannerService fileScannerService;
    private final CategoryService categoryService;
    private final BookScanSourceRepository bookScanSourceRepository;
    private final OperationLogService operationLogService;

    /**
     * 获取所有扫描目录
     */
    @Transactional(readOnly = true)
    public List<ScanDirectory> getAllDirectories(User user) {
        return applyCurrentBookCounts(scanDirectoryRepository.findByUser(user), user);
    }

    /**
     * 获取用户的扫描目录
     */
    @Transactional(readOnly = true)
    public List<ScanDirectory> getDirectories(User user) {
        return getAllDirectories(user);
    }

    /**
     * 获取启用的扫描目录
     */
    public List<ScanDirectory> getEnabledDirectories() {
        return scanDirectoryRepository.findByEnabledTrue();
    }

    /**
     * 添加扫描目录
     */
    @Transactional
    public ScanDirectory addDirectory(User user, String path, Long defaultCategoryId) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("目录路径不能为空");
        }

        // 验证路径格式
        path = path.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // 检查是否已存在
        if (scanDirectoryRepository.existsByUserAndPath(user, path)) {
            throw new IllegalArgumentException("该目录已添加");
        }

        // 检查目录是否存在
        Path dirPath = Paths.get(path);
        boolean exists = Files.exists(dirPath) && Files.isDirectory(dirPath);

        ScanDirectory directory = ScanDirectory.builder()
                .path(path)
                .enabled(exists)
                .defaultCategory(defaultCategoryId == null
                        ? null
                        : categoryService.getOwnedCategory(defaultCategoryId, user))
                .user(user)
                .bookCount(0)
                .build();

        ScanDirectory saved = scanDirectoryRepository.save(directory);
        log.info("添加扫描目录: {} (存在: {})", path, exists);

        return applyCurrentBookCount(saved, user);
    }

    /**
     * 删除扫描目录（所有用户都可以操作）
     */
    @Transactional
    public void deleteDirectory(Long id, User user) {
        ScanDirectory dir = scanDirectoryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));
        bookScanSourceRepository.deleteByScanDirectory(dir);
        scanDirectoryRepository.delete(dir);
        log.info("删除扫描目录: {}", dir.getPath());
    }

    /**
     * 触发扫描目录 - 实际导入书籍到数据库（所有用户都可以操作）
     */
    @Transactional
    public Map<String, Object> scanDirectory(Long id, User user) {
        ScanDirectory dir = scanDirectoryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));

        if (!Boolean.TRUE.equals(dir.getEnabled())) {
            throw new IllegalArgumentException("扫描目录已禁用，请先启用后再扫描");
        }

        Path dirPath = Paths.get(dir.getPath());
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return Map.of(
                "success", false,
                "message", "目录不存在: " + dir.getPath()
            );
        }

        // 调用 FileScannerService 实际导入书籍
        log.info("开始扫描目录并导入书籍: {}", dir.getPath());
        FileScannerService.ScanResult scanResult = fileScannerService.scanDirectory(
                dir.getPath(), user, dir.defaultCategoryId(), dir.getId());

        // 更新扫描目录记录
        dir.setLastScanTime(LocalDateTime.now());
        int bookCount = applyCurrentBookCount(dir, user).getBookCount();
        scanDirectoryRepository.save(dir);

        log.info("扫描目录完成: {}, 新增: {}, 跳过: {}, 失败: {}",
                dir.getPath(), scanResult.getNewCount(), scanResult.getSkippedCount(), scanResult.getFailedCount());

        return Map.of(
            "success", true,
            "message", "扫描完成",
            "path", dir.getPath(),
            "newBooks", scanResult.getNewCount(),
            "skippedBooks", scanResult.getSkippedCount(),
            "failedBooks", scanResult.getFailedCount(),
            "threadCount", scanResult.getThreadCount(),
            "bookCount", bookCount
        );
    }

    /**
     * 切换启用状态（所有用户都可以操作）
     */
    @Transactional
    public ScanDirectory toggleEnabled(Long id, User user) {
        ScanDirectory dir = scanDirectoryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));

        dir.setEnabled(!Boolean.TRUE.equals(dir.getEnabled()));
        return applyCurrentBookCount(scanDirectoryRepository.save(dir), user);
    }

    /** 更新目录在书库中的展示状态；重复提交同一状态不产生额外副作用。 */
    @Transactional
    public ScanDirectory updateLibraryVisibility(Long id, boolean visible, User user) {
        ScanDirectory dir = scanDirectoryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));
        if (Boolean.valueOf(visible).equals(dir.getLibraryVisible())) {
            return applyCurrentBookCount(dir, user);
        }
        dir.setLibraryVisible(visible);
        ScanDirectory saved = scanDirectoryRepository.save(dir);
        operationLogService.record(
                user,
                OperationLog.Action.UPDATE_SCAN_DIRECTORY_VISIBILITY,
                null,
                (visible ? "书库显示" : "书库隐藏") + "扫描目录",
                dir.getPath());
        return applyCurrentBookCount(saved, user);
    }

    /**
     * 更新扫描目录的新书默认分类。
     */
    @Transactional
    public ScanDirectory updateDefaultCategory(Long id, Long categoryId, User user) {
        ScanDirectory dir = scanDirectoryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));
        dir.setDefaultCategory(categoryId == null
                ? null
                : categoryService.getOwnedCategory(categoryId, user));
        return applyCurrentBookCount(scanDirectoryRepository.save(dir), user);
    }

    /**
     * 设置仅用于 API 响应和后续保存的目录书籍数。真实来源是 BookScanSource，不能由某次扫描结果推断。
     */
    private ScanDirectory applyCurrentBookCount(ScanDirectory directory, User user) {
        long count = bookScanSourceRepository
                .countDistinctActiveBooksByScanDirectoryAndUser(directory, user);
        directory.setBookCount(Math.toIntExact(count));
        return directory;
    }

    /** 目录列表使用一次 group-by 查询回填响应数字，避免 N+1。 */
    private List<ScanDirectory> applyCurrentBookCounts(
            List<ScanDirectory> directories, User user) {
        Map<Long, Long> counts = bookScanSourceRepository
                .countDistinctActiveBooksByDirectoryAndUser(user)
                .stream()
                .collect(Collectors.toMap(
                        ScanDirectoryBookCountProjection::getScanDirectoryId,
                        ScanDirectoryBookCountProjection::getBookCount));
        directories.forEach(directory -> directory.setBookCount(
                Math.toIntExact(counts.getOrDefault(directory.getId(), 0L))));
        return directories;
    }

    /**
     * 判断是否为书籍文件
     */
    private boolean isBookFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".epub") || lower.endsWith(".txt") ||
               lower.endsWith(".pdf") || lower.endsWith(".mobi") ||
               lower.endsWith(".azw3") || lower.endsWith(".docx") ||
               lower.endsWith(".html") || lower.endsWith(".htm") ||
               lower.endsWith(".md") || lower.endsWith(".cbz") ||
               lower.endsWith(".cbr");
    }
}
