package com.aibook.controller;

import com.aibook.service.BookCardBadgeSettingsService;
import com.aibook.service.BookCardBadgeSettingsService.Settings;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/book-card-badges")
@RequiredArgsConstructor
public class BookCardBadgeSettingsController {
    private final BookCardBadgeSettingsService settings;
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
