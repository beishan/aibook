package com.aibook.controller;

import com.aibook.dto.BookDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.service.BookService;
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
 * 书籍控制器
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookController {

    private final BookService bookService;
    private final UserService userService;

    /**
     * 获取书籍列表
     */
    @GetMapping
    public ResponseEntity<Page<BookDTO>> getBooks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId) {

        User user = userService.findByUsername(authentication.getName());

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<BookDTO> books;

        if (format != null && !format.isEmpty()) {
            books = bookService.getBooksByFormat(user, format, pageRequest);
        } else if (status != null && !status.isEmpty()) {
            Book.ReadingStatus readingStatus = Book.ReadingStatus.valueOf(status);
            books = bookService.getBooksByStatus(user, readingStatus, pageRequest);
        } else if (categoryId != null) {
            books = bookService.getBooksByCategory(user, categoryId, pageRequest);
        } else if (tagId != null) {
            books = bookService.getBooksByTag(user, tagId, pageRequest);
        } else {
            books = bookService.getBooks(user, pageRequest);
        }

        return ResponseEntity.ok(books);
    }

    /**
     * 获取收藏书籍
     */
    @GetMapping("/favorites")
    public ResponseEntity<Page<BookDTO>> getFavoriteBooks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<BookDTO> books = bookService.getFavoriteBooks(user, pageRequest);
        return ResponseEntity.ok(books);
    }

    /**
     * 获取想读书籍
     */
    @GetMapping("/wanted")
    public ResponseEntity<Page<BookDTO>> getWantedBooks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<BookDTO> books = bookService.getWantedBooks(user, pageRequest);
        return ResponseEntity.ok(books);
    }

    /**
     * 搜索书籍
     */
    @GetMapping("/search")
    public ResponseEntity<Page<BookDTO>> searchBooks(
            Authentication authentication,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<BookDTO> books = bookService.searchBooks(user, keyword, pageRequest);
        return ResponseEntity.ok(books);
    }

    /**
     * 获取书籍详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookDTO> getBookById(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO book = bookService.getBookById(id, user);
        return ResponseEntity.ok(book);
    }

    /**
     * 获取所有书籍（无分页）
     */
    @GetMapping("/all")
    public ResponseEntity<List<BookDTO>> getAllBooks(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<BookDTO> books = bookService.getAllBooks(user);
        return ResponseEntity.ok(books);
    }

    /**
     * 切换收藏状态
     */
    @PutMapping("/{id}/favorite")
    public ResponseEntity<BookDTO> toggleFavorite(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO book = bookService.toggleFavorite(id, user);
        return ResponseEntity.ok(book);
    }

    /**
     * 切换想读状态
     */
    @PutMapping("/{id}/wanted")
    public ResponseEntity<BookDTO> toggleWanted(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO book = bookService.toggleWanted(id, user);
        return ResponseEntity.ok(book);
    }

    /**
     * 删除书籍
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        bookService.deleteBook(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新书籍元数据
     */
    @PutMapping("/{id}/metadata")
    public ResponseEntity<BookDTO> updateBookMetadata(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody BookDTO bookDTO) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO updatedBook = bookService.updateBookMetadata(id, bookDTO, user);
        return ResponseEntity.ok(updatedBook);
    }

    /**
     * 更新阅读状态
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<BookDTO> updateReadingStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        User user = userService.findByUsername(authentication.getName());
        Book.ReadingStatus status = Book.ReadingStatus.valueOf(body.get("status"));
        BookDTO book = bookService.updateReadingStatus(id, status, user);
        return ResponseEntity.ok(book);
    }
}
