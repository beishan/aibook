package com.aibook.controller;

import com.aibook.model.entity.ReadingProgress;
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
    public ResponseEntity<ReadingProgress> getProgress(
            Authentication authentication,
            @PathVariable Long bookId) {
        User user = userService.findByUsername(authentication.getName());
        ReadingProgress progress = readingProgressService.getProgress(bookId, user);
        return ResponseEntity.ok(progress);
    }

    /**
     * 保存阅读进度
     */
    @PostMapping("/book/{bookId}")
    public ResponseEntity<ReadingProgress> saveProgress(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestBody Map<String, Object> body) {
        User user = userService.findByUsername(authentication.getName());

        String currentChapter = (String) body.get("currentChapter");
        Integer chapterProgress = body.get("chapterProgress") != null ?
            Integer.valueOf(body.get("chapterProgress").toString()) : 0;
        Integer totalProgress = body.get("totalProgress") != null ?
            Integer.valueOf(body.get("totalProgress").toString()) : 0;

        ReadingProgress progress = readingProgressService.saveProgress(
            bookId, user, currentChapter, chapterProgress, totalProgress);
        return ResponseEntity.ok(progress);
    }

    /**
     * 更新阅读时长
     */
    @PutMapping("/book/{bookId}/time")
    public ResponseEntity<ReadingProgress> updateReadingTime(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestBody Map<String, Long> body) {
        User user = userService.findByUsername(authentication.getName());
        Long additionalSeconds = body.get("seconds");

        ReadingProgress progress = readingProgressService.updateReadingTime(
            bookId, user, additionalSeconds);
        return ResponseEntity.ok(progress);
    }
}
