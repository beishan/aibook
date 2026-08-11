package com.aibook.controller;

import com.aibook.dto.ScheduledScanSettingsDTO;
import com.aibook.model.entity.User;
import com.aibook.service.ScheduledScanService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 网页端定时扫描设置接口。 */
@RestController
@RequestMapping("/api/scheduled-scan-settings")
@RequiredArgsConstructor
public class ScheduledScanController {

    private final ScheduledScanService scheduledScanService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ScheduledScanSettingsDTO> getSettings(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(scheduledScanService.getSettings(user));
    }

    @PutMapping
    public ResponseEntity<ScheduledScanSettingsDTO> updateSettings(
            Authentication authentication,
            @RequestBody ScheduledScanSettingsDTO request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(scheduledScanService.updateSettings(user, request));
    }
}
