package com.aibook.service;

import com.aibook.model.entity.ScanDirectory;
import com.aibook.repository.ScanDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描目录服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScanDirectoryService {

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final FileScannerService fileScannerService;

    /**
     * 获取所有扫描目录
     */
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
    public ScanDirectory addDirectory(String path) {
        if (scanDirectoryRepository.existsByPath(path)) {
            throw new RuntimeException("目录已存在: " + path);
        }

        ScanDirectory directory = ScanDirectory.builder()
                .path(path)
                .enabled(true)
                .build();

        return scanDirectoryRepository.save(directory);
    }

    /**
     * 删除扫描目录
     */
    @Transactional
    public void deleteDirectory(Long id) {
        scanDirectoryRepository.deleteById(id);
    }

    /**
     * 切换目录启用状态
     */
    @Transactional
    public ScanDirectory toggleDirectory(Long id) {
        ScanDirectory directory = scanDirectoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));

        directory.setEnabled(!directory.getEnabled());
        return scanDirectoryRepository.save(directory);
    }

    /**
     * 扫描指定目录
     */
    @Transactional
    public ScanDirectory scanDirectory(Long id) {
        ScanDirectory directory = scanDirectoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));

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

        return scanDirectoryRepository.save(directory);
    }
}
