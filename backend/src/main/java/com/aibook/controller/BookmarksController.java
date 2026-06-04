package com.aibook.controller;

import com.aibook.dto.CreateBookmarkRequest;
import com.aibook.model.entity.Bookmark;
import com.aibook.model.entity.User;
import com.aibook.service.BookmarkService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 书签控制器
 */
@RestController
@RequestMapping("/api/books/{bookId}/bookmarks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookmarksController {

    private final BookmarkService bookmarkService;
    private final UserService userService;

    /**
     * 获取书籍的所有书签
     */
    @GetMapping
    public ResponseEntity<List<Bookmark>> getBookmarks(
            Authentication authentication,
            @PathVariable Long bookId) {
        User user = getUserFromAuth(authentication);
        List<Bookmark> bookmarks = bookmarkService.getBookmarks(user, bookId);
        return ResponseEntity.ok(bookmarks);
    }

    /**
     * 创建书签
     */
    @PostMapping
    public ResponseEntity<Bookmark> createBookmark(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestBody CreateBookmarkRequest request) {
        User user = getUserFromAuth(authentication);
        Bookmark bookmark = bookmarkService.createBookmark(user, bookId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookmark);
    }

    /**
     * 删除书签
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookmark(
            Authentication authentication,
            @PathVariable Long bookId,
            @PathVariable Long id) {
        User user = getUserFromAuth(authentication);
        bookmarkService.deleteBookmark(user, bookId, id);
        return ResponseEntity.noContent().build();
    }

    private User getUserFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }
}
