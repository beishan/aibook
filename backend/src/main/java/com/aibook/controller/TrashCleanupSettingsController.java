package com.aibook.controller;

import com.aibook.dto.TrashCleanupSettingsDTO;
import com.aibook.model.entity.User;
import com.aibook.service.TrashCleanupService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/trash/settings")
@RequiredArgsConstructor
public class TrashCleanupSettingsController {

    private final UserService userService;
    private final TrashCleanupService trashCleanupService;

    @GetMapping
    public ResponseEntity<TrashCleanupSettingsDTO> getSettings(Authentication authentication) {
        return ResponseEntity.ok(trashCleanupService.getSettings(currentUser(authentication)));
    }

    @PutMapping
    public ResponseEntity<TrashCleanupSettingsDTO> updateSettings(
            Authentication authentication,
            @RequestBody TrashCleanupSettingsDTO request) {
        return ResponseEntity.ok(
                trashCleanupService.updateSettings(currentUser(authentication), request));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
