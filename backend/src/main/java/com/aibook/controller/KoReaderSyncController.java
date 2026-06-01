package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.BookHighlightService;
import com.aibook.service.KoReaderSyncService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * KOReader 同步控制器
 * 提供 KOReader 阅读器的进度和高亮同步接口
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class KoReaderSyncController {

    private final KoReaderSyncService koReaderSyncService;
    private final BookHighlightService bookHighlightService;
    private final UserService userService;

    // ==================== 进度同步 ====================

    /**
     * 获取阅读进度
     */
    @GetMapping("/progress/{documentId}")
    public ResponseEntity<Map<String, Object>> getProgress(
            Authentication authentication,
            @PathVariable String documentId) {
        User user = getUserFromAuth(authentication);
        Map<String, Object> progress = koReaderSyncService.getProgress(user, documentId);
        return ResponseEntity.ok(progress);
    }

    /**
     * 保存阅读进度
     */
    @PostMapping("/progress/{documentId}")
    public ResponseEntity<Map<String, Object>> saveProgress(
            Authentication authentication,
            @PathVariable String documentId,
            @RequestBody Map<String, Object> progressData) {
        User user = getUserFromAuth(authentication);
        Map<String, Object> result = koReaderSyncService.saveProgress(user, documentId, progressData);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量同步进度
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncProgress(
            Authentication authentication,
            @RequestBody Map<String, Object> syncData) {
        User user = getUserFromAuth(authentication);
        Map<String, Object> result = koReaderSyncService.syncProgress(user, syncData);
        return ResponseEntity.ok(result);
    }

    // ==================== 高亮同步 ====================

    /**
     * 获取高亮列表（KOReader 格式）
     */
    @GetMapping("/highlights/{documentId}")
    public ResponseEntity<List<Map<String, Object>>> getHighlights(
            Authentication authentication,
            @PathVariable String documentId) {
        User user = getUserFromAuth(authentication);
        List<Map<String, Object>> highlights = bookHighlightService.getHighlightsForSync(user, documentId);
        return ResponseEntity.ok(highlights);
    }

    /**
     * 同步高亮（KOReader 格式）
     * 客户端上传高亮列表，服务端合并新增
     */
    @PostMapping("/highlights/{documentId}")
    public ResponseEntity<Map<String, Object>> syncHighlights(
            Authentication authentication,
            @PathVariable String documentId,
            @RequestBody List<Map<String, Object>> highlights) {
        User user = getUserFromAuth(authentication);
        Map<String, Object> result = bookHighlightService.syncHighlights(user, documentId, highlights);
        return ResponseEntity.ok(result);
    }

    // ==================== 辅助方法 ====================

    private User getUserFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }
}
