package com.aibook.controller;

import com.aibook.dto.ScanRecordDTO;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.service.ScanDirectoryService;
import com.aibook.service.ScanDirectoryTaskService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final ScanDirectoryTaskService scanDirectoryTaskService;
    private final UserService userService;

    /**
     * 获取所有扫描目录
     */
    @GetMapping
    public ResponseEntity<List<ScanDirectory>> getAllDirectories(Authentication authentication) {
        return ResponseEntity.ok(scanDirectoryService.getAllDirectories(getUserFromAuth(authentication)));
    }

    /**
     * 添加扫描目录
     */
    @PostMapping
    public ResponseEntity<ScanDirectory> addDirectory(
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        String path = body.get("path") == null ? null : body.get("path").toString();
        if (path == null || path.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        User user = getUserFromAuth(authentication);
        Long categoryId = body.get("defaultCategoryId") == null
                ? null
                : Long.valueOf(body.get("defaultCategoryId").toString());
        return ResponseEntity.ok(scanDirectoryService.addDirectory(user, path, categoryId));
    }

    /**
     * 删除扫描目录
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDirectory(
            Authentication authentication, @PathVariable Long id) {
        scanDirectoryService.deleteDirectory(id, getUserFromAuth(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * 触发扫描
     */
    @PostMapping("/{id}/scan")
    public ResponseEntity<Map<String, Object>> scanDirectory(
            Authentication authentication,
            @PathVariable Long id) {
        User user = getUserFromAuth(authentication);
        Map<String, Object> task = scanDirectoryTaskService.startScan(id, user);
        return ResponseEntity.accepted().body(task);
    }

    /**
     * 查询扫描进度。
     */
    @GetMapping("/{id}/scan-progress")
    public ResponseEntity<Map<String, Object>> getScanProgress(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(scanDirectoryTaskService.getProgress(
                id,
                getUserFromAuth(authentication)));
    }

    /**
     * 分页查询当前用户的扫描记录。
     */
    @GetMapping("/scan-history")
    public ResponseEntity<Page<ScanRecordDTO>> getScanHistory(
            Authentication authentication,
            @RequestParam(required = false) Long directoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                Math.min(Math.max(size, 1), 100),
                Sort.by("startedAt").descending());
        return ResponseEntity.ok(scanDirectoryTaskService.getHistory(
                getUserFromAuth(authentication),
                directoryId,
                status,
                pageRequest));
    }

    /**
     * 切换启用状态
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ScanDirectory> toggleEnabled(
            Authentication authentication, @PathVariable Long id) {
        ScanDirectory dir =
                scanDirectoryService.toggleEnabled(id, getUserFromAuth(authentication));
        return ResponseEntity.ok(dir);
    }

    /** 单独控制目录来源书籍是否出现在书库发现列表中。 */
    @PutMapping("/{id}/library-visibility")
    public ResponseEntity<ScanDirectory> updateLibraryVisibility(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Object value = body.get("visible");
        if (!(value instanceof Boolean visible)) {
            throw new IllegalArgumentException("visible 必须为布尔值");
        }
        return ResponseEntity.ok(scanDirectoryService.updateLibraryVisibility(
                id, visible, getUserFromAuth(authentication)));
    }

    @PutMapping("/{id}/default-category")
    public ResponseEntity<ScanDirectory> updateDefaultCategory(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long categoryId = body.get("categoryId") == null
                ? null
                : Long.valueOf(body.get("categoryId").toString());
        return ResponseEntity.ok(scanDirectoryService.updateDefaultCategory(
                id, categoryId, getUserFromAuth(authentication)));
    }

    private User getUserFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }
}
