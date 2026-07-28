package com.aibook.controller;

import com.aibook.dto.UserPreferencesDTO;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户的界面偏好。
 */
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserPreferencesDTO> getPreferences(Authentication authentication) {
        return ResponseEntity.ok(userService.getPreferences(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<UserPreferencesDTO> updatePreferences(
            Authentication authentication,
            @RequestBody UserPreferencesDTO request) {
        return ResponseEntity.ok(
                userService.updatePreferences(authentication.getName(), request));
    }
}
