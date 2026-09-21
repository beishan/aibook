package com.aibook.controller;

import com.aibook.dto.BookVersionCandidatePageDTO;
import com.aibook.dto.BookVersionDTO;
import com.aibook.dto.BookVersionImportRequest;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.service.BookService;
import com.aibook.service.BookVersionImportService;
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
import org.springframework.web.bind.annotation.RequestBody;
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
    private final BookVersionImportService bookVersionImportService;
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

    @GetMapping("/candidates")
    public ResponseEntity<BookVersionCandidatePageDTO> getCandidates(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(bookId, user);
        return ResponseEntity.ok(bookVersionImportService.candidates(
                book, user, page, size, keyword));
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BookVersionDTO>> importVersions(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestBody BookVersionImportRequest request) {
        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(bookId, user);
        return ResponseEntity.ok(bookVersionImportService.importVersions(
                book, user, request));
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
