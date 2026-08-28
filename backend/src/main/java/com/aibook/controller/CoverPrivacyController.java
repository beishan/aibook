package com.aibook.controller;

import com.aibook.dto.CoverPrivacyScopeDTO;
import com.aibook.model.entity.User;
import com.aibook.service.CoverPrivacyService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的封面隐藏设置。 */
@RestController
@RequestMapping("/api/cover-privacy")
@RequiredArgsConstructor
public class CoverPrivacyController {

    private final CoverPrivacyService coverPrivacyService;
    private final UserService userService;

    @GetMapping("/books")
    public ResponseEntity<CoverPrivacyScopeDTO> getBookCoverSettings(
            Authentication authentication) {
        return ResponseEntity.ok(
                coverPrivacyService.getBookCoverSettings(currentUser(authentication)));
    }

    @PutMapping("/books")
    public ResponseEntity<CoverPrivacyScopeDTO> updateBookCoverSettings(
            Authentication authentication,
            @RequestBody CoverPrivacyScopeDTO request) {
        return ResponseEntity.ok(coverPrivacyService.updateBookCoverSettings(
                currentUser(authentication), request));
    }

    @GetMapping("/random-covers")
    public ResponseEntity<CoverPrivacyScopeDTO> getRandomCoverSettings(
            Authentication authentication) {
        return ResponseEntity.ok(
                coverPrivacyService.getRandomCoverSettings(currentUser(authentication)));
    }

    @PutMapping("/random-covers")
    public ResponseEntity<CoverPrivacyScopeDTO> updateRandomCoverSettings(
            Authentication authentication,
            @RequestBody CoverPrivacyScopeDTO request) {
        return ResponseEntity.ok(coverPrivacyService.updateRandomCoverSettings(
                currentUser(authentication), request));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
