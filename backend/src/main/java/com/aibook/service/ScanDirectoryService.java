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
import java.util.List;
import java.util.Map;

/**
 * 扫描目录管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScanDirectoryService {

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final FileScannerService fileScannerService;

    /**
     * 获取所有扫描目录（管理用）
     */
    public List<ScanDirectory> getAllDirectories() {
        return scanDirectoryRepository.findAll();
    }

    /**
     * 获取用户的扫描目录
     */
    public List<ScanDirectory> getDirectories(User user) {
        return scanDirectoryRepository.findByUser(user);
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
                .build();

        ScanDirectory saved = scanDirectoryRepository.save(directory);
        log.info("添加扫描目录: {} (存在: {})", path, exists);

        return saved;
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
    }

    /**
     * 触发扫描目录 - 实际导入书籍到数据库
     */
    @Transactional
    public Map<String, Object> scanDirectory(User user, Long id) {
        ScanDirectory dir = scanDirectoryRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));

        Path dirPath = Paths.get(dir.getPath());
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return Map.of(
                "success", false,
                "message", "目录不存在: " + dir.getPath()
            );
        }

        // 调用 FileScannerService 实际导入书籍
        log.info("开始扫描目录并导入书籍: {}", dir.getPath());
        FileScannerService.ScanResult scanResult = fileScannerService.scanDirectory(dir.getPath());

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
    }

    /**
     * 切换启用状态
     */
    @Transactional
    public ScanDirectory toggleEnabled(User user, Long id) {
        ScanDirectory dir = scanDirectoryRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("扫描目录", id));

        dir.setEnabled(!dir.getEnabled());
        return scanDirectoryRepository.save(dir);
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
