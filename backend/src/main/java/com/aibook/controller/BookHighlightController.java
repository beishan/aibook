package com.aibook.controller;

import com.aibook.dto.CreateHighlightRequest;
import com.aibook.dto.UpdateHighlightRequest;
import com.aibook.model.entity.BookHighlight;
import com.aibook.model.entity.User;
import com.aibook.service.BookHighlightService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 书籍高亮/批注控制器
 */
@RestController
@RequestMapping("/api/books/{bookId}/highlights")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookHighlightController {

    private final BookHighlightService bookHighlightService;
    private final UserService userService;

    /**
     * 获取书籍的所有高亮
     */
    @GetMapping
    public ResponseEntity<List<BookHighlight>> getHighlights(
            Authentication authentication,
            @PathVariable Long bookId) {
        User user = getUserFromAuth(authentication);
        List<BookHighlight> highlights = bookHighlightService.getHighlights(user, bookId);
        return ResponseEntity.ok(highlights);
    }

    /**
     * 创建高亮
     */
    @PostMapping
    public ResponseEntity<BookHighlight> createHighlight(
            Authentication authentication,
            @PathVariable Long bookId,
            @Valid @RequestBody CreateHighlightRequest request) {
        User user = getUserFromAuth(authentication);
        BookHighlight highlight = bookHighlightService.createHighlight(user, bookId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(highlight);
    }

    /**
     * 更新高亮
     */
    @PutMapping("/{id}")
    public ResponseEntity<BookHighlight> updateHighlight(
            Authentication authentication,
            @PathVariable Long bookId,
            @PathVariable Long id,
            @RequestBody UpdateHighlightRequest request) {
        User user = getUserFromAuth(authentication);
        BookHighlight highlight = bookHighlightService.updateHighlight(user, bookId, id, request);
        return ResponseEntity.ok(highlight);
    }

    /**
     * 删除高亮
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHighlight(
            Authentication authentication,
            @PathVariable Long bookId,
            @PathVariable Long id) {
        User user = getUserFromAuth(authentication);
        bookHighlightService.deleteHighlight(user, bookId, id);
        return ResponseEntity.noContent().build();
    }

    private User getUserFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }
}
