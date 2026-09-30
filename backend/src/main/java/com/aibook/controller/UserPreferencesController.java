package com.aibook.controller;

import com.aibook.dto.UserPreferencesDTO;
import com.aibook.dto.RewriteSearchRuleDTO;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/rewrite-search-rules")
    public ResponseEntity<List<RewriteSearchRuleDTO>> getRewriteSearchRules(
            Authentication authentication) {
        return ResponseEntity.ok(userService.getRewriteSearchRules(authentication.getName()));
    }

    @PutMapping("/rewrite-search-rules")
    public ResponseEntity<List<RewriteSearchRuleDTO>> updateRewriteSearchRules(
            Authentication authentication,
            @RequestBody List<RewriteSearchRuleDTO> rules) {
        return ResponseEntity.ok(userService.updateRewriteSearchRules(
                authentication.getName(), rules));
    }
}
