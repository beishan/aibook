package com.aibook.controller;

import com.aibook.dto.SiteFaviconStatusDTO;
import com.aibook.model.entity.User;
import com.aibook.service.SiteFaviconService;
import com.aibook.service.UserService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/site/favicon")
@RequiredArgsConstructor
public class SiteFaviconController {

    private final SiteFaviconService faviconService;
    private final UserService userService;

    @GetMapping("/status")
    public ResponseEntity<SiteFaviconStatusDTO> status() {
        return ResponseEntity.ok(faviconService.getStatus());
    }

    @GetMapping
    public ResponseEntity<Resource> content() {
        SiteFaviconService.FaviconContent favicon = faviconService.getContent();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, favicon.contentType())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new FileSystemResource(favicon.path()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SiteFaviconStatusDTO> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(faviconService.upload(currentUser(authentication), file));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SiteFaviconStatusDTO> restoreDefault(
            Authentication authentication) {
        return ResponseEntity.ok(faviconService.restoreDefault(currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
