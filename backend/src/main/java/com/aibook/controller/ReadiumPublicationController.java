package com.aibook.controller;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.service.BookService;
import com.aibook.service.BookVersionService;
import com.aibook.service.ReadiumPublicationService;
import com.aibook.service.ReadiumAccessTokenService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** Authenticated Readium Web Publication endpoints for one EPUB version. */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReadiumPublicationController {

    private final UserService userService;
    private final BookService bookService;
    private final BookVersionService bookVersionService;
    private final ReadiumPublicationService readiumPublicationService;
    private final ReadiumAccessTokenService readiumAccessTokenService;

    @GetMapping("/{bookId}/readium/{versionId}/manifest.json")
    public ResponseEntity<Map<String, Object>> getManifest(
            Authentication authentication,
            @PathVariable Long bookId,
            @PathVariable Long versionId) throws IOException {
        VersionContext context = resolve(authentication, bookId, versionId);
        String accessToken = readiumAccessTokenService.issue(context.book(), context.version());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(readiumPublicationService.buildManifest(
                        context.book(), context.version(), accessToken));
    }

    private VersionContext resolve(Authentication authentication, Long bookId, Long versionId) {
        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(bookId, user);
        BookVersion version = bookVersionService.resolveVersion(book, versionId);
        return new VersionContext(book, version);
    }

    private record VersionContext(Book book, BookVersion version) {}
}
