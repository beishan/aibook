package com.aibook.controller;

import com.aibook.model.entity.ScanDirectory;
import com.aibook.service.ScanDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 扫描目录控制器
 */
@RestController
@RequestMapping("/api/scan-directories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScanDirectoryController {

    private final ScanDirectoryService scanDirectoryService;

    /**
     * 获取所有扫描目录
     */
    @GetMapping
    public ResponseEntity<List<ScanDirectory>> getAllDirectories() {
        return ResponseEntity.ok(scanDirectoryService.getAllDirectories());
    }

    /**
     * 添加扫描目录
     */
    @PostMapping
    public ResponseEntity<ScanDirectory> addDirectory(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(scanDirectoryService.addDirectory(path));
    }

    /**
     * 删除扫描目录
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDirectory(@PathVariable Long id) {
        scanDirectoryService.deleteDirectory(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 切换目录启用状态
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ScanDirectory> toggleDirectory(@PathVariable Long id) {
        return ResponseEntity.ok(scanDirectoryService.toggleDirectory(id));
    }

    /**
     * 扫描指定目录
     */
    @PostMapping("/{id}/scan")
    public ResponseEntity<ScanDirectory> scanDirectory(@PathVariable Long id) {
        return ResponseEntity.ok(scanDirectoryService.scanDirectory(id));
    }
}
