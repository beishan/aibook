package com.aibook.controller;

import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerSiteIconService;
import com.aibook.service.crawler.CrawlerSiteIconService.IconView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/crawler/sites/{id}/icon")
@RequiredArgsConstructor
public class CrawlerSiteIconController {
    private final UserService users;
    private final CrawlerSiteIconService icons;

    @GetMapping
    public ResponseEntity<IconView> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(icons.get(users.findByUsername(auth.getName()), id));
    }

    @PostMapping("/refresh")
    public IconView refresh(Authentication auth, @PathVariable Long id) {
        return icons.refresh(users.findByUsername(auth.getName()), id);
    }

    @PostMapping(consumes = "multipart/form-data")
    public IconView upload(Authentication auth, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return icons.upload(users.findByUsername(auth.getName()), id, file);
    }
}
