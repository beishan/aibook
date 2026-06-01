package com.aibook.controller;

import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import com.aibook.service.BookListService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 书单控制器
 */
@RestController
@RequestMapping("/api/booklists")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookListController {

    private final BookListService bookListService;
    private final UserService userService;

    /**
     * 获取所有书单
     */
    @GetMapping
    public ResponseEntity<List<BookList>> getBookLists(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<BookList> bookLists = bookListService.getBookLists(user);
        return ResponseEntity.ok(bookLists);
    }

    /**
     * 获取书单详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookList> getBookList(
            Authentication authentication,
            @PathVariable Long id) {
        User user = userService.findByUsername(authentication.getName());
        BookList bookList = bookListService.getBookList(id, user);
        return ResponseEntity.ok(bookList);
    }

    /**
     * 创建书单
     */
    @PostMapping
    public ResponseEntity<BookList> createBookList(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        User user = userService.findByUsername(authentication.getName());
        BookList bookList = bookListService.createBookList(user, body.get("name"), body.get("description"));
        return ResponseEntity.ok(bookList);
    }

    /**
     * 更新书单
     */
    @PutMapping("/{id}")
    public ResponseEntity<BookList> updateBookList(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userService.findByUsername(authentication.getName());
        BookList bookList = bookListService.updateBookList(id, body.get("name"), body.get("description"), user);
        return ResponseEntity.ok(bookList);
    }

    /**
     * 删除书单
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookList(
            Authentication authentication,
            @PathVariable Long id) {
        User user = userService.findByUsername(authentication.getName());
        bookListService.deleteBookList(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 向书单添加书籍
     */
    @PostMapping("/{listId}/books/{bookId}")
    public ResponseEntity<BookList> addBookToList(
            Authentication authentication,
            @PathVariable Long listId,
            @PathVariable Long bookId) {
        User user = userService.findByUsername(authentication.getName());
        BookList bookList = bookListService.addBookToList(listId, bookId, user);
        return ResponseEntity.ok(bookList);
    }

    /**
     * 从书单移除书籍
     */
    @DeleteMapping("/{listId}/books/{bookId}")
    public ResponseEntity<BookList> removeBookFromList(
            Authentication authentication,
            @PathVariable Long listId,
            @PathVariable Long bookId) {
        User user = userService.findByUsername(authentication.getName());
        BookList bookList = bookListService.removeBookFromList(listId, bookId, user);
        return ResponseEntity.ok(bookList);
    }
}
