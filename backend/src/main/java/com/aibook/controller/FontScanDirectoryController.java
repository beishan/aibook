package com.aibook.controller;

import com.aibook.dto.FontScanDirectoryDTO;
import com.aibook.dto.FontScanDirectoryRequest;
import com.aibook.model.entity.User;
import com.aibook.service.FontService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/font-scan-directories")
@RequiredArgsConstructor
public class FontScanDirectoryController {

    private final FontService fontService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<FontScanDirectoryDTO>> list(
            Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(fontService.listDirectories());
    }

    @PostMapping
    public ResponseEntity<FontScanDirectoryDTO> add(
            Authentication authentication,
            @RequestBody FontScanDirectoryRequest request) {
        requireAdmin(authentication);
        return ResponseEntity.ok(fontService.addDirectory(request.getPath()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication, @PathVariable Long id) {
        requireAdmin(authentication);
        fontService.deleteDirectory(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (user.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "仅管理员可以管理字体扫描目录");
        }
    }
}
