package com.aibook.controller;

import com.aibook.dto.AdminUserCreateRequest;
import com.aibook.dto.AdminUserDTO;
import com.aibook.dto.AdminUserUpdateRequest;
import com.aibook.dto.ResetPasswordRequest;
import com.aibook.model.entity.User;
import com.aibook.service.AdminUserService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<AdminUserDTO>> getUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        return ResponseEntity.ok(adminUserService.getUsers(keyword, pageable));
    }

    @PostMapping
    public ResponseEntity<AdminUserDTO> createUser(
            Authentication authentication,
            @Valid @RequestBody AdminUserCreateRequest request) {
        return ResponseEntity.ok(adminUserService.createUser(
                request, currentUser(authentication)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDTO> updateUser(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        return ResponseEntity.ok(adminUserService.updateUser(
                id, request, currentUser(authentication)));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(
                id, request.getPassword(), currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            Authentication authentication,
            @PathVariable Long id) {
        adminUserService.deleteUser(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
