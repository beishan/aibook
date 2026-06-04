package com.aibook.service;

import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.ScanDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描目录管理服务
 * 扫描目录服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScanDirectoryService {

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final FileScannerService fileScannerService;

    /**
     * 获取用户的所有扫描目录
     */
    public List<ScanDirectory> getDirectories(User user) {
        return scanDirectoryRepository.findByUser(user);
    public List<ScanDirectory> getAllDirectories() {
        return scanDirectoryRepository.findAll();
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
    public ScanDirectory addDirectory(User user, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("目录路径不能为空");
        }

        // 验证路径格式
        path = path.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
    public ScanDirectory addDirectory(String path) {
        if (scanDirectoryRepository.existsByPath(path)) {
            throw new RuntimeException("目录已存在: " + path);
        }

        // 检查是否已存在
        if (scanDirectoryRepository.findByUserAndPath(user, path).isPresent()) {
            throw new IllegalArgumentException("该目录已添加");
        }

        // 检查目录是否存在
        Path dirPath = Paths.get(path);
        boolean exists = Files.exists(dirPath) && Files.isDirectory(dirPath);

        ScanDirectory directory = ScanDirectory.builder()
                .path(path)
                .enabled(exists)
                .user(user)
                .bookCount(0)
                .enabled(true)
                .build();

        ScanDirectory saved = scanDirectoryRepository.save(directory);
        log.info("添加扫描目录: {} (存在: {})", path, exists);

        return saved;
        return scanDirectoryRepository.save(directory);
    }

    /**
     * 删除扫描目录
     */
    @Transactional
    public void deleteDirectory(User user, Long id) {
        ScanDirectory dir = scanDirectoryRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));
        scanDirectoryRepository.delete(dir);
        log.info("删除扫描目录: {}", dir.getPath());
    public void deleteDirectory(Long id) {
        scanDirectoryRepository.deleteById(id);
    }

    /**
     * 触发扫描目录 - 实际导入书籍到数据库
     * 切换目录启用状态
     */
    @Transactional
    public Map<String, Object> scanDirectory(User user, Long id) {
        ScanDirectory dir = scanDirectoryRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));
    public ScanDirectory toggleDirectory(Long id) {
        ScanDirectory directory = scanDirectoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));

        Path dirPath = Paths.get(dir.getPath());
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return Map.of(
                "success", false,
                "message", "目录不存在: " + dir.getPath()
            );
        }

        // 调用 FileScannerService 实际导入书籍
        log.info("开始扫描目录并导入书籍: {}", dir.getPath());
        FileScannerService.ScanResult scanResult = fileScannerService.scanDirectory(dirPath, user);

        // 更新扫描目录记录
        dir.setLastScanTime(LocalDateTime.now());
        dir.setBookCount(scanResult.getNewCount() + scanResult.getSkippedCount());
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
            "bookCount", scanResult.getNewCount() + scanResult.getSkippedCount()
        );
        directory.setEnabled(!directory.getEnabled());
        return scanDirectoryRepository.save(directory);
    }

    /**
     * 切换启用状态
     * 扫描指定目录
     */
    @Transactional
    public ScanDirectory toggleEnabled(User user, Long id) {
        ScanDirectory dir = scanDirectoryRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));
    public ScanDirectory scanDirectory(Long id) {
        ScanDirectory directory = scanDirectoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));

        dir.setEnabled(!dir.getEnabled());
        return scanDirectoryRepository.save(dir);
    }

    /**
     * 统计目录中的书籍文件数量
     */
    private int countBookFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isBookFile(p.toString()))
                    .count();
        try {
            FileScannerService.ScanResult result = fileScannerService.scanDirectory(directory.getPath());
            directory.setLastScanTime(LocalDateTime.now());
            directory.setLastScanResult(String.format("新增: %d, 跳过: %d, 失败: %d",
                    result.getNewCount(), result.getSkippedCount(), result.getFailedCount()));
        } catch (Exception e) {
            directory.setLastScanTime(LocalDateTime.now());
            directory.setLastScanResult("扫描失败: " + e.getMessage());
            log.error("扫描目录失败: {}", directory.getPath(), e);
        }
            log.warn("统计目录文件失败: {}", e.getMessage());
            return 0;
        }
    }

        return scanDirectoryRepository.save(directory);
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
