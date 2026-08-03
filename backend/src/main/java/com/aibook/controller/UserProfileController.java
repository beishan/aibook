package com.aibook.controller;

import com.aibook.dto.UserProfileDTO;
import com.aibook.dto.UserProfileUpdateRequest;
import com.aibook.model.entity.User;
import com.aibook.service.UserProfileService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;
    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileDTO> getProfile(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(userProfileService.getProfile(user));
    }

    @PutMapping
    public ResponseEntity<UserProfileDTO> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(
                currentUser(authentication), request));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileDTO> uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userProfileService.uploadAvatar(
                currentUser(authentication), file));
    }

    @GetMapping("/avatar")
    public ResponseEntity<Resource> getAvatar(Authentication authentication) {
        UserProfileService.AvatarContent avatar =
                userProfileService.getAvatar(currentUser(authentication));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, avatar.contentType())
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(avatar.path()));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<UserProfileDTO> deleteAvatar(Authentication authentication) {
        return ResponseEntity.ok(userProfileService.deleteAvatar(
                currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
