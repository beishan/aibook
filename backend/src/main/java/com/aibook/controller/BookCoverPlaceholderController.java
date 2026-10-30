package com.aibook.controller;

import com.aibook.service.BookCoverPlaceholderService;
import com.aibook.service.BookCoverPlaceholderService.Settings;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/book-cover-placeholder")
@RequiredArgsConstructor
public class BookCoverPlaceholderController {
    private final BookCoverPlaceholderService settings;
    private final UserService users;

    @GetMapping
    public Settings get(Authentication authentication) {
        return settings.get(users.findByUsername(authentication.getName()));
    }

    @PutMapping
    public Settings save(Authentication authentication, @RequestBody Settings request) {
        return settings.save(users.findByUsername(authentication.getName()), request);
    }
}
