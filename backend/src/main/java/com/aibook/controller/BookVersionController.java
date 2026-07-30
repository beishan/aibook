package com.aibook.controller;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.service.BookService;
import com.aibook.service.BookVersionService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/versions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookVersionController {

    private final BookVersionService bookVersionService;
    private final BookService bookService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<com.aibook.dto.BookVersionDTO>> getVersions(
            Authentication authentication,
            @PathVariable Long bookId) {
        Book book = ownedBook(authentication, bookId);
        return ResponseEntity.ok(bookVersionService.getVersions(book));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<com.aibook.dto.BookVersionDTO> addVersion(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestParam("file") MultipartFile file) {
        Book book = ownedBook(authentication, bookId);
        return ResponseEntity.ok(bookVersionService.addVersion(book, file));
    }

    @DeleteMapping("/{versionId}")
    public ResponseEntity<Void> deleteVersion(
            Authentication authentication,
            @PathVariable Long bookId,
            @PathVariable Long versionId) {
        Book book = ownedBook(authentication, bookId);
        bookVersionService.deleteVersion(book, versionId);
        return ResponseEntity.noContent().build();
    }

    private Book ownedBook(Authentication authentication, Long bookId) {
        User user = userService.findByUsername(authentication.getName());
        return bookService.getBookEntity(bookId, user);
    }
}
