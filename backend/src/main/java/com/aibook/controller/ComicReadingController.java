package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.ComicReadingService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/versions/{versionId}/comic")
@RequiredArgsConstructor
public class ComicReadingController {

    private final ComicReadingService comics;
    private final UserService users;

    @GetMapping
    public ComicReadingService.Manifest manifest(
            Authentication auth, @PathVariable long bookId, @PathVariable long versionId) {
        return comics.manifest(bookId, versionId, user(auth));
    }

    @GetMapping("/pages/{page}")
    public ResponseEntity<byte[]> page(Authentication auth, @PathVariable long bookId,
            @PathVariable long versionId, @PathVariable int page, @RequestParam String revision) {
        var image = comics.page(bookId, versionId, user(auth), revision, page);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Image-Width", Integer.toString(image.width()))
                .header("X-Image-Height", Integer.toString(image.height()))
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }

    @GetMapping("/progress")
    public ComicReadingService.Position position(
            Authentication auth, @PathVariable long bookId, @PathVariable long versionId) {
        return comics.position(bookId, versionId, user(auth));
    }

    @PutMapping("/progress")
    public ComicReadingService.Position save(Authentication auth, @PathVariable long bookId,
            @PathVariable long versionId, @RequestBody ComicReadingService.ProgressUpdate update) {
        return comics.save(bookId, versionId, user(auth), update);
    }

    private User user(Authentication auth) {
        return users.findByUsername(auth.getName());
    }
}
