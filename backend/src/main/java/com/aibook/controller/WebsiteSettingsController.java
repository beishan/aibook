package com.aibook.controller;

import com.aibook.dto.WebsiteSettingsDTO;
import com.aibook.dto.WebsiteSettingsUpdateRequest;
import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.WebsiteSettingsService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/site")
@RequiredArgsConstructor
public class WebsiteSettingsController {

    private final WebsiteSettingsService websiteSettingsService;
    private final UserService userService;

    @GetMapping("/settings")
    public ResponseEntity<WebsiteSettingsDTO> settings() {
        return ResponseEntity.ok(websiteSettingsService.getSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebsiteSettingsDTO> update(
            Authentication authentication,
            @RequestBody WebsiteSettingsUpdateRequest request) {
        return ResponseEntity.ok(websiteSettingsService.update(currentUser(authentication), request));
    }

    @GetMapping("/login-icon")
    public ResponseEntity<Resource> loginIcon() {
        WebsiteSettingsService.LoginIconContent icon = websiteSettingsService.getLoginIconContent();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, icon.contentType())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new FileSystemResource(icon.path()));
    }

    @PostMapping(value = "/login-icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebsiteSettingsDTO> uploadLoginIcon(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(websiteSettingsService.uploadLoginIcon(
                currentUser(authentication), file));
    }

    @DeleteMapping("/login-icon")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebsiteSettingsDTO> restoreDefaultLoginIcon(Authentication authentication) {
        return ResponseEntity.ok(websiteSettingsService.restoreDefaultLoginIcon(
                currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
