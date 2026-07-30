package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.ReadingProgressService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 阅读进度控制器
 */
@RestController
@RequestMapping("/api/reading-progress")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReadingProgressController {

    private final ReadingProgressService readingProgressService;
    private final UserService userService;

    /**
     * 获取阅读进度
     */
    @GetMapping("/book/{bookId}")
    public ResponseEntity<com.aibook.dto.ReadingProgressDTO> getProgress(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestParam(required = false) Long versionId) {
        User user = userService.findByUsername(authentication.getName());
        com.aibook.dto.ReadingProgressDTO progress =
                readingProgressService.getProgress(bookId, versionId, user);
        return ResponseEntity.ok(progress);
    }

    /**
     * 保存阅读进度
     */
    @PostMapping("/book/{bookId}")
    public ResponseEntity<com.aibook.dto.ReadingProgressDTO> saveProgress(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestParam(required = false) Long versionId,
            @RequestBody Map<String, Object> body) {
        User user = userService.findByUsername(authentication.getName());

        String currentChapter = (String) body.get("currentChapter");
        String currentChapterTitle = (String) body.get("currentChapterTitle");
        Integer chapterProgress = body.get("chapterProgress") != null ?
            Integer.valueOf(body.get("chapterProgress").toString()) : 0;
        Integer totalProgress = body.get("totalProgress") != null ?
            Integer.valueOf(body.get("totalProgress").toString()) : 0;

        com.aibook.dto.ReadingProgressDTO progress =
                readingProgressService.saveProgress(
                        bookId, versionId, user, currentChapter,
                        currentChapterTitle, chapterProgress, totalProgress);
        return ResponseEntity.ok(progress);
    }

    /**
     * 更新阅读时长
     */
    @PutMapping("/book/{bookId}/time")
    public ResponseEntity<com.aibook.dto.ReadingProgressDTO> updateReadingTime(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestParam(required = false) Long versionId,
            @RequestBody Map<String, Long> body) {
        User user = userService.findByUsername(authentication.getName());
        Long additionalSeconds = body.get("seconds");

        com.aibook.dto.ReadingProgressDTO progress =
                readingProgressService.updateReadingTime(
                        bookId, versionId, user, additionalSeconds);
        return ResponseEntity.ok(progress);
    }
}
