package com.aibook.controller;

import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.service.ScanDirectoryService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 扫描目录管理控制器
 */
@RestController
@RequestMapping("/api/scan-directories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScanDirectoryController {

    private final ScanDirectoryService scanDirectoryService;
    private final UserService userService;

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
    public ResponseEntity<ScanDirectory> addDirectory(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        User user = getUserFromAuth(authentication);
        return ResponseEntity.ok(scanDirectoryService.addDirectory(user, path));
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
     * 触发扫描
     */
    @PostMapping("/{id}/scan")
    public ResponseEntity<Map<String, Object>> scanDirectory(@PathVariable Long id) {
        Map<String, Object> result = scanDirectoryService.scanDirectory(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 切换启用状态
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ScanDirectory> toggleEnabled(@PathVariable Long id) {
        ScanDirectory dir = scanDirectoryService.toggleEnabled(id);
        return ResponseEntity.ok(dir);
    }

    private User getUserFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }
}
