package com.aibook.controller;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.service.BookService;
import com.aibook.service.BookVersionService;
import com.aibook.service.StructuredPublicationService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/books/{bookId}/structured")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StructuredPublicationController {

    private final UserService userService;
    private final BookService bookService;
    private final BookVersionService bookVersionService;
    private final StructuredPublicationService publicationService;

    @GetMapping(value = "/manifest", produces = "application/webpub+json")
    public ResponseEntity<Map<String, Object>> manifest(Authentication authentication,
            @PathVariable Long bookId, @RequestParam(required = false) Long versionId) {
        Context context = context(authentication, bookId, versionId);
        return ResponseEntity.ok(publicationService.manifest(context.book(), context.version()));
    }

    @GetMapping(value = "/chapters/{chapterId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chapter(Authentication authentication,
            @PathVariable Long bookId, @PathVariable Long chapterId,
            @RequestParam(required = false) Long versionId) {
        Context context = context(authentication, bookId, versionId);
        return ResponseEntity.ok(publicationService.chapter(context.version(), chapterId));
    }

    private Context context(Authentication authentication, Long bookId, Long versionId) {
        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(bookId, user);
        return new Context(book, bookVersionService.resolveVersion(book, versionId));
    }

    private record Context(Book book, BookVersion version) { }
}
